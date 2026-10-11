#!/usr/bin/env bash
# Restores a backup made by scripts/backup.sh into a Docker Compose deployment of Bookeyond Kid:
# stops the backend, puts the database back as it was at the backup, adds the backup's media files
# (files added since stay - they are never changed, only added), and starts the backend again.
#
#   scripts/restore.sh /path/to/backups/bookeyond-kid-<time> --yes
#
# The erasure ledger is deliberately NOT restored: the running one is newer and lists every child and
# account erased since this backup, and the backend erases them again within one sweep (DEPLOYMENT.md
# section 6). Everything written after the backup is lost - this replaces the live database.
#
# Optional: COMPOSE (the compose command, default "docker compose"), BACKEND / DB / DB_USER / DB_NAME as
# for backup.sh.
set -euo pipefail
docker() { MSYS_NO_PATHCONV=1 command docker "$@"; }
dir="${1:?usage: scripts/restore.sh <backup folder> --yes}"
[ "${2:-}" = "--yes" ] || { echo "This replaces the live database with the backup's. Add --yes to go ahead."; exit 2; }
COMPOSE="${COMPOSE:-docker compose}"
BACKEND="${BACKEND:-bookeyond-kid-backend}"
DB="${DB:-bookeyond-kid-db}"
DB_USER="${DB_USER:-bookeyond_kid}"
DB_NAME="${DB_NAME:-bookeyond_kid}"

(cd "$dir" && sha256sum -c --quiet SHA256SUMS) || { echo "The backup's checksums don't match; nothing was changed."; exit 1; }

$COMPOSE stop backend
docker exec -i "$DB" pg_restore -U "$DB_USER" -d "$DB_NAME" --clean --if-exists --no-owner --exit-on-error < "$dir/database.dump"
# The media tar holds the folder "media"; unpack it beside the live one (the stopped container's volume).
docker cp -a - "$BACKEND:/var/lib/bookeyond" < "$dir/media.tar"
$COMPOSE start backend
for _ in $(seq 1 60); do
  [ "$(docker inspect -f '{{.State.Health.Status}}' "$BACKEND")" = healthy ] && { echo "Restored from $dir; the backend is up."; exit 0; }
  sleep 3
done
echo "Restored, but the backend is not healthy yet: check 'docker compose logs backend'."
exit 1
