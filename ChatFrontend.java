package chat;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;

/**
 * ChatFrontend
 * -------------------------------------------------------------------------
 * Basic Swing GUI frontend for the Off-Grid E2EE Campus Chat project
 * (Phase 1 JDBC layer, viewed through a window instead of a terminal).
 *
 * This is deliberately simple:
 *   - A scrollable chat log at the top
 *   - A text field + Send button at the bottom
 *   - A "Simulate Incoming" button so you can test both directions
 *     (OUT / IN) before the real transport layer (Wi-Fi/BLE/ESP-NOW)
 *     and crypto layer exist
 *   - A status bar showing the local message count
 *
 * Just like ChatDemo, this does NOT do real encryption yet. Messages
 * are stored as fake "ciphertext" (Base64 of the plaintext) purely so
 * the JDBC layer has something to persist. Swap handleOutgoing() and
 * the receive path over to real AES-256-GCM once Task 4.3 exists.
 *
 * Run with:
 *   javac -cp sqlite-jdbc-3.46.1.3.jar -d build ChatDatabase.java ChatFrontend.java
 *   java  -cp "build;sqlite-jdbc-3.46.1.3.jar" chat.ChatFrontend      (Windows)
 *   java  -cp "build:sqlite-jdbc-3.46.1.3.jar" chat.ChatFrontend      (Mac/Linux)
 */
public class ChatFrontend extends JFrame {

    private static final DateTimeFormatter TS_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final ChatDatabase db;
    private final JTextArea logArea = new JTextArea();
    private final JTextField inputField = new JTextField();
    private final JLabel statusLabel = new JLabel("Messages: 0");

    public ChatFrontend(ChatDatabase db) {
        super("Off-Grid Campus Chat (Phase 1 - local demo)");
        this.db = db;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(520, 480);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(8, 8));

        buildLogPanel();
        buildInputPanel();
        buildStatusBar();

        loadHistory();
    }

    private void buildLogPanel() {
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 8));
        add(scrollPane, BorderLayout.CENTER);
    }

    private void buildInputPanel() {
        inputField.addActionListener(this::onSend); // Enter key sends

        JButton sendButton = new JButton("Send");
        sendButton.addActionListener(this::onSend);

        JButton simulateReceiveButton = new JButton("Simulate Incoming");
        simulateReceiveButton.addActionListener(this::onSimulateReceive);

        JButton clearButton = new JButton("Clear History");
        clearButton.addActionListener(this::onClear);

        JPanel buttonRow = new JPanel(new GridLayout(1, 2, 4, 0));
        buttonRow.add(sendButton);
        buttonRow.add(simulateReceiveButton);

        JPanel bottomPanel = new JPanel(new BorderLayout(4, 4));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        bottomPanel.add(inputField, BorderLayout.CENTER);
        bottomPanel.add(buttonRow, BorderLayout.EAST);

        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(bottomPanel, BorderLayout.NORTH);
        southPanel.add(clearButton, BorderLayout.SOUTH);

        add(southPanel, BorderLayout.SOUTH);
    }

    private void buildStatusBar() {
        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 8, 4, 8));
        add(statusLabel, BorderLayout.NORTH);
    }

    private void loadHistory() {
        try {
            List<ChatDatabase.ChatMessage> messages = db.getRecentMessages(100);
            for (ChatDatabase.ChatMessage m : messages) {
                appendToLog(m);
            }
            refreshStatus();
        } catch (SQLException e) {
            showError("Failed to load history", e);
        }
    }

    private void onSend(ActionEvent e) {
        String text = inputField.getText().trim();
        if (text.isEmpty()) return;
        storeAndDisplay(text, "OUT", "me");
        inputField.setText("");
    }

    private void onSimulateReceive(ActionEvent e) {
        String text = inputField.getText().trim();
        if (text.isEmpty()) {
            text = "(simulated peer message)";
        }
        storeAndDisplay(text, "IN", "peer");
        inputField.setText("");
    }

    private void onClear(ActionEvent e) {
        int confirm = JOptionPane.showConfirmDialog(
                this, "Clear all local chat history?", "Confirm",
                JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;
        try {
            db.clearHistory();
            logArea.setText("");
            refreshStatus();
        } catch (SQLException ex) {
            showError("Failed to clear history", ex);
        }
    }

    /**
     * NOTE: This "encodes" plaintext as Base64 and calls it ciphertext,
     * purely so the demo has something plausible to store before the
     * real AES-256-GCM layer (Task 4.3) is wired in. Replace this with
     * a call into the crypto module once it exists — do not ship this
     * placeholder as real encryption.
     */
    private void storeAndDisplay(String text, String direction, String senderId) {
        try {
            String fakeCiphertext = Base64.getEncoder().encodeToString(text.getBytes());
            String fakeNonce = Base64.getEncoder().encodeToString("placeholder-nonce".getBytes());
            long id = db.insertMessage(senderId, direction, fakeCiphertext, fakeNonce, text);

            List<ChatDatabase.ChatMessage> latest = db.getRecentMessages(1);
            if (!latest.isEmpty()) {
                appendToLog(latest.get(0));
            }
            refreshStatus();
        } catch (SQLException ex) {
            showError("Failed to store message", ex);
        }
    }

    private void appendToLog(ChatDatabase.ChatMessage m) {
        String time = TS_FORMAT.format(Instant.ofEpochSecond(m.timestampEpoch()));
        String arrow = m.direction().equals("OUT") ? "->" : "<-";
        String shown = m.plaintextCache() != null
                ? m.plaintextCache()
                : "[encrypted: " + m.ciphertextB64() + "]";
        logArea.append(String.format("[%s] %s %s %s%n", time, m.senderId(), arrow, shown));
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private void refreshStatus() {
        try {
            statusLabel.setText("Messages: " + db.countMessages());
        } catch (SQLException e) {
            statusLabel.setText("Messages: (error)");
        }
    }

    private void showError(String context, Exception e) {
        JOptionPane.showMessageDialog(this, context + ": " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
    }

    public static void main(String[] args) {
        try {
            ChatDatabase db = new ChatDatabase();
            SwingUtilities.invokeLater(() -> new ChatFrontend(db).setVisible(true));
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(null,
                    "Failed to open local database: " + e.getMessage(),
                    "Startup Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }
}
