#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

DATASET="${KAGGLE_DATASET:-wcukierski/enron-email-dataset}"
OUT_DIR="${REPO_ROOT}/datasets/raw/enron"

print_error() {
  printf 'Error: %s\n' "$1" >&2
}

print_next_steps() {
  cat <<'EOF'
Kaggle CLI requires authentication.

1. Create a Kaggle account or sign in.
2. Open https://www.kaggle.com/settings
3. In "API", click "Generate New Token"
4. Save the token to:
   - macOS/Linux: ~/.kaggle/access_token
   - Windows: %USERPROFILE%\.kaggle\access_token
5. On macOS/Linux, restrict permissions with:
   chmod 600 ~/.kaggle/access_token

Alternative options:
- set the KAGGLE_API_TOKEN environment variable
  Example: export KAGGLE_API_TOKEN=...
- use the legacy credentials file ~/.kaggle/kaggle.json
EOF
}

extract_zip() {
  local zip_file="$1"
  local destination="$2"

  if command -v unzip >/dev/null 2>&1; then
    unzip -o "$zip_file" -d "$destination"
    return
  fi

  if command -v python3 >/dev/null 2>&1; then
    python3 -m zipfile -e "$zip_file" "$destination"
    return
  fi

  if command -v python >/dev/null 2>&1; then
    python -m zipfile -e "$zip_file" "$destination"
    return
  fi

  print_error "Neither 'unzip' nor 'python3'/'python' is available to extract ${zip_file}."
  exit 1
}

echo "Preparing output directory: ${OUT_DIR}"
mkdir -p "$OUT_DIR"

if ! command -v kaggle >/dev/null 2>&1; then
  print_error "Kaggle CLI is not installed or not available in PATH."
  echo "Install options:"
  echo "  - python3 -m pip install --user kaggle"
  echo "  - pipx install kaggle"
  echo "If the command is still not found, ensure your Python scripts directory is in PATH."
  echo "After installation, ensure the 'kaggle' command is available in your shell."
  exit 1
fi

echo "Downloading dataset '${DATASET}' into ${OUT_DIR}"
if ! kaggle datasets download -d "$DATASET" -p "$OUT_DIR"; then
  print_error "Kaggle download failed."
  echo "Check that your Kaggle authentication is configured."
  print_next_steps
  exit 1
fi

ZIP_FILE="$(find "$OUT_DIR" -maxdepth 1 -type f -name '*.zip' | head -n 1)"
if [ -z "${ZIP_FILE:-}" ]; then
  print_error "No zip file was downloaded into ${OUT_DIR}."
  exit 1
fi

echo "Extracting $(basename "$ZIP_FILE")"
extract_zip "$ZIP_FILE" "$OUT_DIR"

echo "Removing archive $(basename "$ZIP_FILE")"
rm -f "$ZIP_FILE"

if [ -f "$OUT_DIR/emails.csv" ]; then
  echo "Dataset ready at: $OUT_DIR/emails.csv"
else
  echo "Download finished, but emails.csv was not found in $OUT_DIR"
  echo "Please inspect the extracted files manually."
fi
