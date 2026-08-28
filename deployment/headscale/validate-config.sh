#!/usr/bin/env bash
set -euo pipefail

version="0.29.3"
config_path="${1:-/mnt/e/projectes/client_android_jellyfin/deployment/headscale/config/config.yaml}"
temp_dir="$(mktemp -d)"
trap 'rm -rf -- "$temp_dir"' EXIT
test -f "$config_path"

case "$(uname -m)" in
  x86_64) asset="headscale_${version}_linux_amd64" ;;
  aarch64) asset="headscale_${version}_linux_arm64" ;;
  *) echo "Arquitectura no compatible: $(uname -m)" >&2; exit 2 ;;
esac

base_url="https://github.com/juanfont/headscale/releases/download/v${version}"
curl -fsSL --retry 3 -o "$temp_dir/headscale" "$base_url/$asset"
curl -fsSL --retry 3 -o "$temp_dir/checksums.txt" "$base_url/checksums.txt"

expected="$(awk -v file="$asset" '$2 == file {print $1}' "$temp_dir/checksums.txt")"
actual="$(sha256sum "$temp_dir/headscale" | awk '{print $1}')"
test -n "$expected"
test "$actual" = "$expected"

chmod +x "$temp_dir/headscale"
"$temp_dir/headscale" version

# configtest initialises key/database paths. Redirect those writes to the
# disposable directory while preserving every schema option from the real file.
escaped_temp="${temp_dir//\//\\/}"
policy_path="$(dirname "$config_path")/policy.hujson"
escaped_policy="${policy_path//\//\\/}"
sed \
  -e "s/\/var\/lib\/headscale/$escaped_temp/g" \
  -e "s/\/var\/run\/headscale\/headscale.sock/$escaped_temp\\/headscale.sock/g" \
  -e "s/\/etc\/headscale\/policy.hujson/$escaped_policy/g" \
  "$config_path" > "$temp_dir/config.yaml"

"$temp_dir/headscale" configtest -c "$temp_dir/config.yaml"
