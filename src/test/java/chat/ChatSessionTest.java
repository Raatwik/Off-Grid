package chat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link ChatSession} — the GUI-facing glue between {@link ChatEngine} and
 * {@link ChatDatabase} — over the in-memory transport seam, with no Swing and no hardware.
 */
class ChatSessionTest {

    private static final byte[] KEY = new byte[32];

    static {
        new SecureRandom().nextBytes(KEY);
    }

    private static final Duration SHORT_TIMEOUT = Duration.ofMillis(300);

    @TempDir
    Path tempDir;

    private final List<AutoCloseable> closeables = new ArrayList<>();

    private final List<ChatDatabase.ChatMessage> aliceEvents = new CopyOnWriteArrayList<>();
    private final List<ChatDatabase.ChatMessage> bobEvents = new CopyOnWriteArrayList<>();
    private ChatDatabase aliceDb;
    private ChatDatabase bobDb;
    private InMemoryByteTransport aliceWire;
    private ChatSession alice;
    private ChatSession bob;

    @BeforeEach
    void setUp() throws Exception {
        aliceDb = new ChatDatabase(tempDir.resolve("alice.db").toString());
        bobDb = new ChatDatabase(tempDir.resolve("bob.db").toString());
        InMemoryByteTransport[] wires = InMemoryByteTransport.pair();
        aliceWire = wires[0];
        alice = new ChatSession(aliceDb, wires[0], KEY, SHORT_TIMEOUT, aliceEvents::add);
        bob = new ChatSession(bobDb, wires[1], KEY, SHORT_TIMEOUT, bobEvents::add);
        closeables.add(alice);
        closeables.add(bob);
        closeables.add(aliceDb);
        closeables.add(bobDb);
    }

    @AfterEach
    void tearDown() throws Exception {
        for (AutoCloseable c : closeables) {
            c.close();
        }
    }

    @Test
    void sentMessageIsPersistedAsOutgoingAndPublished() throws Exception {
        alice.send("hello bob");

        assertEquals(1, aliceEvents.size());
        ChatDatabase.ChatMessage event = aliceEvents.get(0);
        assertEquals("OUT", event.direction());
        assertEquals("hello bob", event.plaintextCache());

        List<ChatDatabase.ChatMessage> stored = aliceDb.getRecentMessages(10);
        assertEquals(1, stored.size());
        assertEquals("OUT", stored.get(0).direction());
        assertEquals("hello bob", stored.get(0).plaintextCache());
    }

    @Test
    void storedCiphertextIsRealEncryptionNotBase64OfPlaintext() throws Exception {
        alice.send("secret words");

        ChatDatabase.ChatMessage stored = aliceDb.getRecentMessages(1).get(0);
        String fakeCiphertext = Base64.getEncoder().encodeToString("secret words".getBytes());
        assertNotEquals(fakeCiphertext, stored.ciphertextB64());
        assertFalse(stored.ciphertextB64().isEmpty());
        assertEquals(12, Base64.getDecoder().decode(stored.nonceB64()).length);
    }

    @Test
    void incomingMessageIsPersistedAsIncomingAndPublishedWithoutRefresh() throws Exception {
        alice.send("hi from alice");

        waitUntil(() -> !bobEvents.isEmpty());
        ChatDatabase.ChatMessage event = bobEvents.get(0);
        assertEquals("IN", event.direction());
        assertEquals("hi from alice", event.plaintextCache());

        List<ChatDatabase.ChatMessage> stored = bobDb.getRecentMessages(10);
        assertEquals(1, stored.size());
        assertEquals("IN", stored.get(0).direction());
        assertEquals("hi from alice", stored.get(0).plaintextCache());
    }

    @Test
    void multiChunkMessageRoundTrips() throws Exception {
        String longText = "x".repeat(ChatEngine.MAX_CHUNK_PLAINTEXT_BYTES * 3 + 7);
        alice.send(longText);

        waitUntil(() -> !bobEvents.isEmpty());
        assertEquals(longText, bobEvents.get(0).plaintextCache());
    }

    @Test
    void incompleteMessageSurfacesAsFailureMarkerAndIsPersisted() throws Exception {
        // Drop every chunk after the first so the receiver's reassembly buffer times out.
        int[] seen = {0};
        aliceWire.setOutboundInterceptor(frame -> seen[0]++ == 0 ? frame : null);

        alice.send("y".repeat(ChatEngine.MAX_CHUNK_PLAINTEXT_BYTES * 2));

        waitUntil(() -> !bobEvents.isEmpty());
        ChatDatabase.ChatMessage marker = bobEvents.get(0);
        assertEquals("IN", marker.direction());
        assertNotNull(marker.plaintextCache());
        assertTrue(marker.plaintextCache().contains(ChatSession.FAILED_MARKER));

        List<ChatDatabase.ChatMessage> stored = bobDb.getRecentMessages(10);
        assertEquals(1, stored.size());
        assertTrue(stored.get(0).plaintextCache().contains(ChatSession.FAILED_MARKER));
    }

    private static void waitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("condition not met within 5s");
            }
            Thread.sleep(10);
        }
    }
}
