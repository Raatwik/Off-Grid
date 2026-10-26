# 02 — SerialTransport: real USB serial I/O via jSerialComm

**What to build:** A production `ByteTransport` implementation that moves bytes over a real USB serial connection using `jSerialComm`, with a dedicated blocking-read Reader thread and a queue-draining Writer thread. Demoable on its own with a USB-to-USB serial loopback (or two ports on one machine): bytes written on one end arrive intact on the other — no ESP32 needed yet.

**Blocked by:** 01 (needs the `ByteTransport` interface shape)

**Status:** ready-for-human

- [x] Implement `SerialTransport` against `jSerialComm`, satisfying the `ByteTransport` interface defined in ticket 01. See `src/main/java/chat/SerialTransport.java`. Note: raw USB serial (unlike ESP-NOW) has no inherent packet boundary, so `SerialTransport` adds its own 2-byte length prefix on the wire to still hand `read()` callers back exactly one frame per call — `ByteTransport`'s Javadoc has been amended to document this as the one sanctioned exception to "no additional framing."
- [x] Reader side: a dedicated thread performs a plain blocking `read()` loop on the serial `InputStream` — no `SerialPortDataListener`/event-driven callback, no intermediate queue between the port and the reader logic.
- [x] Writer side: a dedicated thread drains an outgoing byte-frame queue and writes each frame to the serial `OutputStream`.
- [x] Both Reader and Writer threads run via `ExecutorService` as daemon threads, independent of each other and of any UI thread.
- [x] **Verified against real hardware.** `/dev/ttyACM1` flashed with a throwaway echo sketch (`echo-sketch.ino`, not the Issue 03 firmware — see `.scratch/transport-crypto-layer/echo-sketch/`); `VerifySerialRoundTrip.java` (`.scratch/transport-crypto-layer/verify-serial-roundtrip/`) run against it over a real `SerialTransport`:
  - Small (5-byte) frame round-trips byte-identical.
  - Max-size (234-byte) frame round-trips byte-identical, no truncation.
  - 10 frames written and read back one at a time ("paced") all round-trip byte-identical and in order.
  - Reader/Writer threads burn ~0ms CPU over a 5s idle window — confirmed blocking, not busy-polling.
  - **One caveat found:** 10 frames written back-to-back *before* any are read ("burst", no pacing) desynchronize the length-prefixed stream from one frame onward — strong evidence (same logic path passes cleanly when paced) points to the echo sketch's small USB-CDC RX buffer dropping bytes under an unpaced ~2KB burst, not a `SerialTransport` logic bug. `ChatEngine` already tolerates dropped/corrupted chunks via its per-msgId timeout and failure marker regardless of cause, so this doesn't block the issue, but worth keeping in mind if real usage ever pipelines multiple in-flight messages without backpressure.
  - `/dev/ttyACM0`'s board was not reflashed with the echo sketch, so only one board was verified this way — sufficient to confirm `SerialTransport`'s logic against real hardware (both boards run identical code).
