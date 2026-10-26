package chat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link ChatEngine}'s public send/receive behavior end-to-end
 * through the in-memory {@link InMemoryByteTransport} test seam — no real
 * serial port, no ESP32 hardware, per the transport-crypto-layer spec's
 * testing decisions.
 */
class ChatEngineTest {

    private static final byte[] KEY = new byte[32];

    static {
        new SecureRandom().nextBytes(KEY);
    }

    private static final Duration SHORT_TIMEOUT = Duration.ofMillis(300);

    private final List<ChatEngine> engines = new ArrayList<>();

    @AfterEach
    void closeEngines() {
        for (ChatEngine engine : engines) {
            engine.close();
        }
    }

    private ChatEngine engine(ByteTransport transport, RecordingListener listener, Duration timeout) {
        ChatEngine engine = new ChatEngine(transport, KEY, listener, timeout);
        engines.add(engine);
        return engine;
    }

    @Test
    void shortSingleChunkMessageRoundTrips() throws IOException, InterruptedException {
        InMemoryByteTransport[] pair = InMemoryByteTransport.pair();
        RecordingListener listenerA = new RecordingListener();
        RecordingListener listenerB = new RecordingListener();
        ChatEngine engineA = engine(pair[0], listenerA, SHORT_TIMEOUT);
        ChatEngine engineB = engine(pair[1], listenerB, SHORT_TIMEOUT);

        engineA.send("hello off-grid");

        awaitTrue(() -> listenerB.received.size() == 1, 1000);
        assertEquals("hello off-grid", listenerB.received.get(0));
        assertTrue(listenerB.failures.isEmpty());
    }

    @Test
    void multiChunkMessageRoundTripsInOrder() throws IOException, InterruptedException {
        InMemoryByteTransport[] pair = InMemoryByteTransport.pair();
        RecordingListener listenerA = new RecordingListener();
        RecordingListener listenerB = new RecordingListener();
        ChatEngine engineA = engine(pair[0], listenerA, SHORT_TIMEOUT);
        ChatEngine engineB = engine(pair[1], listenerB, SHORT_TIMEOUT);

        // Longer than MAX_CHUNK_PLAINTEXT_BYTES, so this forces multiple chunks.
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < ChatEngine.MAX_CHUNK_PLAINTEXT_BYTES * 3 + 17; i++) {
            builder.append((char) ('a' + (i % 26)));
        }
        String longMessage = builder.toString();

        engineA.send(longMessage);

