#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET_DIR="${ROOT_DIR}/src/main/resources"

if [[ ! -d "${TARGET_DIR}" ]]; then
  echo "ERROR: target dir not found: ${TARGET_DIR}" >&2
  exit 1
fi

ensure_oxipng() {
  if command -v oxipng >/dev/null 2>&1; then
    return 0
  fi

  if [[ -x "${HOME}/.cargo/bin/oxipng" ]]; then
    export PATH="${HOME}/.cargo/bin:${PATH}"
    return 0
  fi

  if ! command -v cargo >/dev/null 2>&1; then
    echo "ERROR: oxipng not found, and cargo not available to install it." >&2
    echo "Hint (Arch): pacman -S oxipng  (requires sudo)" >&2
    exit 2
  fi

  echo "oxipng not found; installing via cargo (user-local)..." >&2
  cargo install oxipng --locked
  export PATH="${HOME}/.cargo/bin:${PATH}"
}

human_bytes() {
  python3 - <<'PY' "$1"
import sys
n=int(sys.argv[1])
for unit in ['B','KiB','MiB','GiB','TiB']:
    if n < 1024 or unit == 'TiB':
        print(f"{n:.2f} {unit}" if unit != 'B' else f"{int(n)} {unit}")
        break
    n/=1024
PY
}

sum_png_bytes() {
  find "${TARGET_DIR}" -type f -iname '*.png' -printf '%s\n' | awk '{s+=$1} END{print s+0}'
}

ensure_oxipng

before="$(sum_png_bytes)"
echo "Before: ${before} bytes ($(human_bytes "${before}"))"

# -o max: maximum lossless compression effort
# --strip all: remove all ancillary chunks; keep only the essential pixel data representation
# -r: recurse into directories
oxipng -o max --strip all -r "${TARGET_DIR}"

after="$(sum_png_bytes)"
echo "After:  ${after} bytes ($(human_bytes "${after}"))"

echo "Saved:  $((before-after)) bytes ($(human_bytes "$((before-after))"))"