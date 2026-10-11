#!/usr/bin/env bash
# Backs up a Docker Compose deployment of Bookeyond Kid into one folder: the database (pg_dump custom
# format), the media files, and - kept apart because it must never be restored over a newer copy - the
# erasure ledger. Run it on the server, e.g. daily from cron; copy the folder off the host (encrypted).
#
#   scripts/backup.sh /path/to/backups
#
# Optional: BACKEND / DB (container names, default bookeyond-kid-backend / bookeyond-kid-db), DB_USER /
# DB_NAME (default bookeyond_kid), LEDGER (the ERASURE_LEDGER_FILE path, default
# /var/lib/bookeyond/erasures/ledger.log; skipped when the file isn't there).
set -euo pipefail
docker() { MSYS_NO_PATHCONV=1 command docker "$@"; }
out="${1:?usage: scripts/backup.sh <backup folder>}"
BACKEND="${BACKEND:-bookeyond-kid-backend}"
DB="${DB:-bookeyond-kid-db}"
DB_USER="${DB_USER:-bookeyond_kid}"
DB_NAME="${DB_NAME:-bookeyond_kid}"
LEDGER="${LEDGER:-/var/lib/bookeyond/erasures/ledger.log}"
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
dir="$out/bookeyond-kid-$stamp"
mkdir -p "$dir"

# The database first, then the media: media is only ever added (files named by their content), so
# media taken at the same time or later always holds every file the database refers to.
docker exec "$DB" pg_dump -U "$DB_USER" -d "$DB_NAME" -Fc > "$dir/database.dump"
docker exec -i "$DB" pg_restore --list < "$dir/database.dump" > /dev/null
docker cp "$BACKEND:/var/lib/bookeyond/media" - > "$dir/media.tar"
if docker exec "$BACKEND" test -f "$LEDGER"; then
  docker cp "$BACKEND:$LEDGER" - > "$dir/erasure-ledger.tar"
fi
# What the backup holds, for scripts/restore-drill.sh to compare a restore against.
{
  echo "taken_at=$stamp"
  docker exec "$DB" psql -U "$DB_USER" -d "$DB_NAME" -tA -F= -c "select 'schema_version', max(version::int) from flyway_schema_history where success"
  for table in accounts families children enrollments activity_attempts answer_records learner_skill_states content_assets audit_events; do
    docker exec "$DB" psql -U "$DB_USER" -d "$DB_NAME" -tA -c "select '$table=' || count(*) from $table" 2>/dev/null || true
  done
  echo "media_files=$(tar -t < "$dir/media.tar" | grep -cv '/$')"
} > "$dir/manifest.txt"
(cd "$dir" && sha256sum ./* > SHA256SUMS)
echo "Backed up to $dir:"
ls -l "$dir"