        awaitTrue(() -> listenerB.received.size() == 1, 2000);
        assertEquals(longMessage, listenerB.received.get(0));
        assertTrue(listenerB.failures.isEmpty());
    }

    @Test
    void droppedChunkSurfacesFailureAfterTimeoutInsteadOfHanging() throws IOException, InterruptedException {
        InMemoryByteTransport[] pair = InMemoryByteTransport.pair();
        RecordingListener listenerB = new RecordingListener();
        ChatEngine engineA = engine(pair[0], new RecordingListener(), SHORT_TIMEOUT);
        ChatEngine engineB = engine(pair[1], listenerB, SHORT_TIMEOUT);

        // Drop the second chunk on the wire, simulating an unreliable radio link.
        pair[0].setOutboundInterceptor(frame -> {
            ChatFrame decoded = ChatFrame.decode(frame);
            return decoded.chunkIndex() == 1 ? null : frame;
        });

        String message = "x".repeat(ChatEngine.MAX_CHUNK_PLAINTEXT_BYTES + 10); // forces 2 chunks
        engineA.send(message);

        awaitTrue(() -> !listenerB.failures.isEmpty(), 2000);
        assertTrue(listenerB.received.isEmpty(), "incomplete message must never be delivered as if it succeeded");
    }

    @Test
    void corruptedChunkFailsGracefullyWithoutCrashOrWrongPlaintext() throws IOException, InterruptedException {
        InMemoryByteTransport[] pair = InMemoryByteTransport.pair();
        RecordingListener listenerB = new RecordingListener();
        ChatEngine engineA = engine(pair[0], new RecordingListener(), SHORT_TIMEOUT);
        ChatEngine engineB = engine(pair[1], listenerB, SHORT_TIMEOUT);

        // Flip a byte inside the ciphertext region (after the 18-byte header), corrupting the GCM tag/ciphertext.
        pair[0].setOutboundInterceptor(frame -> {
            byte[] corrupted = frame.clone();
            int ciphertextOffset = ChatFrame.HEADER_LENGTH;
            corrupted[ciphertextOffset] ^= 0x01;
            return corrupted;
        });

        engineA.send("sensitive payload");

        awaitTrue(() -> !listenerB.failures.isEmpty(), 2000);
        assertTrue(listenerB.received.isEmpty(), "a corrupted chunk must never decrypt into accepted plaintext");
    }

    /**
     * Forces a msgId collision (real msgIds are random, so this is simulated by injecting
     * hand-crafted frames directly). Documents the chosen behavior: whichever message claims
     * a chunk index first wins that slot; a later chunk claiming the same msgId + index with
     * different content is rejected (dropped) rather than overwriting it. This guarantees the
     * original message reassembles with its own, uncontaminated content.
     */
    @Test
    void collidingMsgIdDoesNotCrossContaminateTheOriginalMessage() throws GeneralSecurityException, IOException,
            InterruptedException {
        InMemoryByteTransport[] pair = InMemoryByteTransport.pair();
        RecordingListener listenerB = new RecordingListener();
        ChatEngine engineB = engine(pair[1], listenerB, SHORT_TIMEOUT);
        ByteTransport injector = pair[0];

        byte[] sharedMsgId = {0x01, 0x02, 0x03, 0x04};

        // Message 1, chunk 0 of 2: arrives first, opens the reassembly buffer.
        injector.write(buildFrame(sharedMsgId, 0, 2, "legit-chunk-0"));

        // Message 2 (attacker/colliding message), chunk 0 of 2, same msgId + index, different content.
        injector.write(buildFrame(sharedMsgId, 0, 2, "EVIL-CHUNK!!!"));

        // Message 1, chunk 1 of 2: completes message 1's buffer.
        injector.write(buildFrame(sharedMsgId, 1, 2, "legit-chunk-1"));

        awaitTrue(() -> listenerB.received.size() == 1, 2000);
        assertEquals("legit-chunk-0legit-chunk-1", listenerB.received.get(0));
        assertTrue(listenerB.failures.isEmpty());
    }

    /**
     * Same documented reject behavior as above, but the collision disagrees on
     * {@code totalChunks} itself rather than on content at a shared index — the other
     * branch of the collision check in {@code ChatEngine.processFrame}.
     */
    @Test
    void collidingMsgIdWithDifferentTotalChunksIsRejectedWithoutCorruptingTheOriginal()
            throws GeneralSecurityException, IOException, InterruptedException {
        InMemoryByteTransport[] pair = InMemoryByteTransport.pair();
        RecordingListener listenerB = new RecordingListener();
        ChatEngine engineB = engine(pair[1], listenerB, SHORT_TIMEOUT);
        ByteTransport injector = pair[0];

        byte[] sharedMsgId = {0x05, 0x06, 0x07, 0x08};

        // Message 1, chunk 0 of 2: arrives first, opens the reassembly buffer with totalChunks=2.
        injector.write(buildFrame(sharedMsgId, 0, 2, "legit-chunk-0"));

        // Message 2 (colliding), same msgId, but claims a different totalChunks (3) — rejected outright.
        injector.write(buildFrame(sharedMsgId, 0, 3, "EVIL-CHUNK!!!"));

        // Message 1, chunk 1 of 2: completes message 1's buffer, unaffected by the rejected collision.
        injector.write(buildFrame(sharedMsgId, 1, 2, "legit-chunk-1"));

        awaitTrue(() -> listenerB.received.size() == 1, 2000);
        assertEquals("legit-chunk-0legit-chunk-1", listenerB.received.get(0));
        assertTrue(listenerB.failures.isEmpty());
    }

    private static byte[] buildFrame(byte[] msgId, int chunkIndex, int totalChunks, String plaintext)
            throws GeneralSecurityException {
        byte[] nonce = new byte[ChatFrame.NONCE_LENGTH];
        new SecureRandom().nextBytes(nonce);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(KEY, "AES"), new GCMParameterSpec(128, nonce));
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new ChatFrame(msgId, chunkIndex, totalChunks, nonce, ciphertext).encode();
    }

    private static void awaitTrue(BooleanSupplier condition, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(20);
        }
        assertTrue(condition.getAsBoolean(), "condition not met within " + timeoutMillis + "ms");
    }

    private static final class RecordingListener implements ChatEngine.Listener {
        final List<String> received = new CopyOnWriteArrayList<>();
        final List<String> failures = new CopyOnWriteArrayList<>();

        @Override
        public void onMessageReceived(String plaintext) {
            received.add(plaintext);
        }

        @Override
        public void onMessageFailed(String reason) {
            failures.add(reason);
        }
    }
}
