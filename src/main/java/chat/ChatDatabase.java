package chat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * ChatDatabase
 * -------------------------------------------------------------------------
 * Embedded local storage layer for the Off-Grid E2EE Campus Chat project
 * (Phase 4 / Task 4.2 in the project workflow).
 *
 * Responsibilities:
 *   - Open/maintain a single embedded SQLite file (chat_store.db)
 *   - Create the "messages" table if it does not exist
 *   - Provide simple CRUD access for storing and retrieving chat history
 *
 * IMPORTANT: This class only ever stores/retrieves data that the caller
 * gives it. It has no knowledge of encryption — by design, encryption
 * (AES-256-GCM via JCA) and decryption happen in the crypto layer
 * (Task 4.3), *before* insertMessage() is called and *after*
 * getRecentMessages() is called. This keeps storage and cryptography
 * cleanly separated, which also makes it trivial to swap this class out
 * later without touching any crypto code.
 *
 * Column notes:
 *   - direction: "OUT" (sent by this node) or "IN" (received from a peer)
 *   - ciphertext_b64: the raw AES-GCM ciphertext (Base64), NOT plaintext.
 *     Storing ciphertext at rest means a stolen laptop/db file alone does
 *     not leak message content — the AES key in the KeyStore is still
 *     required to decrypt.
 *   - plaintext_cache: OPTIONAL convenience column. Left null by default;
 *     only populate it if you deliberately want fast re-display without
 *     re-decrypting on every read. For a stricter security posture, leave
 *     this column unused entirely and decrypt on read instead.
 */
public class ChatDatabase implements AutoCloseable {

    private static final String DB_FILE = "chat_store.db";
    private final Connection connection;

    public ChatDatabase() throws SQLException {
        this(DB_FILE);
    }

    public ChatDatabase(String dbFileName) throws SQLException {
        String url = "jdbc:sqlite:" + dbFileName;
        this.connection = DriverManager.getConnection(url);
        // Reasonable defaults for a small embedded, single-writer DB
        try (Statement pragma = connection.createStatement()) {
            pragma.execute("PRAGMA journal_mode = WAL");
            pragma.execute("PRAGMA synchronous = NORMAL");
            pragma.execute("PRAGMA foreign_keys = ON");
        }
        initializeSchema();
    }

    private void initializeSchema() throws SQLException {
        String ddl = """
            CREATE TABLE IF NOT EXISTS messages (
                id               INTEGER PRIMARY KEY AUTOINCREMENT,
                sender_id        TEXT    NOT NULL,
                direction        TEXT    NOT NULL CHECK (direction IN ('IN','OUT')),
                timestamp_epoch  INTEGER NOT NULL,
                ciphertext_b64   TEXT    NOT NULL,
                nonce_b64        TEXT    NOT NULL,
                plaintext_cache  TEXT
            );
            """;
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(ddl);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_messages_ts ON messages(timestamp_epoch)");
        }
    }

    /**
     * Insert a message record. Encryption must already have happened
     * upstream — this method never sees plaintext unless you explicitly
     * pass a plaintextCache value (optional, may be null).
     */
    public long insertMessage(String senderId,
                               String direction,
                               String ciphertextB64,
                               String nonceB64,
                               String plaintextCache) throws SQLException {
        String sql = """
            INSERT INTO messages (sender_id, direction, timestamp_epoch, ciphertext_b64, nonce_b64, plaintext_cache)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, senderId);
            ps.setString(2, direction);
            ps.setLong(3, Instant.now().getEpochSecond());
            ps.setString(4, ciphertextB64);
            ps.setString(5, nonceB64);
            if (plaintextCache == null) {
                ps.setNull(6, java.sql.Types.VARCHAR);
            } else {
                ps.setString(6, plaintextCache);
            }
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : -1L;
            }
        }
    }

    /** Fetch the most recent N messages, oldest-first, for chat log display. */
    public List<ChatMessage> getRecentMessages(int limit) throws SQLException {
        String sql = """
            SELECT id, sender_id, direction, timestamp_epoch, ciphertext_b64, nonce_b64, plaintext_cache
            FROM messages
            ORDER BY timestamp_epoch DESC, id DESC
            LIMIT ?
            """;
        List<ChatMessage> results = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new ChatMessage(
                            rs.getLong("id"),
                            rs.getString("sender_id"),
                            rs.getString("direction"),
                            rs.getLong("timestamp_epoch"),
                            rs.getString("ciphertext_b64"),
                            rs.getString("nonce_b64"),
                            rs.getString("plaintext_cache")
                    ));
                }
            }
        }
        // Reverse so caller gets oldest-first (natural reading order)
        java.util.Collections.reverse(results);
        return results;
    }

    /** Total message count — handy for CLI status lines / benchmarks. */
    public long countMessages() throws SQLException {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM messages")) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    /** Wipe all local chat history (e.g. "/clear" command in the REPL). */
    public void clearHistory() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("DELETE FROM messages");
        }
    }

    @Override
    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    /** Simple immutable record representing one stored chat message. */
    public record ChatMessage(
            long id,
            String senderId,
            String direction,
            long timestampEpoch,
            String ciphertextB64,
            String nonceB64,
            String plaintextCache
    ) {}
}
