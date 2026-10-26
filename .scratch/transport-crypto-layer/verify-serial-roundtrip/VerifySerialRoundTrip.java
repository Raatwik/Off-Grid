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
            orderedMultiFrameRoundTrip(transport);
            idleResilience(transport);
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

    private static void orderedMultiFrameRoundTrip(ByteTransport transport) throws Exception {
        int frameCount = 10;
        byte[][] sent = new byte[frameCount][];
        for (int i = 0; i < frameCount; i++) {
            sent[i] = new byte[20 + RANDOM.nextInt(MAX_FRAME_LENGTH - 20)];
            RANDOM.nextBytes(sent[i]);
            transport.write(sent[i]);
        }
        for (int i = 0; i < frameCount; i++) {
            byte[] received = transport.read();
            check("frame " + i + " byte-identical and in order", Arrays.equals(sent[i], received));
        }
        System.out.println("[PASS] " + frameCount + " back-to-back frames round-tripped intact and in order");
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
}
