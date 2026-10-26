package chat;

import java.io.Closeable;
import java.io.IOException;

/**
 * ByteTransport
 * -------------------------------------------------------------------------
 * The seam {@link ChatEngine} sits behind (Phase 3+4 transport-crypto-layer,
 * Issue 01 — chat-engine-core).
 *
 * Responsibilities:
 *   - Write a frame out
 *   - Blockingly read the next frame in
 *
 * Production wiring is {@code SerialTransport} (jSerialComm, over USB serial
 * to an ESP32 "dumb modem"), added in a later issue; tests wire two
 * {@link ChatEngine} instances together through an in-memory pipe that
 * implements this same interface, with no hardware involved.
 *
 * Each frame handed to {@link #write} is a single logical unit (one
 * ESP-NOW packet in production), and each frame handed back by {@link
 * #read} must reconstruct that same unit exactly. ESP-NOW itself delivers
 * discrete packets, so implementations backed by it need no delimiting of
 * their own. A raw byte-stream implementation — {@code SerialTransport}'s
 * direct USB-serial link to the ESP32, which has no such packet boundary —
 * is the one case where additional on-wire delimiting (e.g. a length
 * prefix) is implementation-internal plumbing, not a change to what this
 * interface's callers see: they still get exactly one complete frame back
 * per {@link #read} call, unchanged.
 */
public interface ByteTransport extends Closeable {

    /** Write one complete frame. Must not interleave partial frames from concurrent callers. */
    void write(byte[] frame) throws IOException;

    /**
     * Block until the next frame arrives and return it.
     *
     * @throws IOException if the transport is closed or otherwise unable to
     *     produce any further frames; callers should treat this as a signal
     *     to stop reading, not retry.
     */
    byte[] read() throws IOException;
}
