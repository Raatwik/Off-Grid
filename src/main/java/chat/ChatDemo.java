package chat;

import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Scanner;

/**
 * ChatDemo
 * -------------------------------------------------------------------------
 * Minimal CLI front end to sanity-check ChatDatabase before the real
 * crypto (Task 4.3) and transport (Task 3.x / 4.4) layers exist.
 *
 * This is intentionally NOT the real chat REPL (that comes with
 * JLine3/Lanterna in a later task) — it just proves the JDBC layer
 * works end to end: insert, list, count, clear.
 *
 * Run with:
 *   mvn compile exec:java -Dexec.mainClass=chat.ChatDemo
 * or after packaging:
 *   java -jar chat-app.jar
 *
 * Commands:
 *   /send <text>   store a fake "OUT" message (ciphertext is faked as
 *                  base64 of the plaintext, since no crypto layer exists yet)
 *   /recv <text>   store a fake "IN" message, as if it came from a peer
 *   /log [n]       print the last n messages (default 10)
 *   /count         print total stored message count
 *   /clear         wipe local history
 *   /quit          exit
 */
public class ChatDemo {

    private static final DateTimeFormatter TS_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    public static void main(String[] args) {
        System.out.println("Off-Grid Campus Chat - JDBC frontend demo (Phase 1)");
        System.out.println("Local store: chat_store.db");
        System.out.println("Type /send <text>, /recv <text>, /log, /count, /clear, or /quit");

        try (ChatDatabase db = new ChatDatabase();
             Scanner scanner = new Scanner(System.in)) {

            while (true) {
                System.out.print("> ");
                if (!scanner.hasNextLine()) break;
                String line = scanner.nextLine().trim();
                if (line.isEmpty()) continue;

                try {
                    if (line.equals("/quit")) {
                        break;
                    } else if (line.startsWith("/send ")) {
                        handleFakeMessage(db, line.substring(6), "OUT", "me");
                    } else if (line.startsWith("/recv ")) {
                        handleFakeMessage(db, line.substring(6), "IN", "peer");
                    } else if (line.equals("/log") || line.startsWith("/log ")) {
                        int limit = 10;
                        String[] parts = line.split("\\s+");
                        if (parts.length == 2) {
                            limit = Integer.parseInt(parts[1]);
                        }
                        printLog(db, limit);
                    } else if (line.equals("/count")) {
                        System.out.println("Stored messages: " + db.countMessages());
                    } else if (line.equals("/clear")) {
                        db.clearHistory();
                        System.out.println("History cleared.");
                    } else {
                        System.out.println("Unknown command. Try /send, /recv, /log, /count, /clear, /quit");
                    }
                } catch (SQLException e) {
                    System.out.println("DB error: " + e.getMessage());
                }
            }
        } catch (SQLException e) {
            System.err.println("Failed to open local database: " + e.getMessage());
            System.exit(1);
        }

        System.out.println("Goodbye.");
    }

    /**
     * NOTE: This "encodes" plaintext as Base64 and calls it ciphertext,
     * purely so the demo has something plausible to store before the
     * real AES-256-GCM layer (Task 4.3) is wired in. Replace this with
     * a call into the crypto module once it exists — do not ship this
     * placeholder as real encryption.
     */
    private static void handleFakeMessage(ChatDatabase db, String text, String direction, String senderId)
            throws SQLException {
        String fakeCiphertext = Base64.getEncoder().encodeToString(text.getBytes());
        String fakeNonce = Base64.getEncoder().encodeToString("placeholder-nonce".getBytes());
        long id = db.insertMessage(senderId, direction, fakeCiphertext, fakeNonce, text);
        System.out.println("Stored message #" + id + " (" + direction + ")");
    }

    private static void printLog(ChatDatabase db, int limit) throws SQLException {
        List<ChatDatabase.ChatMessage> messages = db.getRecentMessages(limit);
        if (messages.isEmpty()) {
            System.out.println("(no messages yet)");
            return;
        }
        for (ChatDatabase.ChatMessage m : messages) {
            String time = TS_FORMAT.format(Instant.ofEpochSecond(m.timestampEpoch()));
            String arrow = m.direction().equals("OUT") ? "->" : "<-";
            String shown = m.plaintextCache() != null
                    ? m.plaintextCache()
                    : "[encrypted: " + m.ciphertextB64() + "]";
            System.out.printf("[%s] %s %s %s%n", time, m.senderId(), arrow, shown);
        }
    }
}
