#!/usr/bin/env bash
set -euo pipefail

if [ "$(id -u)" -ne 0 ]; then
    echo "ERROR: run as root"
    exit 1
fi

WIREGUARD_SERVICE="/root/web/src/ui/services/wireguard_service.py"
WG_STATS="/root/web/src/wg_stats.py"
BACKUP_SUFFIX=".bak.wdtt0"
SYNC_SCRIPT="/usr/local/bin/wdtt0-sync-names.py"
CRON_FILE="/etc/cron.d/wdtt0-sync"
WG_CONF="/etc/wireguard/wdtt0.conf"

echo "[Wdtt0] Uninstalling wdtt0 name sync..."

# 1. Remove cron job
if [ -f "$CRON_FILE" ]; then
    rm -f "$CRON_FILE"
    echo "[Wdtt0] Removed $CRON_FILE"
fi

# 2. Remove sync script
if [ -f "$SYNC_SCRIPT" ]; then
    rm -f "$SYNC_SCRIPT"
    echo "[Wdtt0] Removed $SYNC_SCRIPT"
fi

# 3. Restore original StatusOpenVPN files
if [ -f "${WIREGUARD_SERVICE}${BACKUP_SUFFIX}" ]; then
    cp "${WIREGUARD_SERVICE}${BACKUP_SUFFIX}" "$WIREGUARD_SERVICE"
    echo "[Wdtt0] Restored $WIREGUARD_SERVICE"
else
    echo "[Wdtt0] WARNING: backup for $WIREGUARD_SERVICE not found"
fi

if [ -f "${WG_STATS}${BACKUP_SUFFIX}" ]; then
    cp "${WG_STATS}${BACKUP_SUFFIX}" "$WG_STATS"
    echo "[Wdtt0] Restored $WG_STATS"
else
    echo "[Wdtt0] WARNING: backup for $WG_STATS not found"
fi

# 4. Remove generated wdtt0.conf
if [ -f "$WG_CONF" ]; then
    rm -f "$WG_CONF"
    echo "[Wdtt0] Removed $WG_CONF"
fi

# 5. Restart StatusOpenVPN
systemctl restart StatusOpenVPN

echo "[Wdtt0] Done. StatusOpenVPN restarted."
echo "[Wdtt0] wdtt0 names removed from StatusOpenVPN."
