#!/bin/bash
set -euo pipefail

set -x

export SSH_AUTH_SOCK=/tmp/phone-key-ssh-agent.sock

ssh-add -l
