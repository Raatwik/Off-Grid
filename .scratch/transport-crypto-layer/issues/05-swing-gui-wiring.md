# 05 — Swing GUI wiring (ChatFrontend)

**What to build:** The user-facing feature: typing a message in the existing `ChatFrontend` chat window and pressing send actually encrypts and transmits it over the real ESP32/ESP-NOW link; incoming decrypted messages appear live in the chat window; a visible "message failed / incomplete" marker appears for a message that never fully arrives; history persists via the existing `ChatDatabase` schema (`ciphertext_b64`, `nonce_b64`, `plaintext_cache`). `ChatDemo` (CLI) is explicitly left untouched.

**Blocked by:** 04

**Status:** done

- [x] `ChatFrontend`'s send action enqueues the typed message onto the real `ChatEngine`/Writer-thread pipeline (01+02) instead of writing a Base64 placeholder directly to the DB.
- [x] An incoming, fully-reassembled, decrypted message is published to the Swing UI via `SwingUtilities.invokeLater` and appears in the chat log automatically, with no manual refresh.
- [x] A message that times out as incomplete (per ticket 01's timeout/failure behavior) shows a visible "message failed / incomplete" marker in the chat log rather than silently vanishing.
- [x] Both outgoing and incoming messages are persisted into the existing `ChatDatabase` schema as-is — no schema changes in this ticket.
- [x] The Reader/Writer threads (01+02) and the Swing UI thread run independently — a slow or blocked serial read never freezes the chat window, and outgoing sends never block on incoming reads.
- [x] `ChatDemo` (the CLI) is not modified and continues to function as a DB-testing REPL exactly as before.
- [ ] Manual verification: run the updated `ChatFrontend` on two laptops with real ESP32 hardware attached, send messages in both directions, and confirm they appear correctly, including at least one deliberately-interrupted send that surfaces the failure marker.

## Comments

Implemented in `ac8d2ab` (`ChatSession`, `ChatFrontend`, `ChatEngine.seal`; `ChatSessionTest`). Marked done at the user's request. The last checklist item (two-laptop manual run with a deliberately interrupted send) is left unchecked: it was not verified on two laptops, only launched on a single machine.
