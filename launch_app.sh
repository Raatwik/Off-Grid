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

PORT="${1:-}"
BAUD="${2:-115200}"

if [[ -z "$PORT" ]]; then
    DEVICES=($(ls /dev/ttyACM* /dev/ttyUSB* 2>/dev/null || true))
    if [[ ${#DEVICES[@]} -eq 1 ]]; then
        PORT="${DEVICES[0]}"
        echo "[INFO] Auto-detected serial port: $PORT"
    elif [[ ${#DEVICES[@]} -gt 1 ]]; then
        echo "Multiple serial ports found:"
        for i in "${!DEVICES[@]}"; do
            echo "  $((i+1))) ${DEVICES[$i]}"
        done
        read -rp "Select port number or enter device path [default: ${DEVICES[0]}]: " CHOICE
        if [[ "$CHOICE" =~ ^[0-9]+$ ]] && (( CHOICE >= 1 && CHOICE <= ${#DEVICES[@]} )); then
            PORT="${DEVICES[$((CHOICE-1))]}"
        elif [[ -n "$CHOICE" ]]; then
            PORT="$CHOICE"
        else
            PORT="${DEVICES[0]}"
        fi
    else
        read -rp "Enter serial port [default: /dev/ttyACM0]: " PORT
        PORT="${PORT:-/dev/ttyACM0}"
    fi
fi

echo "[INFO] Launching Off-Grid Campus Chat GUI on $PORT @ $BAUD baud..."
exec java -cp "$JAR" chat.ChatFrontend "$PORT" "$BAUD"
