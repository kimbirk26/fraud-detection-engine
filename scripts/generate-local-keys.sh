#!/usr/bin/env bash
#
# Generates an RSA key pair for local JWT signing and writes the
# JWT_PRIVATE_KEY and JWT_PUBLIC_KEY environment variables to .env.local.
#
# Usage:
#   ./scripts/generate-local-keys.sh
#   source .env.local
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
ENV_FILE="$PROJECT_DIR/.env.local"
TMP_DIR=$(mktemp -d)
trap 'rm -rf "$TMP_DIR"' EXIT

echo "Generating RSA 2048-bit key pair..."

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 \
  -out "$TMP_DIR/private.pem" 2>/dev/null

openssl pkey -in "$TMP_DIR/private.pem" -pubout \
  -out "$TMP_DIR/public.pem" 2>/dev/null

PRIVATE_KEY=$(<"$TMP_DIR/private.pem")
PUBLIC_KEY=$(<"$TMP_DIR/public.pem")

cat > "$ENV_FILE" <<ENVEOF
# Auto-generated local JWT keys — do not commit this file.
# Regenerate with: ./scripts/generate-local-keys.sh

export JWT_PRIVATE_KEY='${PRIVATE_KEY}'

export JWT_PUBLIC_KEY='${PUBLIC_KEY}'
ENVEOF

echo "Keys written to $ENV_FILE"
echo "Run 'source $ENV_FILE' before starting the app with the local profile."
