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
fi

clear
echo "========================================================"
echo "            Off-Grid Campus Chat Launcher"
echo "========================================================"
echo " 1. Launch Chat App (Swing GUI)"
echo " 2. Launch Database Management CLI (ChatDemo)"
echo " 3. Exit"
echo "========================================================"
read -rp "Enter choice [1-3]: " CHOICE

case "$CHOICE" in
    1)
        exec "$SCRIPT_DIR/launch_app.sh"
        ;;
    2)
        exec "$SCRIPT_DIR/launch_db.sh"
        ;;
    3)
        exit 0
        ;;
    *)
        echo "Invalid choice."
        exit 1
        ;;
esac
