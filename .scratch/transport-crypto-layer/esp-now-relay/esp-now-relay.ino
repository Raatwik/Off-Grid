// esp-now-relay
// -----------------------------------------------------------------------
// Issue 03 deliverable: a dumb, symmetric byte relay between USB Serial
// and ESP-NOW. No chat/crypto/chunk awareness lives here at all — that's
// all handled upstream in Java (ChatEngine, issue 01). This firmware just
// moves bytes between the two transports in both directions:
//
//   Serial -> ESP-NOW : bytes read from USB Serial are relayed verbatim
//                        via esp_now_send() to the hardcoded peer MAC.
//   ESP-NOW -> Serial : bytes arriving via the ESP-NOW receive callback
//                        are written verbatim out USB Serial.
//
// PEER_MAC below must be set to the *other* board's WiFi station MAC
// (use mac-discovery.ino to read it off each board), then this exact
// sketch is flashed to both boards with only that array differing.
//
// Serial->ESP-NOW framing note: raw USB serial has no packet boundaries,
// but ESP-NOW is itself packet-oriented, so bytes are buffered until
// either the ~250-byte ESP-NOW payload ceiling is reached or no further
// byte has arrived for FLUSH_IDLE_MS, then sent as one esp_now_send()
// call. This is a transport-level batching policy only — it does not
// interpret, delimit, or re-chunk the bytes themselves.

#include <WiFi.h>
#include <esp_now.h>
#include <string.h>

// TODO: set to the OTHER board's MAC address, e.g. {0xAC, 0xA7, 0x04, 0xED, 0xE6, 0x80}
static uint8_t PEER_MAC[6] = {0x00, 0x00, 0x00, 0x00, 0x00, 0x00};

static const size_t ESP_NOW_PAYLOAD_MAX = 250;
static const unsigned long FLUSH_IDLE_MS = 2;

static uint8_t outBuf[ESP_NOW_PAYLOAD_MAX];
static size_t outBufLen = 0;
static unsigned long lastByteMs = 0;

void onDataRecv(const esp_now_recv_info_t *info, const uint8_t *data, int len) {
  // ESP-NOW's receive callback fires for any frame addressed to this
  // device regardless of the registered peer list (the peer list only
  // governs esp_now_send()'s outbound targeting) -- filter by source MAC
  // here so a stray third board on the same channel can't get relayed
  // onto Serial, keeping this a fixed two-board pair per the spec.
  if (memcmp(info->src_addr, PEER_MAC, 6) != 0) {
    return;
  }
  Serial.write(data, len);
}

void flushOutBuf() {
  if (outBufLen == 0) {
    return;
  }
  esp_now_send(PEER_MAC, outBuf, outBufLen);
  outBufLen = 0;
}

void setup() {
  Serial.begin(115200);

  WiFi.mode(WIFI_STA);
  WiFi.disconnect();

  if (esp_now_init() != ESP_OK) {
    return;
  }
  esp_now_register_recv_cb(onDataRecv);

  esp_now_peer_info_t peer = {};
  memcpy(peer.peer_addr, PEER_MAC, 6);
  peer.channel = 0;
  peer.encrypt = false;
  esp_now_add_peer(&peer);
}

void loop() {
  while (Serial.available() > 0 && outBufLen < ESP_NOW_PAYLOAD_MAX) {
    outBuf[outBufLen++] = (uint8_t)Serial.read();
    lastByteMs = millis();
  }

  if (outBufLen == ESP_NOW_PAYLOAD_MAX) {
    flushOutBuf();
  } else if (outBufLen > 0 && (millis() - lastByteMs) >= FLUSH_IDLE_MS) {
    flushOutBuf();
  }
}
