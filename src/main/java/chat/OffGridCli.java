package chat;

import java.io.IOException;
import java.util.Scanner;

/**
 * OffGridCli
 * -------------------------------------------------------------------------
 * Headless end-to-end harness (Phase 3+4 transport-crypto-layer, Issue 04):
 * wires a {@link ChatEngine} to a real {@link SerialTransport} so two
 * laptops, each with a flashed ESP32 attached, can exchange encrypted
 * messages over the ESP-NOW link with no GUI.
 *
 * Run (one per laptop):
 *   java -cp target/chat-app.jar chat.OffGridCli /dev/ttyACM0 [baud]
 *
 * Each line typed on stdin is sent; received messages and failures are
 * printed as they arrive. EOF or /quit exits.
 *
 * The AES-256 key is hardcoded and identical on both laptops by design
 * (project scope: no key exchange). Do not reuse it for anything real.
 */
public final class OffGridCli {

    static final int DEFAULT_BAUD = 115200;

    static final byte[] SHARED_KEY = {
        0x4f, 0x66, 0x66, 0x2d, 0x47, 0x72, 0x69, 0x64, 0x2d, 0x43, 0x61, 0x6d, 0x70, 0x75, 0x73, 0x2d,
        0x43, 0x68, 0x61, 0x74, 0x2d, 0x4b, 0x65, 0x79, 0x2d, 0x76, 0x31, 0x2d, 0x32, 0x30, 0x32, 0x36
    };

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("usage: OffGridCli <serial-port> [baud]");
            System.exit(2);
        }
        int baud = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_BAUD;

        ChatEngine.Listener listener = new ChatEngine.Listener() {
            @Override
            public void onMessageReceived(String plaintext) {
                System.out.println("<< " + plaintext);
            }

            @Override
            public void onMessageFailed(String reason) {
                System.out.println("!! " + reason);
            }
        };

        try (ChatEngine engine = new ChatEngine(new SerialTransport(args[0], baud), SHARED_KEY, listener);
             Scanner scanner = new Scanner(System.in)) {
            System.out.println("Connected on " + args[0] + " @ " + baud + ". Type a message, /quit to exit.");
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (line.equals("/quit")) {
                    break;
                }
                if (!line.isEmpty()) {
                    engine.send(line);
                    System.out.println(">> sent " + line.length() + " chars");
                }
            }
        }
    }
}
