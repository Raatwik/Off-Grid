# 02 — SerialTransport: real USB serial I/O via jSerialComm

**What to build:** A production `ByteTransport` implementation that moves bytes over a real USB serial connection using `jSerialComm`, with a dedicated blocking-read Reader thread and a queue-draining Writer thread. Demoable on its own with a USB-to-USB serial loopback (or two ports on one machine): bytes written on one end arrive intact on the other — no ESP32 needed yet.

**Blocked by:** 01 (needs the `ByteTransport` interface shape)

**Status:** ready-for-human

- [x] Implement `SerialTransport` against `jSerialComm`, satisfying the `ByteTransport` interface defined in ticket 01. See `src/main/java/chat/SerialTransport.java`. Note: raw USB serial (unlike ESP-NOW) has no inherent packet boundary, so `SerialTransport` adds its own 2-byte length prefix on the wire to still hand `read()` callers back exactly one frame per call — `ByteTransport`'s Javadoc has been amended to document this as the one sanctioned exception to "no additional framing."
- [x] Reader side: a dedicated thread performs a plain blocking `read()` loop on the serial `InputStream` — no `SerialPortDataListener`/event-driven callback, no intermediate queue between the port and the reader logic.
- [x] Writer side: a dedicated thread drains an outgoing byte-frame queue and writes each frame to the serial `OutputStream`.
- [x] Both Reader and Writer threads run via `ExecutorService` as daemon threads, independent of each other and of any UI thread.
- [ ] **Partially verified.** Opened a real `SerialTransport` against both actual ESP32 boards (no firmware flashed yet), once `/dev/ttyACM0` was freed from Arduino IDE's Serial Monitor: on each of `/dev/ttyACM0` and `/dev/ttyACM1`, port opened, `write()` didn't throw, `close()` cleanly shut down both daemon threads with none left alive.
  - **Still not verified:** an actual byte-perfect round trip (write on one `SerialTransport`, read back unmodified/in-order/untruncated on another). This needs either the ESP32 dumb-relay firmware (a separate, non-Java deliverable, out of this issue's scope) or a temporary echo sketch — neither exists yet, so there was nothing on the other end of either port to read bytes back from. Needs a human with the hardware to close out.
- [ ] **Not verified beyond the above:** Reader-thread resilience when no bytes arrive for an extended period (only a ~200ms idle window was observed in the smoke test, not a sustained quiet port).
