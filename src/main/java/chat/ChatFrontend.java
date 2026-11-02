package chat;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ChatFrontend
 * -------------------------------------------------------------------------
 * Basic Swing GUI frontend for the Off-Grid E2EE Campus Chat project
 * (Phase 1 JDBC layer, viewed through a window instead of a terminal).
 *
 * Layout:
 *   - A scrollable chat log at the top
 *   - A text field + Send button at the bottom
 *   - A status bar showing the local message count
 *
 * Sending and receiving go through a {@link ChatSession} (real AES-256-GCM
 * over the ESP32/ESP-NOW serial link). The session's Reader/Writer threads
 * run independently of the Swing event thread; every row it persists is
 * published here via SwingUtilities.invokeLater, so incoming messages and
 * "message failed / incomplete" markers appear live with no refresh.
 *
 * Run with (one per laptop, ESP32 attached):
 *   java -cp target/chat-app.jar chat.ChatFrontend /dev/ttyACM0 [baud]
 */
public class ChatFrontend extends JFrame {

    private static final DateTimeFormatter TS_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final ChatDatabase db;
    private final ChatSession session;
    // Sends run here, not on the Swing thread, so a slow transport/DB write never freezes the window.
    private final ExecutorService sender = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "chat-frontend-sender");
        thread.setDaemon(true);
        return thread;
    });
    private final JTextArea logArea = new JTextArea();
    private final JTextField inputField = new JTextField();
    private final JLabel statusLabel = new JLabel("Messages: 0");

    public ChatFrontend(ChatDatabase db, ByteTransport transport) {
        super("Off-Grid Campus Chat");
        this.db = db;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                sender.shutdownNow();
                session.close();
            }
        });
        setSize(520, 480);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(8, 8));

        buildLogPanel();
        buildInputPanel();
        buildStatusBar();

        loadHistory();
        // Started last so no live row can race the history load into a duplicate.
        this.session = new ChatSession(db, transport, OffGridCli.SHARED_KEY,
                message -> SwingUtilities.invokeLater(() -> onLogged(message)));
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

        JButton clearButton = new JButton("Clear History");
        clearButton.addActionListener(this::onClear);


        JPanel bottomPanel = new JPanel(new BorderLayout(4, 4));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        bottomPanel.add(inputField, BorderLayout.CENTER);
        bottomPanel.add(sendButton, BorderLayout.EAST);

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
            List<ChatDatabase.ChatMessage> messages;
            synchronized (db) {
                messages = db.getRecentMessages(100);
            }
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
        inputField.setText("");
        sender.submit(() -> {
            try {
                session.send(text); // the logged row comes back via onLogged
            } catch (IOException | IllegalArgumentException ex) {
                SwingUtilities.invokeLater(() -> showError("Failed to send message", ex));
            }
        });
    }

    /** Runs on the Swing event thread: shows a row the session just persisted. */
    private void onLogged(ChatDatabase.ChatMessage message) {
        appendToLog(message);
        refreshStatus();
    }

    private void onClear(ActionEvent e) {
        int confirm = JOptionPane.showConfirmDialog(
                this, "Clear all local chat history?", "Confirm",
                JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;
        try {
            synchronized (db) {
                db.clearHistory();
            }
            logArea.setText("");
            refreshStatus();
        } catch (SQLException ex) {
            showError("Failed to clear history", ex);
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
            long count;
            synchronized (db) {
                count = db.countMessages();
            }
            statusLabel.setText("Messages: " + count);
        } catch (SQLException e) {
            statusLabel.setText("Messages: (error)");
        }
    }

    private void showError(String context, Exception e) {
        JOptionPane.showMessageDialog(this, context + ": " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("usage: ChatFrontend <serial-port> [baud]");
            System.exit(2);
        }
        int baud = args.length > 1 ? Integer.parseInt(args[1]) : OffGridCli.DEFAULT_BAUD;
        try {
            ChatDatabase db = new ChatDatabase();
            ByteTransport transport = new SerialTransport(args[0], baud);
            SwingUtilities.invokeLater(() -> new ChatFrontend(db, transport).setVisible(true));
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(null,
                    "Failed to open local database: " + e.getMessage(),
                    "Startup Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null,
                    "Failed to open serial port " + args[0] + ": " + e.getMessage(),
                    "Startup Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }
}
