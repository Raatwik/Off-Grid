# 04 — End-to-end real off-grid round trip

**What to build:** The first fully real demo of the system: two laptops, each with a flashed ESP32 board attached over USB, exchange an AES-256-GCM-encrypted message over the real wireless ESP-NOW link — no GUI yet, this proves the real path works underneath. Laptop A → USB → ESP32 → ESP-NOW (wireless) → ESP32 → USB → Laptop B, decrypting correctly on arrival.

**Blocked by:** 01, 02, 03

**Status:** ready-for-agent

- [ ] Wire `ChatEngine` (01) to `SerialTransport` (02) on both laptops, each laptop's `SerialTransport` connected to its own flashed ESP32 (03).
- [ ] Send a short, single-chunk message from Laptop A and confirm it arrives decrypted and correct on Laptop B.
- [ ] Send a multi-chunk message and confirm it reassembles correctly on the receiving laptop after traveling over the real ESP-NOW link.
- [ ] Confirm the timeout/failure-marker behavior from ticket 01 still surfaces correctly when a real chunk is lost over the wireless link (e.g. by moving boards out of range mid-send), not just in the in-memory test seam.
- [ ] Confirm the round trip works with no Wi-Fi router, no Bluetooth, and no internet connection active anywhere in range — only the USB-tethered ESP32 boards and the wireless ESP-NOW hop between them.
- [ ] Document any real-world framing/timing adjustments discovered against actual hardware (e.g. serial baud rate, ESP-NOW send timing) that weren't visible in the in-memory/loopback tests from tickets 01–03.

## Comments

Started (2026-10-27). Added `src/main/java/chat/OffGridCli.java`: headless stdin/stdout harness wiring `ChatEngine` to `SerialTransport` with the hardcoded shared AES-256 key (same key on every laptop), 115200 baud (matches the relay firmware). Run one per laptop: `java -cp <classes>:jSerialComm.jar chat.OffGridCli /dev/ttyACM0`.

Single-machine pre-check, both flashed boards on this one laptop (`/dev/ttyACM0` and `/dev/ttyACM1`, two CLI processes), so traffic really crossed the ESP-NOW hop:
- Short single-chunk message (12 chars) A → B: arrived decrypted and correct.
- 612-char message (3 chunks) A → B: reassembled correctly, full length, over the real link.

Not yet done (checkboxes left unticked on purpose — the ticket calls for two laptops):
- Repeat on two separate laptops, one board each.
- Out-of-range mid-send to confirm the timeout / "message incomplete" failure surfaces on a real lost chunk.
- Confirm with Wi-Fi router, Bluetooth and internet all off.
- Record any baud/timing findings. None so far at 115200 for 3 chunks; burst/larger messages untested.
