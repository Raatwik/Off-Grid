#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

JAR="target/chat-app.jar"

if ! command -v java &>/dev/null; then
    echo "[ERROR] Java is not installed or not in PATH. Please install Java 17+."
    exit 1
fi

if [[ ! -f "$JAR" ]]; then
    echo "[INFO] $JAR not found. Attempting to build with Maven..."
    if command -v mvn &>/dev/null; then
        mvn package -DskipTests
    elif [[ -x "$HOME/.local/share/JetBrains/Toolbox/apps/intellij-idea/plugins/maven/lib/maven3/bin/mvn" ]]; then
        "$HOME/.local/share/JetBrains/Toolbox/apps/intellij-idea/plugins/maven/lib/maven3/bin/mvn" package -DskipTests
    else
        echo "[ERROR] $JAR not found and 'mvn' is not in PATH. Please build first."
        exit 1
    fi
fi

echo "[INFO] Launching Database CLI (ChatDemo)..."
echo "Commands: /send <text>, /recv <text>, /log [n], /count, /clear, /quit"
echo "------------------------------------------------------------"
exec java -cp "$JAR" chat.ChatDemo
