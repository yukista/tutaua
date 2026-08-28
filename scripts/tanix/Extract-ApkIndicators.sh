#!/usr/bin/env bash
set -euo pipefail

audit_dir="${1:?Usage: Extract-ApkIndicators.sh AUDIT_DIRECTORY}"
shopt -s nullglob
apks=("$audit_dir"/*.apk)
if ((${#apks[@]} == 0)); then
  echo "No APK files found in $audit_dir" >&2
  exit 1
fi

for apk in "${apks[@]}"; do
  echo "=== $(basename "$apk") URLS ==="
  unzip -p "$apk" 2>/dev/null |
    strings -a |
    grep -Eio 'https?://[^[:space:]"<>]+' |
    sed 's/[),;]$//' |
    sort -u |
    head -80 || true
done
