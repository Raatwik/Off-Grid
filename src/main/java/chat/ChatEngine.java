package chat;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * ChatEngine
 * -------------------------------------------------------------------------
 * The crypto/framing brain of the Off-Grid E2EE Campus Chat project
 * (Phase 3+4 transport-crypto-layer, Issue 01 — chat-engine-core).
 *
 * Responsibilities:
 *   - Encrypt and chunk an outgoing plaintext message into {@link ChatFrame}s
 *     (per {@link #send}) and write them out over a {@link ByteTransport}
 *   - Reassemble/decrypt incoming frames read from that same transport back
 *     into the original plaintext, delivered to a {@link Listener}
 *   - Own per-msgId reassembly bookkeeping (timestamps, timeout sweeping)
 *
 * Each chunk is encrypted independently with {@code AES/GCM/NoPadding}
 * under a single shared key, using a fresh random 12-byte nonce per chunk.
 * Chunks are grouped by a random 4-byte {@code msgId}; a chunk that never
 * arrives (dropped on the wire, or fails to decrypt) causes that message's
 * reassembly buffer to be discarded once it has been incomplete for longer
 * than the reassembly timeout, surfacing {@link Listener#onMessageFailed}
 * rather than hanging or silently dropping it.
 *
 * Two chunks claiming the same {@code msgId} but disagreeing about
 * {@code totalChunks}, or about the content at a shared {@code chunkIndex},
 * are treated as a collision: whichever message claimed that {@code msgId}
 * first keeps its reassembly buffer, and the colliding chunk is rejected
 * (dropped) rather than overwriting it. This guarantees a message in
 * flight is never cross-contaminated by another message's chunks.
 *
 * IMPORTANT: this class only knows about the {@link ByteTransport} seam, not
 * the hardware behind it — production wires a real {@code SerialTransport}
 * (jSerialComm) in a later issue; tests wire two instances of this class to
 * each other through an in-memory pipe. Key management (e.g. the project's
 * single hardcoded shared AES-256 key) is also a later-issue concern; this
 * class simply takes whatever key it's constructed with.
 */
public final class ChatEngine implements AutoCloseable {

    /** Plaintext bytes per chunk, chosen so ciphertext+tag stays within {@link ChatFrame#MAX_CIPHERTEXT_LENGTH}. */
    public static final int MAX_CHUNK_PLAINTEXT_BYTES = ChatFrame.MAX_CIPHERTEXT_LENGTH - 16;

    private static final String CIPHER_TRANSFORM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

    /** Receives fully reassembled incoming messages, or a failure notice when one can't be completed. */
    public interface Listener {
        void onMessageReceived(String plaintext);

        void onMessageFailed(String reason);
    }

    private final ByteTransport transport;
    private final SecretKeySpec key;
    private final Listener listener;
    private final long timeoutMillis;
    private final SecureRandom random = new SecureRandom();
    private final Map<Integer, ReassemblyBuffer> buffers = new ConcurrentHashMap<>();
    private final Object buffersLock = new Object();
    private final Thread readerThread;
    private final ScheduledExecutorService timeoutSweeper;
    private volatile boolean closed = false;

    public ChatEngine(ByteTransport transport, byte[] key, Listener listener) {
        this(transport, key, listener, DEFAULT_TIMEOUT);
    }

    public ChatEngine(ByteTransport transport, byte[] key, Listener listener, Duration reassemblyTimeout) {
        this.transport = transport;
        this.key = new SecretKeySpec(key, "AES");
        this.listener = listener;
        this.timeoutMillis = reassemblyTimeout.toMillis();

        this.readerThread = new Thread(this::readLoop, "chat-engine-reader");
        this.readerThread.setDaemon(true);
        this.readerThread.start();

        this.timeoutSweeper = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "chat-engine-timeout-sweeper");
            thread.setDaemon(true);
            return thread;
        });
        long sweepIntervalMillis = Math.max(50, this.timeoutMillis / 10);
        this.timeoutSweeper.scheduleAtFixedRate(
                this::sweepTimedOutBuffers, sweepIntervalMillis, sweepIntervalMillis, TimeUnit.MILLISECONDS);
    }

    /** Encrypts, chunks, and writes {@code plaintext} out over the transport under a fresh random msgId. */
    public void send(String plaintext) throws IOException {
        byte[] plaintextBytes = plaintext.getBytes(StandardCharsets.UTF_8);
        List<byte[]> chunks = splitIntoChunks(plaintextBytes);
        if (chunks.size() > 255) {
            throw new IllegalArgumentException("message too long: would need " + chunks.size() + " chunks (max 255)");
        }

        byte[] msgId = new byte[ChatFrame.MSG_ID_LENGTH];
        random.nextBytes(msgId);
        int totalChunks = chunks.size();

        for (int index = 0; index < totalChunks; index++) {
            byte[] nonce = new byte[ChatFrame.NONCE_LENGTH];
            random.nextBytes(nonce);
            byte[] ciphertext = encrypt(chunks.get(index), nonce);
            ChatFrame frame = new ChatFrame(msgId, index, totalChunks, nonce, ciphertext);
            transport.write(frame.encode());
        }
    }

    /**
     * Encrypts {@code plaintext} under the shared key with a fresh random nonce, for storing
     * at rest (the DB's {@code ciphertext_b64}/{@code nonce_b64} columns). Independent of the
     * wire chunks, which are not retained after send/reassembly.
     */
    public SealedText seal(String plaintext) {
        byte[] nonce = new byte[ChatFrame.NONCE_LENGTH];
        random.nextBytes(nonce);
        return new SealedText(encrypt(plaintext.getBytes(StandardCharsets.UTF_8), nonce), nonce);
    }

    /** Ciphertext (including GCM tag) and the nonce it was produced with. */
    public record SealedText(byte[] ciphertext, byte[] nonce) {}

    @Override
    public void close() {
        closed = true;
        timeoutSweeper.shutdownNow();
        try {
            transport.close();
        } catch (IOException ignored) {
            // transport is going away anyway; nothing the caller can do with this
        }
        readerThread.interrupt();
    }

    private List<byte[]> splitIntoChunks(byte[] plaintextBytes) {
        List<byte[]> chunks = new ArrayList<>();
        if (plaintextBytes.length == 0) {
            chunks.add(plaintextBytes);
            return chunks;
        }
        for (int offset = 0; offset < plaintextBytes.length; offset += MAX_CHUNK_PLAINTEXT_BYTES) {
            int end = Math.min(offset + MAX_CHUNK_PLAINTEXT_BYTES, plaintextBytes.length);
            byte[] chunk = new byte[end - offset];
            System.arraycopy(plaintextBytes, offset, chunk, 0, chunk.length);
            chunks.add(chunk);
        }
        return chunks;
    }

    private void readLoop() {
        while (!closed) {
            byte[] frameBytes;
            try {
                frameBytes = transport.read();
            } catch (IOException e) {
                return; // transport closed/unavailable: stop quietly, nothing left to read
            }
            try {
                processFrame(ChatFrame.decode(frameBytes));
            } catch (RuntimeException e) {
                // A malformed frame must never take down the reader thread.
                listener.onMessageFailed("malformed frame: " + e.getMessage());
            }
        }
    }

    private void processFrame(ChatFrame frame) {
        int msgIdKey = frame.msgIdKey();
        synchronized (buffersLock) {
            // The buffer is registered from the header alone (msgId/totalChunks are never
            // encrypted), even if this particular chunk turns out to be corrupted below —
            // otherwise a message whose only/first chunk is corrupted would never be tracked
            // at all, and so could never time out and surface as failed.
            ReassemblyBuffer buffer = buffers.computeIfAbsent(
                    msgIdKey, k -> new ReassemblyBuffer(frame.totalChunks()));

            if (buffer.totalChunks != frame.totalChunks()) {
                return; // colliding msgId claiming a different chunk count: reject, keep original
            }

            byte[] plaintext;
            try {
                plaintext = decrypt(frame.ciphertext(), frame.nonce());
            } catch (GeneralSecurityException e) {
                // Corrupted/undecryptable chunk: leave this index unfilled, same as a chunk
                // that never arrived. The buffer above will eventually time out and surface
                // as failed, rather than crashing or decrypting garbage.
                return;
            }

            if (!buffer.accept(frame.chunkIndex(), plaintext)) {
                return; // colliding msgId with conflicting content at a shared index: reject
            }

            if (buffer.isComplete()) {
                buffers.remove(msgIdKey);
                listener.onMessageReceived(buffer.assemble());
            }
        }
    }

    private void sweepTimedOutBuffers() {
        long now = System.currentTimeMillis();
        synchronized (buffersLock) {
            Iterator<Map.Entry<Integer, ReassemblyBuffer>> it = buffers.entrySet().iterator();
            while (it.hasNext()) {
                ReassemblyBuffer buffer = it.next().getValue();
                if (now - buffer.firstArrivalMillis > timeoutMillis) {
                    it.remove();
                    listener.onMessageFailed("message incomplete: timed out waiting for missing chunk(s)");
                }
            }
        }
    }

    private byte[] encrypt(byte[] plaintext, byte[] nonce) {
        try {
            return newCipher(Cipher.ENCRYPT_MODE, nonce).doFinal(plaintext);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("encryption failed", e);
        }
    }

    private byte[] decrypt(byte[] ciphertext, byte[] nonce) throws GeneralSecurityException {
        // AEADBadTagException (bad auth tag, i.e. a corrupted chunk) propagates to the
        // caller, which treats it as a graceful drop rather than a crash.
        return newCipher(Cipher.DECRYPT_MODE, nonce).doFinal(ciphertext);
    }

    private Cipher newCipher(int mode, byte[] nonce) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORM);
        cipher.init(mode, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce));
        return cipher;
    }

    /** Per-msgId reassembly state: which chunks have arrived, and when the first one did. */
    private static final class ReassemblyBuffer {
        final int totalChunks;
        final byte[][] chunks;
        final long firstArrivalMillis;
        private int filledCount = 0;

        ReassemblyBuffer(int totalChunks) {
            this.totalChunks = totalChunks;
            this.chunks = new byte[totalChunks][];
            this.firstArrivalMillis = System.currentTimeMillis();
        }

        /** Returns false if {@code index} already holds different content (a colliding message). */
        boolean accept(int index, byte[] plaintext) {
            byte[] existing = chunks[index];
            if (existing != null) {
                return java.util.Arrays.equals(existing, plaintext);
            }
            chunks[index] = plaintext;
            filledCount++;
            return true;
        }

        boolean isComplete() {
            return filledCount == totalChunks;
        }

        String assemble() {
            int totalLength = 0;
            for (byte[] chunk : chunks) {
                totalLength += chunk.length;
            }
            byte[] full = new byte[totalLength];
            int offset = 0;
            for (byte[] chunk : chunks) {
                System.arraycopy(chunk, 0, full, offset, chunk.length);
                offset += chunk.length;
            }
            return new String(full, StandardCharsets.UTF_8);
        }
    }
}
