#!/usr/bin/env bash
# Encrypts a backup folder (from scripts/backup.sh) to a public key, ready to copy off the server:
# the server holds only the public key, so a stolen server or backup store can't read the backups.
# Keep the private key offline (and a second copy somewhere safe): without it no backup can be restored.
#
#   scripts/encrypt-backup.sh /var/backups/bookeyond-kid/bookeyond-kid-<time> <gpg key id or email>
#
# Writes <folder>.tar.gpg beside the folder and checks it is a complete encrypted file for that key.
# One-time setup on a trusted machine: gpg --quick-generate-key "Bookeyond Kid backups" rsa4096 encrypt never
# then: gpg --export --armor <key> > backup-key.asc, and on the server: gpg --import backup-key.asc
set -euo pipefail
dir="${1:?usage: scripts/encrypt-backup.sh <backup folder> <recipient>}"
recipient="${2:?usage: scripts/encrypt-backup.sh <backup folder> <recipient>}"
dir="${dir%/}"
out="$dir.tar.gpg"
tar -C "$(dirname "$dir")" -c "$(basename "$dir")" \
  | gpg --batch --yes --trust-model always --auto-key-locate local --encrypt --recipient "$recipient" --output "$out"
gpg --batch --list-packets "$out" 2>/dev/null | grep -q "pubkey enc packet" \
  || { echo "The encrypted file looks incomplete: $out"; exit 1; }
echo "Encrypted to $out ($(wc -c < "$out" | tr -d ' ') bytes) for $recipient."
