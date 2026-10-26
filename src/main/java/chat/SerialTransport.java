package chat;

import com.fazecast.jSerialComm.SerialPort;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * SerialTransport
 * -------------------------------------------------------------------------
 * Production {@link ByteTransport} implementation that moves {@link
 * ChatFrame} bytes over a real USB serial connection via jSerialComm
 * (Phase 3+4 transport-crypto-layer, Issue 02 — serial-transport).
 *
 * Responsibilities:
 *   - Own the Reader thread: a plain blocking read() loop on the serial
 *     port's InputStream (no SerialPortDataListener/event-driven callback,
 *     no intermediate queue between the port and the reader logic)
 *   - Own the Writer thread: drains an outgoing frame queue and writes
 *     each frame to the serial port's OutputStream
 *   - Run both threads as daemon threads via ExecutorService, independent
 *     of each other and of any UI thread
 *
 * IMPORTANT — on-wire framing: unlike ESP-NOW (which delivers one discrete
 * packet per transmission), a serial connection is a continuous byte
 * stream with no inherent message boundaries — a single write() on one
 * end can legally arrive split across several read()s on the other, or
 * coalesced with the start of the next frame. To still hand {@link
 * ByteTransport#read()} callers back exactly one {@link ChatFrame}-sized
 * array per call, every frame is prefixed on the wire with its own 2-byte
 * big-endian length, and the Reader thread reassembles length header +
 * payload before a frame is ever handed off. This is a transport-level
 * framing concern only — frame contents are passed through unchanged, no
 * chat/crypto awareness lives here.
 */
public final class SerialTransport implements ByteTransport {

    private static final int LENGTH_PREFIX_BYTES = 2;
    private static final ThreadFactory DAEMON_THREADS = runnable -> {
        Thread thread = new Thread(runnable, "SerialTransport-worker");
        thread.setDaemon(true);
        return thread;
    };

    private final SerialPort port;
    private final BlockingQueue<byte[]> outbox = new LinkedBlockingQueue<>();
    private final BlockingQueue<Object> inbox = new LinkedBlockingQueue<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(2, DAEMON_THREADS);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicReference<IOException> transportFailure = new AtomicReference<>();

    /** Sentinel placed on {@link #inbox} to wake a blocked read() once the transport closes. */
    private static final Object CLOSED_SENTINEL = new Object();

    /**
     * Opens {@code systemPortName} (e.g. {@code "/dev/ttyACM0"}) at {@code
     * baudRate} and starts the Reader/Writer threads.
     */
    public SerialTransport(String systemPortName, int baudRate) throws IOException {
        this(SerialPort.getCommPort(systemPortName), baudRate);
    }

    SerialTransport(SerialPort port, int baudRate) throws IOException {
        this.port = port;
        port.setComPortParameters(baudRate, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
        // Fully blocking reads: InputStream.read() blocks until >=1 byte arrives, rather than
        // busy-polling. Closing the port interrupts the blocked read (see close()).
        port.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 0, 0);
        if (!port.openPort()) {
            throw new IOException("Failed to open serial port: " + port.getSystemPortName());
        }
        executor.submit(this::runReaderLoop);
        executor.submit(this::runWriterLoop);
    }

    @Override
    public void write(byte[] frame) throws IOException {
        if (closed.get()) {
            throw closedException();
        }
        outbox.add(frame);
    }

    @Override
    public byte[] read() throws IOException {
        try {
            Object next = inbox.take();
            if (next == CLOSED_SENTINEL) {
                inbox.put(CLOSED_SENTINEL); // let any other blocked reader see it too
                // Reaching the sentinel means the Reader loop has permanently stopped — true
                // regardless of whether close() was called explicitly or the loop exited on
                // its own (e.g. the port failed), so this always throws, not just when closed.
                throw closedException();
            }
            return (byte[]) next;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("read interrupted", e);
        }
    }

    /** Reports the transport as closed, with whichever side's failure caused it, if any. */
    private IOException closedException() {
        IOException cause = transportFailure.get();
        return cause != null ? new IOException("transport closed", cause) : new IOException("transport closed");
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        inbox.add(CLOSED_SENTINEL);
        port.closePort();
        executor.shutdownNow();
    }

    /**
     * Plain blocking read() loop on the serial InputStream — no event listener, no extra queue.
     * However the loop ends (an explicit {@link #close()}, or the port failing on its own),
     * it always finishes by calling {@link #close()} so {@code closed} and the failure cause
     * are consistent with the {@link #CLOSED_SENTINEL} that read() callers will see.
     */
    private void runReaderLoop() {
        try {
            InputStream in = port.getInputStream();
            while (!closed.get()) {
                byte[] header = readExactly(in, LENGTH_PREFIX_BYTES);
                if (header == null) {
                    break; // port closed/unavailable mid-read
                }
                int length = ByteBuffer.wrap(header).getShort() & 0xFFFF;
                byte[] payload = readExactly(in, length);
                if (payload == null) {
                    break;
                }
                inbox.add(payload);
            }
        } catch (IOException e) {
            transportFailure.compareAndSet(null, e);
        } finally {
            close();
        }
    }

    /**
     * Reads exactly {@code length} bytes via repeated blocking {@code read()} calls (a single
     * call may return fewer bytes than requested), or returns {@code null} if the stream ends
     * (closed) before {@code length} bytes are available.
     */
    private byte[] readExactly(InputStream in, int length) throws IOException {
        byte[] buffer = new byte[length];
        int filled = 0;
        while (filled < length) {
            if (closed.get()) {
                return null;
            }
            int n = in.read(buffer, filled, length - filled);
            if (n < 0) {
                return null;
            }
            filled += n;
        }
        return buffer;
    }

    /** Drains the outgoing queue and writes each frame, length-prefixed, to the OutputStream. */
    private void runWriterLoop() {
        try {
            OutputStream out = port.getOutputStream();
            while (!closed.get()) {
                byte[] frame = outbox.take();
                ByteBuffer buffer = ByteBuffer.allocate(LENGTH_PREFIX_BYTES + frame.length);
                buffer.putShort((short) frame.length);
                buffer.put(frame);
                out.write(buffer.array());
                out.flush();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            transportFailure.compareAndSet(null, e);
            close();
        }
    }
}
