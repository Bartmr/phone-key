#!/bin/bash
set -euo pipefail

export SSH_AUTH_SOCK=/tmp/phone-key-ssh-agent.sock

STRING_TO_SIGN="Hello, this is a secure message signed by my SSH key!"
NAMESPACE="my-application"
SSH_PUBKEY="$HOME/.ssh/id_ed25519.pub"

if [ ! -f "$SSH_PUBKEY" ]; then
    echo "No SSH key at $SSH_PUBKEY. Generate one or point SSH_PUBKEY to an existing key."
    exit 1
fi

SIGNATURE=$(echo -n "$STRING_TO_SIGN" | ssh-keygen -Y sign -n "$NAMESPACE" -f "$SSH_PUBKEY")

SIG_FILE=$(mktemp)
echo "$SIGNATURE" > "$SIG_FILE"

ALLOWED_SIGNERS_FILE=$(mktemp)
PRINCIPAL="user@local"

echo "$PRINCIPAL $(cat "$SSH_PUBKEY")" > "$ALLOWED_SIGNERS_FILE"

echo -n "$STRING_TO_SIGN" \
    | ssh-keygen -Y verify -n "$NAMESPACE" -f "$ALLOWED_SIGNERS_FILE" -I "$PRINCIPAL" -s "$SIG_FILE"
