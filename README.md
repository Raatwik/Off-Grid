# 📡 OFF-GRID — E2EE Campus Chat

> **No Internet. No Servers. No Compromises.**

A peer-to-peer, end-to-end encrypted messaging system built in pure Java that works **completely off-grid** — via Wi-Fi Direct sockets, Bluetooth, or ESP-NOW radio.

🔗 **For the full, rich documentation open [`README.html`](README.html) in your browser.**

---

## Quick Start

```bash
# Compile
javac -cp sqlite-jdbc-3.46.1.3.jar -d build ChatDatabase.java ChatFrontend.java ChatDemo.java

# Run GUI (Windows)
java -cp "build;sqlite-jdbc-3.46.1.3.jar" chat.ChatFrontend

# Run CLI (Windows)
java -cp "build;sqlite-jdbc-3.46.1.3.jar" chat.ChatDemo
```

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 17+ |
| GUI | Java Swing |
| Database | SQLite (sqlite-jdbc) |
| Encryption | AES-256-GCM (JCA) |
| Build | Maven + Shade Plugin |
| Transport | Wi-Fi Sockets / BLE / ESP-NOW |

## Project Structure

```
├── ChatFrontend.java     # Swing GUI frontend
├── ChatDatabase.java     # SQLite JDBC storage layer
├── ChatDemo.java         # CLI REPL for testing
├── pom.xml               # Maven build config
├── sqlite-jdbc-*.jar     # Bundled JDBC driver
├── README.html           # Full HTML documentation
└── docs/
    ├── project(1).md             # Detailed project workflow
    ├── laptop_to_laptop.md       # P2P setup guide
    └── Off-Grid E2EE Campus Chat.pptx  # Presentation
```

---

Built with ☕ Java & 🔐 Cryptography
