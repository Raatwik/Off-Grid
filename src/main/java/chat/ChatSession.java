package chat;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Base64;
import java.util.function.Consumer;

/**
 * ChatSession
 * -------------------------------------------------------------------------
 * GUI-facing glue between {@link ChatEngine} and {@link ChatDatabase}
 * (Phase 3+4 transport-crypto-layer, Issue 05 — swing-gui-wiring).
 *
 * Responsibilities:
 *   - Send typed text through the engine and persist it as an OUT row
 *   - Persist each reassembled incoming message as an IN row
 *   - Persist a visible "message failed / incomplete" row when an incoming
 *     message times out, so it never silently vanishes
 *   - Publish every persisted row to a {@code Consumer}; callers (the Swing
 *     frontend) are responsible for hopping onto their own UI thread
 *
 * Deliberately Swing-free so it can be tested over the in-memory transport.
 * Engine callbacks arrive on the engine's own Reader/sweeper threads, so DB
 * access is serialized on {@code db}; sends only enqueue onto the transport
 * and never wait on incoming reads.
 */
public final class ChatSession implements AutoCloseable {

    /** Text stored/shown in place of a message that never fully arrived. */
    public static final String FAILED_MARKER = "[message failed / incomplete]";

    private static final String LOCAL_SENDER = "me";
    private static final String PEER_SENDER = "peer";

    private final ChatDatabase db;
    private final Consumer<ChatDatabase.ChatMessage> onLogged;
    private final ChatEngine engine;

    public ChatSession(ChatDatabase db, ByteTransport transport, byte[] key,
                       Consumer<ChatDatabase.ChatMessage> onLogged) {
        this(db, transport, key, Duration.ofSeconds(5), onLogged);
    }

    public ChatSession(ChatDatabase db, ByteTransport transport, byte[] key, Duration reassemblyTimeout,
                       Consumer<ChatDatabase.ChatMessage> onLogged) {
        this.db = db;
        this.onLogged = onLogged;
        this.engine = new ChatEngine(transport, key, new ChatEngine.Listener() {
            @Override
            public void onMessageReceived(String plaintext) {
                persistAndPublish(PEER_SENDER, "IN", plaintext);
            }

            @Override
            public void onMessageFailed(String reason) {
                persistAndPublish(PEER_SENDER, "IN", FAILED_MARKER + " " + reason);
            }
        }, reassemblyTimeout);
    }

    /** Transmits {@code text} and, once handed to the transport, persists and publishes it as OUT. */
    public void send(String text) throws IOException {
        engine.send(text);
        persistAndPublish(LOCAL_SENDER, "OUT", text);
    }

    private void persistAndPublish(String senderId, String direction, String text) {
        ChatEngine.SealedText sealed = engine.seal(text);
        ChatDatabase.ChatMessage stored;
        try {
            synchronized (db) {
                db.insertMessage(senderId, direction,
                        Base64.getEncoder().encodeToString(sealed.ciphertext()),
                        Base64.getEncoder().encodeToString(sealed.nonce()),
                        text);
                stored = db.getRecentMessages(1).get(0);
            }
        } catch (SQLException e) {
            System.err.println("ChatSession: failed to persist " + direction + " message: " + e.getMessage());
            return;
        }
        onLogged.accept(stored);
    }

    @Override
    public void close() {
        engine.close();
    }
}
