# Project Workflow: Off-Grid E2EE Campus Chat (Pure Java JVM / Terminal P2P)

## 1. Project Overview & Scope
* **Objective:** Build a 100% off-grid, True End-to-End Encrypted (E2EE) terminal messaging system [cite: 1].
* **Core Architecture:** A pure Java CLI application running on any standard JVM (laptop/desktop) handles all logic (REPL terminal UI, cryptography, packet framing, local database) [cite: 1]. 
* **Transport Mechanism:** Communication will be handled directly via laptop-to-laptop Wi-Fi (Ad-hoc/Sockets) or Bluetooth, completely bypassing any intermediary microcontrollers or ESP32 boards.

---

## 2. Phase 1: Procurement & Setup
* **Software Initialization:**
  * **Build System:** Set up Maven or Gradle for dependency management [cite: 1].
  * **IDE / Tooling:** Standard Java IDE (IntelliJ, Eclipse, VS Code) or pure CLI (`javac`, `java`, Neovim/Vim) [cite: 1].
  * **Repository Structure:** Create a shared Git repository [cite: 1].

---

## 3. Phase 2: System Architecture & Data Flow Mapping
* **Outbound Data Flow:**
  1. User inputs text into the Terminal CLI [cite: 1].
  2. Java encrypts the message payload (AES-256-GCM) using the Standard Java Cryptography Architecture (JCA) [cite: 1].
  3. Java frames and chunks the ciphertext into byte segments [cite: 1].
  4. The application streams these chunks directly to the peer laptop over a Wi-Fi socket or Bluetooth stream.
* **Inbound Data Flow:**
  1. The application receives a data frame from a peer over the local network socket.
  2. Java application parses, verifies frame headers, and reassembles chunks [cite: 1].
  3. Java decrypts the payload, verifies the 128-bit authentication tag, and writes to the local SQLite DB [cite: 1].
  4. Java prints the decrypted message to the active CLI terminal buffer [cite: 1].

---

## 4. Phase 3: Peer-to-Peer Transport Layer Setup
* **Task 3.1: Network Initialization**
  * Establish a direct Wi-Fi Ad-hoc network or pair the laptops via OS-level Bluetooth.
* **Task 3.2: Socket Configuration**
  * Initialize Java standard `java.net.Socket` (TCP) or `java.net.DatagramSocket` (UDP) for Wi-Fi. Alternatively, use a third-party Java Bluetooth library for RFCOMM transport.
  * Configure ring buffers to prevent packet dropping during peak incoming data rates [cite: 1].

---

## 5. Phase 4: Pure JVM Terminal App Development (The Brain)
* **Task 4.1: Terminal UI / REPL Interface**
  * Implement an interactive console UI using `JLine3` or `Lanterna` to handle asynchronous input and scrolling chat logs without terminal output collisions [cite: 1].
* **Task 4.2: Embedded Local Storage**
  * Embed `sqlite-jdbc` for local storage of chat history (Messages, Timestamps, Sender IDs) in an embedded file (e.g., `chat_store.db`) [cite: 1].
* **Task 4.3: Java Cryptography Architecture (JCA)**
  * Implement standard Java `javax.crypto.Cipher` with `AES/GCM/NoPadding` [cite: 1].
  * Implement `javax.crypto.KeyGenerator` for AES-256 key creation and standard Java `KeyStore` (PKCS12 format) for local persistent key management [cite: 1].
* **Task 4.4: Threading & Async Execution**
  * Use `java.util.concurrent.ExecutorService` with daemon threads [cite: 1]:
    * **Thread 1:** Reader thread constantly listening for socket or Bluetooth input [cite: 1].
    * **Thread 2:** Writer thread sending queued packets out [cite: 1].
    * **Thread 3:** UI event loop rendering CLI updates [cite: 1].

---

## 6. Phase 5: Integration & Bench Testing
* **Task 5.1: Cryptographic Tamper Test**
  * Intentionally mutate a byte inside the transmit frame buffer (via code tweak) to confirm that `javax.crypto.AEADBadTagException` is thrown and caught gracefully on the receiving terminal [cite: 1].
* **Task 5.2: Memory & Execution Benchmarks**
  * Verify JVM heap usage remains minimal and that the CLI prompt remains instantly responsive during heavy packet processing [cite: 1].

---

## 7. Phase 6: Field Testing & Deployment
* **Task 7.1: Shadow/Fat JAR Generation**
  * Configure the Maven Shade or Gradle Shadow plugin to package all dependencies (`sqlite-jdbc`, `jline`, etc.) into a single executable `chat-app.jar` [cite: 1].
* **Task 7.2: Field Range Mapping**
  * Run `java -jar chat-app.jar` on two laptops outdoors [cite: 1].
  * Walk away in opposite directions while sending test messages to map exact Wi-Fi or Bluetooth drop-off limits [cite: 1].
* **Task 7.3: Final Presentation / Documentation**
  * Record a video demonstration of the terminal UI [cite: 1].
  * Finalize the repository README with strict CLI build and execution instructions [cite: 1].
