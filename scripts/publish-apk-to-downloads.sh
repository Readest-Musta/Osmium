#!/usr/bin/env bash
# Pulls the most recent Codespace debug APK into the device's shared Downloads
# folder so it shows up in the Android Downloads app / file manager.
#
# Usage:
#   ./scripts/publish-apk-to-downloads.sh [version-label]
#
# Defaults: label = current git short hash, codespace = organic-train-6vw9xr4vqg75257pp.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "${REPO_ROOT}"

CODESPACE="${OSMIUM_CODESPACE:-organic-train-6vw9xr4vqg75257pp}"
LABEL="${1:-$(git rev-parse --short HEAD 2>/dev/null || echo latest)}"
DEST_DIR="${OSMIUM_DOWNLOAD_DIR:-/sdcard/Download}"
DEST="${DEST_DIR}/Osmium-${LABEL}.apk"
REMOTE_TMP="/home/codespace/osmium-publish-${LABEL}.apk"
REMOTE_APK="/workspaces/Osmium/app/build/outputs/apk/debug/app-debug.apk"

echo "==> Fetching APK from Codespace ${CODESPACE}"
# gh codespace cp is unreliable for absolute remote paths here; stream via base64.
gh codespace ssh -c "${CODESPACE}" -- \
  "cp '${REMOTE_APK}' '${REMOTE_TMP}' && base64 -w0 '${REMOTE_TMP}' && rm -f '${REMOTE_TMP}'" \
  2>/dev/null | base64 -d > "${DEST}"

chmod 660 "${DEST}"
chown root:aid_everybody "${DEST}" 2>/dev/null || true

echo "==> Published: ${DEST}"
ls -lh "${DEST}"
sha256sum "${DEST}"
