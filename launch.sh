#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

TARGET="${1:-}"

if [[ "$TARGET" == "app" || "$TARGET" == "gui" ]]; then
    shift
    exec "$SCRIPT_DIR/launch_app.sh" "$@"
elif [[ "$TARGET" == "db" ]]; then
    exec "$SCRIPT_DIR/launch_db.sh"
elif [[ "$TARGET" == "build" ]]; then
    if command -v mvn &>/dev/null; then
        mvn clean package -DskipTests
    elif [[ -x "$HOME/.local/share/JetBrains/Toolbox/apps/intellij-idea/plugins/maven/lib/maven3/bin/mvn" ]]; then
        "$HOME/.local/share/JetBrains/Toolbox/apps/intellij-idea/plugins/maven/lib/maven3/bin/mvn" clean package -DskipTests
    else
        echo "[ERROR] Maven is not installed."
        exit 1
    fi
    exit 0
fi

clear
echo "========================================================"
echo "            Off-Grid Campus Chat Launcher"
echo "========================================================"
echo " 1. Launch Chat App (Swing GUI)"
echo " 2. Launch Database Management CLI (ChatDemo)"
echo " 3. Rebuild chat-app.jar (Maven)"
echo " 4. Exit"
echo "========================================================"
read -rp "Enter choice [1-4]: " CHOICE

case "$CHOICE" in
    1)
        exec "$SCRIPT_DIR/launch_app.sh"
        ;;
    2)
        exec "$SCRIPT_DIR/launch_db.sh"
        ;;
    3)
        if command -v mvn &>/dev/null; then
            mvn clean package -DskipTests
        elif [[ -x "$HOME/.local/share/JetBrains/Toolbox/apps/intellij-idea/plugins/maven/lib/maven3/bin/mvn" ]]; then
            "$HOME/.local/share/JetBrains/Toolbox/apps/intellij-idea/plugins/maven/lib/maven3/bin/mvn" clean package -DskipTests
        else
            echo "[ERROR] Maven is not installed."
            exit 1
        fi
        ;;
    4)
        exit 0
        ;;
    *)
        echo "Invalid choice."
        exit 1
        ;;
esac
