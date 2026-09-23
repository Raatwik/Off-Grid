# Project Workflow: Off-Grid E2EE Campus Chat (Pure Java JVM / Terminal + ESP32)

## 1. Project Overview & Scope
* **Objective:** Build a 100% off-grid, True End-to-End Encrypted (E2EE) terminal messaging system.
* **Core Architecture:** A pure Java CLI application running on any standard JVM (laptop/desktop/Raspberry Pi) handles all logic (REPL terminal UI, cryptography, packet framing, local database). The ESP32 handles physical transport (Serial/BLE from the host machine, ESP-NOW to the remote ESP32 peer).
* **Range Constraints:** 100–200 meters (Line of Sight) via ESP-NOW.

---

## 2. Phase 1: Procurement & Setup
**Goal:** Initialize terminal dev toolchains and hardware.

* **Hardware Procurement:**
  * 2x ESP32 Development Boards.
  * 2x Micro-USB / USB-C Cables (for Host-to-ESP32 interface) or Portable Power Banks.
  * *(Optional)* External 3dBi antennas for better range.
* **Software Initialization:**
  * **Build System:** Set up Maven or Gradle for dependency management.
  * **IDE / Tooling:** Standard Java IDE (IntelliJ, Eclipse, VS Code) or pure CLI (`javac`, `java`, Neovim/Vim).
  * **ESP32 Tooling:** PlatformIO or Arduino IDE.
  * **Repository Structure:** Create a shared Git repository with two main folders: `/jvm_terminal_app` and `/esp32_firmware`.

---

## 3. Phase 2: System Architecture & Data Flow Mapping
**Goal:** Define byte-level data flow between the host terminal application and ESP32 nodes.

* **Outbound Data Flow:**
  1. User inputs text into the Terminal CLI.
  2. Java encrypts the message payload (AES-256-GCM) using the Standard Java Cryptography Architecture (JCA).
  3. Java frames and chunks the ciphertext into byte segments (e.g., < 247 bytes).
  4. Java streams chunks to the ESP32 over Serial/UART (via `jSerialComm`) or OS-level BLE.
  5. ESP32 receives raw bytes and immediately broadcasts them via ESP-NOW.
* **Inbound Data Flow:**
  1. ESP32 receives an ESP-NOW frame from a peer.
  2. ESP32 streams the frame to the host machine over Serial/BLE Notification.
  3. Java application parses, verifies frame headers, and reassembles chunks.
  4. Java decrypts the payload, verifies the 128-bit authentication tag, and writes to the local SQLite DB.
  5. Java prints the decrypted message to the active CLI terminal buffer.

---

## 4. Phase 3: ESP32 Firmware Development (The Bridge)
**Goal:** Build a low-latency physical bridge with zero cryptography responsibility.

* **Task 3.1: MAC Address Hardcoding**
  * Write a utility script to extract the Wi-Fi MAC address of both ESP32 units.
  * Hardcode Peer B’s MAC address into Node A, and Peer A’s into Node B.
* **Task 3.2: Host Transport Setup**
  * Initialize Hardware Serial (UART over USB) or BLE Server with a custom Service UUID.
  * Configure ring buffers to prevent packet dropping during peak incoming data rates.
* **Task 3.3: ESP-NOW Protocol Setup**
  * Initialize Wi-Fi in Station mode (unconnected to access points).
  * Initialize the ESP-NOW protocol and register the targeted peer MAC address and callbacks.
* **Task 3.4: Bidirectional Bridge Routing**
  * Map incoming Host Serial/BLE data directly to `esp_now_send()`.
  * Map incoming ESP-NOW packets directly to `Serial.write()` or BLE TX notifications.

---

## 5. Phase 4: Pure JVM Terminal App Development (The Brain)
**Goal:** Build an asynchronous, secure, CLI-driven Java app using standard JVM packages.

* **Task 4.1: Terminal UI / REPL Interface**
  * Implement an interactive console UI using `JLine3` or `Lanterna` to handle asynchronous input and scrolling chat logs without terminal output collisions.
* **Task 4.2: Embedded Local Storage**
  * Embed `sqlite-jdbc` for local storage of chat history (Messages, Timestamps, Sender IDs) in an embedded file (e.g., `chat_store.db`).
* **Task 4.3: Java Cryptography Architecture (JCA)**
  * Implement standard Java `javax.crypto.Cipher` with `AES/GCM/NoPadding`.
  * Implement `javax.crypto.KeyGenerator` for AES-256 key creation and standard Java `KeyStore` (PKCS12 format) for local persistent key management.
* **Task 4.4: Host Transport Layer**
  * Integrate `jSerialComm` for rock-solid USB-Serial communication with the ESP32 (or use OS-level BLE library wrappers).
  * Build packet sequence headers and framing logic (e.g., `[START_BYTE][CHUNK_INDEX][TOTAL_CHUNKS][PAYLOAD][END_BYTE]`).
* **Task 4.5: Threading & Async Execution**
  * Use `java.util.concurrent.ExecutorService` with daemon threads:
    * **Thread 1:** Reader thread constantly listening for Serial/BLE input.
    * **Thread 2:** Writer thread sending queued packets out.
    * **Thread 3:** UI event loop rendering CLI updates.

---

## 6. Phase 5: Integration & Bench Testing
**Goal:** Verify hardware transport reliability and crypto validation in a terminal environment.

* **Task 6.1: CLI Loopback & Echo Verification**
  * Test high-speed string generation via the Terminal to ensure no serial buffer overruns occur on the ESP32.
* **Task 6.2: Cryptographic Tamper Test**
  * Intentionally mutate a byte inside the transmit frame buffer (via code tweak) to confirm that `javax.crypto.AEADBadTagException` is thrown and caught gracefully on the receiving terminal.
* **Task 6.3: Memory & Execution Benchmarks**
  * Verify JVM heap usage remains minimal and that the CLI prompt remains instantly responsive during heavy packet processing.

---

## 7. Phase 6: Field Testing & Deployment
**Goal:** Build a runnable JAR distribution and perform outdoor physical tests.

* **Task 7.1: Shadow/Fat JAR Generation**
  * Configure the Maven Shade or Gradle Shadow plugin to package all dependencies (`sqlite-jdbc`, `jSerialComm`, `jline`) into a single executable `chat-app.jar`.
* **Task 7.2: Field Range Mapping**
  * Run `java -jar chat-app.jar` on two laptops connected to ESP32s outdoors. 
  * Walk away in opposite directions while sending test messages to map exact ESP-NOW line-of-sight drop-off limits.
* **Task 7.3: Wi-Fi Channel Tuning**
  * Set the ESP32 Wi-Fi channel to the least congested physical frequency (e.g., Channel 13) to avoid campus Wi-Fi saturation causing interference.
* **Task 7.4: Final Presentation / Documentation**
  * Record a video demonstration of the terminal UI and physical hardware.
  * Finalize the repository README with strict CLI build and execution instructions.