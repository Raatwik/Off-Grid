// echo-sketch
// -----------------------------------------------------------------------
// THROWAWAY test aid for Issue 02 (serial-transport) hardware
// verification only. This is NOT Issue 03's dumb-relay firmware (that one
// also talks ESP-NOW to a peer board; this one doesn't touch ESP-NOW at
// all). Flash this temporarily to get a byte-perfect echo so
// SerialTransport's write()/read() round trip can be verified against
// real hardware, then reflash with the real firmware once Issue 03 is
// built.
//
// Behavior: every byte read from USB Serial is written straight back out
// the same USB Serial connection, unmodified, in order, with no
// buffering beyond what Serial's own FIFO does.

void setup() {
  Serial.begin(115200);
}

void loop() {
  while (Serial.available() > 0) {
    Serial.write(Serial.read());
  }
}
