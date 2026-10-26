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
 * Each frame is a single, already-delimited unit (one ESP-NOW packet in
 * production) — implementations are not expected to do any additional
 * framing/delimiting of their own.
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
