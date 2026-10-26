package chat;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;

/**
 * In-memory, in-process {@link ByteTransport} test seam: two instances
 * created via {@link #pair()} are wired directly to each other, so a
 * {@link ChatEngine} round-trip can be exercised with no real serial port
 * and no ESP32 hardware.
 *
 * <p>An outbound interceptor can be installed to simulate an unreliable
 * wire: returning {@code null} drops the frame (simulating a lost chunk),
 * and returning a mutated array simulates a corrupted chunk.
 */
final class InMemoryByteTransport implements ByteTransport {

    private final BlockingQueue<byte[]> inbox = new LinkedBlockingQueue<>();
    private InMemoryByteTransport peer;
    private volatile UnaryOperator<byte[]> outboundInterceptor = UnaryOperator.identity();
    private volatile boolean closed = false;

    private InMemoryByteTransport() {
    }

    static InMemoryByteTransport[] pair() {
        InMemoryByteTransport a = new InMemoryByteTransport();
        InMemoryByteTransport b = new InMemoryByteTransport();
        a.peer = b;
        b.peer = a;
        return new InMemoryByteTransport[] {a, b};
    }

    /** Applied to every frame this side writes, before it reaches the peer's inbox. */
    void setOutboundInterceptor(UnaryOperator<byte[]> interceptor) {
        this.outboundInterceptor = interceptor;
    }

    @Override
    public void write(byte[] frame) throws IOException {
        if (closed) {
            throw new IOException("transport closed");
        }
        byte[] toDeliver = outboundInterceptor.apply(frame);
        if (toDeliver != null) {
            peer.inbox.add(toDeliver);
        }
    }

    @Override
    public byte[] read() throws IOException {
        while (!closed) {
            try {
                byte[] frame = inbox.poll(100, TimeUnit.MILLISECONDS);
                if (frame != null) {
                    return frame;
                }
            } catch (InterruptedException e) {
                throw new IOException("read interrupted", e);
            }
        }
        throw new IOException("transport closed");
    }

    @Override
    public void close() {
        closed = true;
    }
}
