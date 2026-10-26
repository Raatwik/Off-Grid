#!/usr/bin/env python3
"""Issue 03 hardware verification: round-trip raw bytes over the real
ESP-NOW wireless link between two flashed boards.

Usage: verify_round_trip.py <port_a> <port_b> <baud>

Writes a known byte string into port_a, expects it to arrive verbatim on
port_b via the boards' ESP-NOW relay firmware, then does the same in the
reverse direction (port_b -> port_a). Exits 0 only if both directions
round-trip exactly; exits 1 otherwise, printing what went wrong.

This is intentionally a thin, standalone script (not project test code) --
it exercises real hardware and has no place in the JVM test suite, per the
project's Testing Decisions (hardware/firmware correctness is manual
field verification, not something CI can cover).
"""
import sys
import time

try:
    import serial
except ImportError:
    print("pyserial not installed", file=sys.stderr)
    sys.exit(1)


def round_trip(write_port, read_port, baud, payload, label):
    with serial.Serial(write_port, baud, timeout=2) as w, \
         serial.Serial(read_port, baud, timeout=2) as r:
        r.reset_input_buffer()
        w.write(payload)
        w.flush()

        deadline = time.time() + 5
        received = b""
        while time.time() < deadline and len(received) < len(payload):
            chunk = r.read(len(payload) - len(received))
            received += chunk

    if received == payload:
        print(f"  PASS [{label}]: {payload!r} arrived unmodified")
        return True
    print(f"  FAIL [{label}]: sent {payload!r}, received {received!r}", file=sys.stderr)
    return False


def main():
    if len(sys.argv) != 4:
        print(f"usage: {sys.argv[0]} <port_a> <port_b> <baud>", file=sys.stderr)
        sys.exit(2)

    port_a, port_b, baud = sys.argv[1], sys.argv[2], int(sys.argv[3])

    ok_a_to_b = round_trip(port_a, port_b, baud, b"ping-a-to-b-0123456789", "A -> B")
    time.sleep(0.5)
    ok_b_to_a = round_trip(port_b, port_a, baud, b"ping-b-to-a-9876543210", "B -> A")

    sys.exit(0 if (ok_a_to_b and ok_b_to_a) else 1)


if __name__ == "__main__":
    main()
