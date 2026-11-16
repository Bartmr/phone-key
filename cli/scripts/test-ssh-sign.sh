#!/bin/bash
set -euo pipefail

set -x

export SSH_AUTH_SOCK=/tmp/phone-key-ssh-agent.sock

echo "=== Step 1: List identities from the phone ==="
ssh-add -l

echo ""
echo "=== Step 2: Get the first public key from the phone agent ==="
PUBKEY_FILE=$(mktemp)
# Take only the first key to avoid multi-key signing complexity
ssh-add -L | head -1 > "$PUBKEY_FILE"

if [ ! -s "$PUBKEY_FILE" ]; then
    echo "ERROR: No keys found in the agent. Make sure the phone is connected and has ECDSA keys."
    rm "$PUBKEY_FILE"
    exit 1
fi

echo "Public key:"
cat "$PUBKEY_FILE"

echo ""
echo "=== Step 3: Sign a test message ==="
STRING_TO_SIGN="Hello, this is a secure message signed by my SSH key!"
NAMESPACE="phone-key-test"
SIG_FILE=$(mktemp)

echo -n "$STRING_TO_SIGN" | ssh-keygen -Y sign -n "$NAMESPACE" -f "$PUBKEY_FILE" > "$SIG_FILE"

echo "Signature:"
cat "$SIG_FILE"

echo ""
echo "=== Step 4: Verify the signature ==="
ALLOWED_SIGNERS_FILE=$(mktemp)
PRINCIPAL="phone-key-user"

echo "$PRINCIPAL $(cat "$PUBKEY_FILE")" > "$ALLOWED_SIGNERS_FILE"

echo -n "$STRING_TO_SIGN" \
    | ssh-keygen -Y verify -n "$NAMESPACE" -f "$ALLOWED_SIGNERS_FILE" -I "$PRINCIPAL" -s "$SIG_FILE"

echo ""
echo "=== SUCCESS: SSH signing end-to-end works! ==="

rm "$PUBKEY_FILE" "$SIG_FILE" "$ALLOWED_SIGNERS_FILE"
