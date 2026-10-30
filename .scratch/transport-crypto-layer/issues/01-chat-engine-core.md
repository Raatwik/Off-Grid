# 01 — Core ChatEngine: encrypt, chunk, reassemble, timeout

**What to build:** The crypto/framing brain of the system, fully verifiable with no hardware at all: a plaintext message handed to one `ChatEngine` instance produces the identical plaintext out of another `ChatEngine` instance, with the two wired together only through an in-memory `ByteTransport` test seam (no real serial port, no ESP32).

**Blocked by:** None — can start immediately

**Status:** ready-for-agent

- [ ] Define the `ByteTransport`-style interface (write bytes out / blocking read bytes in) that `ChatEngine` sits behind — this is the seam production (`SerialTransport`) and tests (in-memory pipe) both implement.
- [ ] Implement per-chunk `AES/GCM/NoPadding` encryption with a single hardcoded, shared AES-256 key, using a fresh random 12-byte `SecureRandom` nonce for every chunk.
- [ ] Implement the documented wire frame per chunk: 4-byte random `msgId` (via `SecureRandom`, not a counter), 1-byte `chunkIndex`, 1-byte `totalChunks`, 12-byte nonce, ciphertext (including the 16-byte GCM tag) — total frame at or under ~234 bytes.
- [ ] Implement chunk-then-encrypt splitting of outgoing plaintext (split first, encrypt each chunk independently) and chunk reassembly on the receiving side back into the original plaintext, in order, regardless of chunk count.
- [ ] Implement per-`msgId` reassembly buffering with a timestamp, and a timeout sweep (e.g. 5s) that discards a buffer once a chunk is permanently missing, surfacing a "message failed / incomplete" result from `ChatEngine` rather than hanging or silently dropping it.
- [ ] Handle a corrupted chunk (bad ciphertext/tag) as a graceful decryption failure — no crash, no silently-wrong plaintext — consistent with `AEADBadTagException` handling named in the project's roadmap docs.
- [ ] Handle two messages with colliding `msgId`s (forced in a test, since real IDs are random) without cross-contaminating each other's chunks; document the chosen reject/overwrite behavior in the test itself.
- [ ] Automated tests covering: short single-chunk round-trip, multi-chunk round-trip in order, dropped-chunk timeout → failure surfaced, corrupted-chunk → graceful failure, colliding-`msgId` handling — all run against the in-memory `ByteTransport`, no hardware required.
