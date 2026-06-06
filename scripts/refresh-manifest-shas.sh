#!/usr/bin/env bash
# Compute the real SHA-256 and Content-Length for each curated GGUF in
# registry/src/commonMain/kotlin/dev/koml/registry/CuratedModels.kt, then
# print a paste-ready Kotlin diff.
#
# Usage: ./scripts/refresh-manifest-shas.sh
#
# Network-bound; takes ~10–30 min depending on bandwidth (~5 GB of GGUFs).
# Files are downloaded to a temp dir and deleted afterward.
#
# macOS ships bash 3.2 which doesn't support associative arrays, so this
# script uses a tab-separated heredoc instead of `declare -A`.
set -euo pipefail

TMP_DIR="$(mktemp -d -t koml-manifest-XXXXXX)"
trap 'rm -rf "$TMP_DIR"' EXIT

# id<TAB>url, one per line. Edit this block when adding/removing models.
MODELS=$(cat <<'EOF'
smollm2-135m-instruct-q8	https://huggingface.co/bartowski/SmolLM2-135M-Instruct-GGUF/resolve/main/SmolLM2-135M-Instruct-Q8_0.gguf
smollm2-1.7b-instruct-q4km	https://huggingface.co/bartowski/SmolLM2-1.7B-Instruct-GGUF/resolve/main/SmolLM2-1.7B-Instruct-Q4_K_M.gguf
tinyllama-1.1b-chat-q4km	https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf
phi-3-mini-4k-instruct-q4	https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-gguf/resolve/main/Phi-3-mini-4k-instruct-q4.gguf
qwen2.5-1.5b-instruct-q4km	https://huggingface.co/bartowski/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/Qwen2.5-1.5B-Instruct-Q4_K_M.gguf
EOF
)

echo "==> Downloading and hashing curated GGUFs to $TMP_DIR"
echo

# IFS-driven read: split each line on tab into id + url.
while IFS=$'\t' read -r id url; do
    [ -z "$id" ] && continue
    target="$TMP_DIR/$id.gguf"
    echo "--- $id"
    echo "    url: $url"
    curl -L --fail --silent --show-error -o "$target" "$url"
    size=$(wc -c < "$target" | tr -d ' ')
    sha=$(shasum -a 256 "$target" | awk '{print $1}')
    echo "    sizeBytes = ${size}L,"
    echo "    sha256 = \"$sha\","
    rm -f "$target"
    echo
done <<< "$MODELS"

echo "==> Done. Paste the size/sha pairs into registry/.../CuratedModels.kt."
