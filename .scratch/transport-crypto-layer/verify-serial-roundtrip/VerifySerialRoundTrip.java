import chat.ByteTransport;
import chat.SerialTransport;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Manual hardware-verification script for Issue 02 (serial-transport). Not part of the Maven
 * test suite — the spec explicitly excludes SerialTransport's actual jSerialComm usage from
 * automated tests, since it needs real hardware.
 *
 * <p>Requires the companion {@code echo-sketch.ino} flashed to the board at the given port: it
 * echoes every byte it receives straight back out the same USB serial connection. Writing a
 * frame into {@link SerialTransport} therefore sends it out over the wire, the board echoes it
 * back unmodified, and it should arrive back at {@link SerialTransport#read()} byte-identical.
 *
 * <p>Usage: {@code java -cp <classpath> VerifySerialRoundTrip <systemPortName> <baudRate>}
 */
public class VerifySerialRoundTrip {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_FRAME_LENGTH = 234; // ChatFrame.MAX_FRAME_LENGTH

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: VerifySerialRoundTrip <systemPortName> <baudRate>");
            System.exit(2);
        }
        String portName = args[0];
        int baud = Integer.parseInt(args[1]);

        System.out.println("Opening " + portName + " @ " + baud + " (expects echo-sketch.ino flashed)...");
        ByteTransport transport = new SerialTransport(portName, baud);
        try {
            smallFrameRoundTrip(transport);
            maxSizeFrameRoundTrip(transport);
            pacedMultiFrameRoundTrip(transport);
            idleResilience(transport);
            // Run last: if the echo link can't keep up with a burst, this desynchronizes the
            // length-prefixed stream permanently and later read()s on this transport will hang.
            burstMultiFrameRoundTrip(transport);
            System.out.println();
            System.out.println("ALL CHECKS PASSED for " + portName);
        } finally {
            transport.close();
        }
    }

    private static void smallFrameRoundTrip(ByteTransport transport) throws Exception {
        byte[] sent = {1, 2, 3, 4, 5};
        transport.write(sent);
        byte[] received = transport.read();
        check("small frame byte-identical", Arrays.equals(sent, received));
        System.out.println("[PASS] small frame (" + sent.length + " bytes) round-tripped intact");
    }

    private static void maxSizeFrameRoundTrip(ByteTransport transport) throws Exception {
        byte[] sent = new byte[MAX_FRAME_LENGTH];
        RANDOM.nextBytes(sent);
        transport.write(sent);
        byte[] received = transport.read();
        check("max-size frame length preserved (no truncation)", received.length == sent.length);
        check("max-size frame byte-identical", Arrays.equals(sent, received));
        System.out.println("[PASS] max-size frame (" + sent.length + " bytes) round-tripped intact, no truncation");
    }

    /** Writes, then immediately reads back, each frame in turn — one in flight at a time. */
    private static void pacedMultiFrameRoundTrip(ByteTransport transport) throws Exception {
        int frameCount = 10;
        int mismatches = 0;
        for (int i = 0; i < frameCount; i++) {
            byte[] sent = new byte[20 + RANDOM.nextInt(MAX_FRAME_LENGTH - 20)];
            RANDOM.nextBytes(sent);
            transport.write(sent);
            byte[] received = transport.read();
            if (!Arrays.equals(sent, received)) {
                mismatches++;
                System.out.println("[DEBUG] paced frame " + i + " mismatch:");
                System.out.println("  sent     (" + sent.length + " bytes): " + hex(sent));
                System.out.println("  received (" + received.length + " bytes): " + hex(received));
            }
        }
        check(mismatches + " of " + frameCount + " paced frames mismatched", mismatches == 0);
        System.out.println("[PASS] " + frameCount + " paced (write-then-read) frames round-tripped intact");
    }

    /**
     * Writes all frames back-to-back before reading any of them back, with no pacing/flow
     * control — stresses whatever buffering exists between the two ends of the wire.
     */
    private static void burstMultiFrameRoundTrip(ByteTransport transport) throws Exception {
        int frameCount = 10;
        byte[][] sent = new byte[frameCount][];
        for (int i = 0; i < frameCount; i++) {
            sent[i] = new byte[20 + RANDOM.nextInt(MAX_FRAME_LENGTH - 20)];
            RANDOM.nextBytes(sent[i]);
            transport.write(sent[i]);
        }
        int mismatches = 0;
        for (int i = 0; i < frameCount; i++) {
            byte[] received = transport.read();
            if (!Arrays.equals(sent[i], received)) {
                mismatches++;
                System.out.println("[DEBUG] burst frame " + i + " mismatch:");
                System.out.println("  sent     (" + sent[i].length + " bytes): " + hex(sent[i]));
                System.out.println("  received (" + received.length + " bytes): " + hex(received));
            }
        }
        check(mismatches + " of " + frameCount + " burst frames mismatched", mismatches == 0);
        System.out.println("[PASS] " + frameCount + " back-to-back (burst) frames round-tripped intact and in order");
    }

    /** Confirms the Reader/Writer threads block rather than busy-poll while the wire is silent. */
    private static void idleResilience(ByteTransport transport) throws Exception {
        ThreadMXBean threadsBean = ManagementFactory.getThreadMXBean();
        long[] workerThreadIds = Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.getName().equals("SerialTransport-worker"))
                .mapToLong(Thread::threadId)
                .toArray();
        if (workerThreadIds.length == 0) {
            System.out.println("[SKIP] could not locate SerialTransport-worker threads for CPU-time check");
            return;
        }
        long cpuBefore = totalCpuTime(threadsBean, workerThreadIds);
        Thread.sleep(5000); // 5s of silence on the wire, nothing sent or expected
        long cpuAfter = totalCpuTime(threadsBean, workerThreadIds);
        long usedMillis = (cpuAfter - cpuBefore) / 1_000_000;
        check("Reader/Writer threads not busy-looping while idle (<50ms CPU over 5s idle)", usedMillis < 50);
        System.out.println("[PASS] idle for 5s used " + usedMillis
                + "ms total CPU across Reader+Writer threads (no busy-loop)");
    }

    private static long totalCpuTime(ThreadMXBean threadsBean, long[] threadIds) {
        long total = 0;
        for (long id : threadIds) {
            total += threadsBean.getThreadCpuTime(id);
        }
        return total;
    }

    private static void check(String description, boolean condition) {
        if (!condition) {
            throw new AssertionError("FAILED: " + description);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x ", b));
        }
        return sb.toString();
    }
}
