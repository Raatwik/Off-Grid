// mac-discovery
// -----------------------------------------------------------------------
// THROWAWAY sketch for Issue 03 (esp32-firmware) setup only. Not the
// dumb-relay deliverable. ESP-NOW addresses peers by their station-mode
// WiFi MAC, which is not the same as any USB serial identifier, so each
// board's MAC has to be read off the board itself before the real
// firmware (esp-now-relay.ino) can be written with the peer's MAC
// hardcoded in.
//
// Behavior: prints this board's WiFi station MAC address once at boot,
// then repeats it every 2 seconds so it's easy to catch over a serial
// monitor opened after the board already booted.

#include <WiFi.h>

void setup() {
  Serial.begin(115200);
  delay(1000);
  WiFi.mode(WIFI_STA);
}

void loop() {
  Serial.print("MAC: ");
  Serial.println(WiFi.macAddress());
  delay(2000);
}
