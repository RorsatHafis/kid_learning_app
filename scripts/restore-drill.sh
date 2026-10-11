#!/usr/bin/env bash
# A restore drill: proves a backup can be restored WITHOUT touching the live system. The backup's
# database is restored into a throwaway PostgreSQL container (removed afterwards), then compared with
# what the backup recorded when it was taken (manifest.txt): schema version and row counts, and every
# picture or recording the database refers to is in the backup's media. Run it monthly and after any
# change to backups (DEPLOYMENT.md section 6).
#
#   scripts/restore-drill.sh /var/backups/bookeyond-kid/bookeyond-kid-<time>
#   scripts/restore-drill.sh bookeyond-kid-<time>.tar.gpg      (decrypted first: needs the private key)
set -euo pipefail
docker() { MSYS_NO_PATHCONV=1 command docker "$@"; }
source="${1:?usage: scripts/restore-drill.sh <backup folder or .tar.gpg>}"
work="$(mktemp -d)"
container="bookeyond-restore-drill-$$"
cleanup() { docker rm -f "$container" >/dev/null 2>&1 || true; rm -rf "$work"; }
trap cleanup EXIT
failures=0
pass() { echo "PASS $*"; }
fail() { echo "FAIL $*"; failures=$((failures + 1)); }

if [ -f "$source" ] && [ "${source%.gpg}" != "$source" ]; then
  gpg --batch --quiet --decrypt "$source" | tar -x -C "$work"
  dir="$(find "$work" -mindepth 1 -maxdepth 1 -type d | head -1)"
  pass "decrypted $(basename "$source")"
else
  dir="$source"
fi
(cd "$dir" && sha256sum -c --quiet SHA256SUMS) && pass "checksums match" || { fail "checksums do not match"; exit 1; }
[ -f "$dir/manifest.txt" ] || { fail "no manifest.txt (a backup made before the drill existed: restore-only check)"; }

docker run -d --name "$container" -e POSTGRES_PASSWORD=drill -e POSTGRES_USER=bookeyond_kid -e POSTGRES_DB=bookeyond_kid postgres:18-alpine >/dev/null
for _ in $(seq 1 60); do docker exec "$container" pg_isready -U bookeyond_kid -d bookeyond_kid >/dev/null 2>&1 && break; sleep 1; done
sleep 2
docker exec -i "$container" pg_restore -U bookeyond_kid -d bookeyond_kid --no-owner --exit-on-error < "$dir/database.dump" \
  && pass "the database restores into an empty PostgreSQL" || { fail "pg_restore failed"; exit 1; }
q() { docker exec "$container" psql -U bookeyond_kid -d bookeyond_kid -tA -c "$1"; }

if [ -f "$dir/manifest.txt" ]; then
  version="$(q "select max(version::int) from flyway_schema_history where success")"
  grep -qx "schema_version=$version" "$dir/manifest.txt" && pass "schema version V$version as recorded" || fail "schema version V$version, recorded: $(grep schema_version "$dir/manifest.txt")"
  while IFS='=' read -r table expected; do
    case "$table" in schema_version|taken_at|media_files|"") continue;; esac
    actual="$(q "select count(*) from $table")"
    [ "$actual" = "$expected" ] && pass "$table: $actual rows as recorded" || fail "$table: $actual rows, recorded $expected"
  done < "$dir/manifest.txt"
fi

# Every stored file the database refers to is in the backup (files are named by their SHA-256).
{ tar -t < "$dir/media.tar" | grep -v '/$' || true; } | sed 's#.*/##' | sort > "$work/files"
q "select sha256 from content_assets where sha256 is not null" | sort > "$work/referenced"
missing="$(comm -23 "$work/referenced" "$work/files" | wc -l | tr -d ' ')"
[ "$missing" = 0 ] && pass "all $(wc -l < "$work/referenced" | tr -d ' ') referenced media files are in the backup" || fail "$missing referenced media files are missing from the backup"
[ -f "$dir/erasure-ledger.tar" ] && pass "the erasure ledger was copied (kept apart; never restored over the live one)" || echo "NOTE no erasure ledger in this backup (none configured)"

echo "Taken at $(grep taken_at "$dir/manifest.txt" 2>/dev/null | cut -d= -f2). $failures check(s) failed. The throwaway database is removed; nothing live was touched."
exit "$failures"
