#!/usr/bin/env bash
set -euo pipefail

directory=".local/chatbot-keys"
private_key="$directory/private.pem"
public_key="$directory/public.pem"

if [[ -e "$private_key" || -e "$public_key" ]]; then
  if [[ "${1:-}" != "--rotate" ]]; then
    echo "Local chatbot keys already exist; pass --rotate to replace them." >&2
    exit 1
  fi
fi

mkdir -p "$directory"
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$private_key"
openssl rsa -pubout -in "$private_key" -out "$public_key" >/dev/null 2>&1
chmod 600 "$private_key"
chmod 644 "$public_key"
echo "Local chatbot keys created under $directory without printing key material."
