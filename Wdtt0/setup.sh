#!/usr/bin/env bash
# Wdtt0 setup script for StatusOpenVPN
#
# Usage (as root):
#   Install:
#     bash -c "$(curl -sL https://raw.githubusercontent.com/jewbsv/proxy-turn-vk-android/master/Wdtt0/setup.sh)"
#
#   Uninstall:
#     bash -c "$(curl -sL https://raw.githubusercontent.com/jewbsv/proxy-turn-vk-android/master/Wdtt0/setup.sh)" -- --uninstall
#

set -euo pipefail

# Требуем root, потому что патчим /root/web и /etc/wireguard
if [ "$(id -u)" -ne 0 ]; then
    echo "ERROR: run as root"
    exit 1
fi

# Ветка/репозиторий, откуда забираем скрипты
REPO_RAW="https://raw.githubusercontent.com/jewbsv/proxy-turn-vk-android/master/Wdtt0"

INSTALL_URL="$REPO_RAW/install_Wdtt0.sh"
UNINSTALL_URL="$REPO_RAW/uninstall_Wdtt0.sh"

INSTALL_SCRIPT="/root/install_Wdtt0.sh"
UNINSTALL_SCRIPT="/root/uninstall_Wdtt0.sh"

echo "[Wdtt0] Downloading scripts from $REPO_RAW ..."

# -f — fail на 404, -s — тихий режим, -S — показать ошибку, -L — следовать редиректам
curl -fsSL "$INSTALL_URL" -o "$INSTALL_SCRIPT"
curl -fsSL "$UNINSTALL_URL" -o "$UNINSTALL_SCRIPT"

chmod +x "$INSTALL_SCRIPT" "$UNINSTALL_SCRIPT"

if [ "${1:-}" = "--uninstall" ]; then
    echo "[Wdtt0] Running uninstall..."
    "$UNINSTALL_SCRIPT"
else
    echo "[Wdtt0] Running install..."
    "$INSTALL_SCRIPT"
fi
