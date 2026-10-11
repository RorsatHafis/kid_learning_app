#!/usr/bin/env bash
# Keeps the newest daily backups and one per week for a while longer, and lists or deletes the rest:
#
#   scripts/prune-backups.sh /var/backups/bookeyond-kid               (lists what would be deleted)
#   scripts/prune-backups.sh /var/backups/bookeyond-kid --yes         (deletes it)
#
# KEEP_DAILY (default 14) and KEEP_WEEKLY (default 8) set how many. How long backups are kept is also a
# promise to families (PD-24: erased data lives on in backups until they expire) - state it in the
# privacy notice and keep these numbers in line with it. Only folders and encrypted files named
# bookeyond-kid-<time> are touched.
set -euo pipefail
dir="${1:?usage: scripts/prune-backups.sh <backup folder> [--yes]}"
apply="${2:-}"
KEEP_DAILY="${KEEP_DAILY:-14}"
KEEP_WEEKLY="${KEEP_WEEKLY:-8}"
# Newest first, one entry per backup time (a folder and its .tar.gpg count as one).
mapfile -t stamps < <(find "$dir" -maxdepth 1 -name 'bookeyond-kid-*' -printf '%f\n' 2>/dev/null \
  | sed -E 's/^bookeyond-kid-([0-9]{8}T[0-9]{6}Z).*/\1/' | sort -ru)
keep=()
weeks=()
dailyweeks=()
for i in "${!stamps[@]}"; do
  stamp="${stamps[$i]}"
  week="$(date -u -d "${stamp:0:4}-${stamp:4:2}-${stamp:6:2}" +%G-W%V)"
  if [ "$i" -lt "$KEEP_DAILY" ]; then
    # A week already covered by a daily backup needs no weekly one: weekly ones reach further back.
    keep+=("$stamp"); dailyweeks+=("$week")
  elif [[ " ${dailyweeks[*]:-} " == *" $week "* ]]; then
    :
  elif [ "${#weeks[@]}" -lt "$KEEP_WEEKLY" ] && [[ ! " ${weeks[*]} " == *" $week "* ]]; then
    keep+=("$stamp"); weeks+=("$week")
  fi
done
deleted=0
for stamp in "${stamps[@]}"; do
  [[ " ${keep[*]} " == *" $stamp "* ]] && continue
  for path in "$dir"/bookeyond-kid-"$stamp"*; do
    if [ "$apply" = "--yes" ]; then rm -rf "$path"; echo "deleted $path"; else echo "would delete $path"; fi
    deleted=$((deleted + 1))
  done
done
echo "${#keep[@]} backup(s) kept, $deleted path(s) $([ "$apply" = "--yes" ] && echo deleted || echo 'to delete (add --yes)')."
