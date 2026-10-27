# 03 — ESP32 "dumb modem" firmware

**What to build:** Arduino firmware, flashed to two ESP32 boards, that blindly relays bytes between USB Serial and ESP-NOW in both directions — no chat, crypto, or chunking awareness lives on the device. Demoable on its own by sending raw bytes into one board over USB and observing them appear out the other board's USB — no Java app involved yet.

**Blocked by:** None — independent, non-Java track; can run in parallel with tickets 01 and 02

**Status:** done

- [x] Build/flash toolchain: Arduino IDE (no `arduino-cli` on this machine; esp32 core 3.3.7 already installed). Flashing stays manual through the IDE GUI — see the wizard below.
- [x] Each board's firmware has the other board's MAC address hardcoded at flash time (unicast `esp_now_send`, no broadcast, no runtime discovery/pairing handshake) — fixed two-board pair only. See `PEER_MAC` in `esp-now-relay.ino`; the wizard fills in each board's real peer MAC and writes two per-board copies.
- [x] Serial → ESP-NOW path: bytes read from the board's USB Serial are relayed verbatim via `esp_now_send()` to the hardcoded peer MAC, respecting ESP-NOW's ~250-byte payload ceiling (upstream frames are expected to already fit, per ticket 01's ~234-byte frame budget).
- [x] ESP-NOW → Serial path: bytes arriving via the ESP-NOW receive callback are written verbatim out the board's USB Serial.
- [x] No chat/message semantics, no crypto, no chunk/frame parsing on the firmware side — it is a dumb, symmetric byte relay in both directions.
- [x] Verify with real hardware: raw bytes sent into board A's USB serial arrive at board B's USB serial (and vice versa) over the wireless ESP-NOW link, with no modification and no silent drops under normal conditions.

## Comments

Firmware written, not yet flashed or verified against real hardware:

- `.scratch/transport-crypto-layer/mac-discovery/mac-discovery.ino` — throwaway sketch to read each board's WiFi station MAC (needed for `PEER_MAC`, since ESP-NOW addresses by WiFi MAC, not the USB serial ID).
- `.scratch/transport-crypto-layer/esp-now-relay/esp-now-relay.ino` — the real dumb-relay deliverable. Buffers Serial bytes and flushes via `esp_now_send()` either at the ~250-byte ESP-NOW ceiling or after a short (2ms) idle gap — a transport-level batching policy only, no framing/delimiting of the bytes themselves. Written against the installed esp32 Arduino core 3.3.7, which uses the newer `esp_now_recv_cb_t` signature (`const esp_now_recv_info_t *info`), confirmed by reading that core's `esp_now.h` directly rather than assuming.
- `.scratch/transport-crypto-layer/verify-esp-now-relay/run-wizard.sh` (+ `verify_round_trip.py`) — interactive wizard that walks a human through: confirming both serial ports are free, flashing the MAC-discovery sketch to each board (auto-reading the printed MAC back over the port when pyserial is available, falling back to asking), generating the two per-board relay sketches with `PEER_MAC` correctly swapped, flashing those, then running an automated byte-for-byte round-trip check in both directions (A→B and B→A) — falling back to manual `stty`/`cat` instructions if pyserial isn't available.

Status set to `ready-for-human` rather than closing the ticket: the last checklist item — real-hardware verification — requires a human physically present with both boards and Arduino IDE open to run `.scratch/transport-crypto-layer/verify-esp-now-relay/run-wizard.sh`. Re-run that wizard, then flip the last checkbox and `Status` to reflect the outcome once it reports PASS for both directions.

Ran `/code-review` (Standards + Spec axes) before committing. Two real findings came back and were fixed in place, not just noted:

- **Spec**: `onDataRecv()` ignored `esp_now_recv_info_t::src_addr` and relayed any inbound ESP-NOW frame onto Serial regardless of sender — ESP-NOW's receive callback fires for any frame addressed to this device, not just the registered peer, so this silently broke the "unicast only / fixed two-board pair / no broadcast" addressing requirement (spec.md's ESP-NOW addressing decision) for the *inbound* direction even though outbound `esp_now_send()` was already correctly peer-scoped. Fixed: the callback now compares `src_addr` against `PEER_MAC` and drops anything not from the hardcoded peer.
- **Standards**: the wizard's `_write_relay_sketch` used a `sed` regex to inject each board's `PEER_MAC` into a copy of `esp-now-relay.ino`, with no check that the substitution actually matched — if the source sketch's `PEER_MAC` declaration line ever changed shape, the generated per-board firmware would silently keep the all-zero placeholder MAC with no error. Fixed: it now greps the output for the expected post-substitution line and warns loudly (naming both files) if it's not there, instead of asking the human to flash a silently-wrong binary.

One finding was noted but deliberately not changed: `flushOutBuf()` ignores `esp_now_send()`'s return value, so a send failure drops that buffered chunk with no retry — consistent with this stack having no retransmission anywhere (ESP-NOW, this firmware, or USB serial), same category as the burst-drop behavior already documented as accepted in issue 02, not a new regression.

Hardware verification complete (2026-10-27): the wizard (`run-wizard.sh`) reported PASS for both directions (A→B and B→A) on two real ESP32 boards — bytes arrived verbatim over the ESP-NOW link. All acceptance criteria met; status set to done.
