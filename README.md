# Off-Grid E2EE Campus Chat

An infrastructure-less, decentralized, True End-to-End Encrypted (E2EE) messaging system built with **Java (Swing / JVM)** and **ESP32 (ESP-NOW)** microcontrollers.

Designed for emergency scenarios, campus connectivity during network outages, and high-privacy local communication without internet, cell towers, or Wi-Fi routers.

---

## Architecture Overview:

```text
[ Laptop A (Java/Swing) ] ──(USB Serial)──> [ ESP32 A ]
                                               │
                                       (ESP-NOW 2.4GHz)
                                               │
[ Laptop B (Java/Swing) ] <──(USB Serial)── [ ESP32 B ]
```

* **The Java Application (The Brain)**: Handles AES-256-GCM encryption, dynamic chunking, reassembly buffers, local SQLite storage, and the Swing user interface.
* **The ESP32 Boards (Dumb Modems)**: Operate purely as physical wireless relays between USB Serial and ESP-NOW packets with zero awareness of cryptography or chat payloads.

---

## Quick Start: How to Run

### Prerequisites
* **Java Development Kit (JDK) 17+**
* **2x ESP32 Microcontrollers** flashed with the `esp-now-relay` firmware
* **2x Laptops** (Windows or Linux)

### On Windows
1. Connect your ESP32 via USB and note the COM port in **Device Manager** (e.g. `COM3`).
2. Run the interactive launcher:
   ```cmd
   launch.bat
   ```
   Or launch directly:
   ```cmd
   launch_app.bat COM3 115200
   ```
3. To inspect local SQLite database history:
   ```cmd
   launch_db.bat
   ```

### On Linux
1. Connect your ESP32 via USB (ensure user is in `dialout` group: `sudo usermod -a -G dialout $USER`).
2. Run the interactive launcher:
   ```bash
   ./launch.sh
   ```
   Or launch directly:
   ```bash
   ./launch_app.sh /dev/ttyACM0 115200
   ```
3. To inspect local SQLite database history:
   ```bash
   ./launch_db.sh
   ```

---

## What Has Been Completed

- [x] **Phase 1: Local Storage & Data Schema**: SQLite integration via `sqlite-jdbc` in WAL mode (`ChatDatabase`).
- [x] **Phase 2: Cryptographic Engine**: AES-256-GCM authenticated encryption with per-chunk random 12-byte nonces and 128-bit authentication tags (`ChatEngine`).
- [x] **Phase 3: Serial Transport Driver**: Multithreaded USB serial driver using `jSerialComm` with non-blocking daemon queues and length-prefixed packet framing (`SerialTransport`).
- [x] **Phase 4: Session Coordination & GUI**: Asynchronous glue layer (`ChatSession`) connecting Swing GUI (`ChatFrontend`) to the crypto/serial pipeline.
- [x] **Phase 5 (Part 1): Standalone Packaging & Scripts**: Precompiled fat JAR (`chat-app.jar`) and native Windows/Linux launch scripts.
- [x] **Phase 5 (Part 2): Academic OOP Documentation**: Complete AOOP architectural analysis and viva examination guide (`DOCUMENTATION.md`).

---

## What Is To Be Done (Project Roadmap & Next Steps)

The following tasks remain to be completed for final validation, field benchmarking, and future project expansion:

### 1. Hardware Integration & Verification (Immediate Next Steps)
- [ ] **Dual-Laptop Live Verification**: Run the application concurrently across two separate physical laptops with tethered ESP32 boards; verify two-way message transmission and automatic log display without manual refresh.
- [ ] **Drop-Off & Incomplete Frame Test**: Deliberately disconnect an ESP32 or walk out of range mid-message to verify that the Reader thread timeout triggers and renders the visual `[message failed / incomplete]` marker in the chat log.
- [ ] **Cryptographic Tamper Test**: Artificially corrupt a single ciphertext byte in the transmission buffer to confirm that `javax.crypto.AEADBadTagException` is thrown and handled safely on the receiver.

### 2. Field Testing & Benchmarking (Phase 6)
- [ ] **Outdoor Range Mapping**: Test and record maximum transmission distance in an open campus field (expected 100–200 meters Line-of-Sight).
- [ ] **Obstacle Penetration Benchmarking**: Measure packet delivery success through solid reinforced concrete walls, floors, and metal barriers.
- [ ] **Throughput & Latency Profiling**: Benchmark round-trip transmission latency for messages varying from short single-chunk texts (under 200 bytes) to multi-chunk paragraphs.

### 3. Final Deliverables & Review Preparation
- [ ] **Demonstration Video**: Record a video demonstrating:
  1. Launching via scripts on Windows and Linux.
  2. Live messaging between the two machines.
  3. Failure handling when an antenna/board is disconnected.
  4. Local database inspection via `launch_db`.
- [ ] **Final Review Presentation**: Align final presentation slides with actual hardware test results and metrics.

### 4. Future Enhancements (Beyond Current Scope)
- [ ] **Dynamic Key Exchange (ECDH / X25519)**: Replace the hardcoded shared key with a runtime Diffie-Hellman handshake for forward secrecy.
- [ ] **Multi-Node Mesh Routing**: Expand the point-to-point fixed MAC relay into a multi-hop flood/mesh network.
- [ ] **File / Attachment Transfer**: Add capability to chunk, encrypt, and reconstruct small binary files (voice notes, images).
- [ ] **Android Companion App**: Implement a Java Android client connecting to the ESP32 bridge over Bluetooth Low Energy (BLE).

---

## Documentation & Code Architecture

For an in-depth explanation of OOP design patterns, thread models, access specifiers, and viva questions, refer to:
* **[DOCUMENTATION.md](DOCUMENTATION.md)**: Exhaustive technical and academic guide to the codebase.
