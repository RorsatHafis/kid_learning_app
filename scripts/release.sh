#!/usr/bin/env bash
# Releases the current commit to the server this runs on (DEPLOYMENT.md section 5):
#   1. checks the settings (scripts/check-production-settings.sh);
#   2. backs up the running release, if there is one (scripts/backup.sh) - the way back if a migration
#      has to be undone - and notes which release it was;
#   3. builds both images tagged with this release, starts them (Flyway migrates before the backend
#      reports healthy; the web front waits for it) and waits until every service is healthy;
#   4. runs the read-only smoke test against the public address.
# On a failure it stops and says how to go back.
#
#   scripts/release.sh deploy/production.settings
#
# Optional: RELEASE (default: the current git commit), PROJECT (default bookeyond-kid-prod), BACKUP_DIR
# (default /var/backups/bookeyond-kid), EXTRA_COMPOSE (more -f files, e.g. the rehearsal overlay),
# and CACERT / RESOLVE for the smoke test (the local rehearsal).
set -euo pipefail
docker() { MSYS_NO_PATHCONV=1 command docker "$@"; }
settings="${1:?usage: scripts/release.sh <settings file>}"
here="$(cd "$(dirname "$0")/.." && pwd)"
settings="$(cd "$(dirname "$settings")" && pwd)/$(basename "$settings")"
RELEASE="${RELEASE:-$(git -C "$here" rev-parse --short HEAD)}"
PROJECT="${PROJECT:-bookeyond-kid-prod}"
BACKUP_DIR="${BACKUP_DIR:-/var/backups/bookeyond-kid}"
if [ -n "$(git -C "$here" status --porcelain --untracked-files=no 2>/dev/null)" ]; then
  echo "The working tree has uncommitted changes: release a commit, not a working copy."; exit 1
fi
compose=(docker compose -p "$PROJECT" --env-file "$settings" -f "$here/deploy/compose.production.yml")
for extra in ${EXTRA_COMPOSE:-}; do compose+=(-f "$extra"); done
export RELEASE SETTINGS_FILE="$settings"
public="$(grep -E '^APP_PUBLIC_URL=' "$settings" | tail -1 | cut -d= -f2-)"

echo "== 1. settings"
bash "$here/scripts/check-production-settings.sh" "$settings"

echo "== 2. backup of the running release"
backend="$("${compose[@]}" ps -q backend 2>/dev/null || true)"
if [ -n "$backend" ]; then
  previous="$(docker inspect -f '{{.Config.Image}}' "$backend" | sed 's/.*://')"
  BACKEND="$backend" DB="$("${compose[@]}" ps -q db)" bash "$here/scripts/backup.sh" "$BACKUP_DIR"
  mkdir -p "$BACKUP_DIR" && echo "$previous" > "$BACKUP_DIR/previous-release"
  echo "The running release was $previous; its backup is the newest folder in $BACKUP_DIR."
else
  previous=""
  echo "Nothing running yet: first release, no backup needed."
fi

echo "== 3. build and start release $RELEASE"
if ! "${compose[@]}" up -d --build --wait --wait-timeout 600; then
  echo "The release did not become healthy. See: ${compose[*]} logs backend"
  [ -n "$previous" ] && echo "To go back: RELEASE=$previous ${compose[*]} up -d --wait   (and if the logs show a failed migration, restore the backup first: DEPLOYMENT.md section 5)"
  exit 1
fi

echo "== 4. smoke test"
if ! bash "$here/scripts/smoke-test.sh" "$public"; then
  echo "The smoke test failed."
  [ -n "$previous" ] && echo "To go back: RELEASE=$previous ${compose[*]} up -d --wait   (DEPLOYMENT.md section 5)"
  exit 1
fi
echo "Release $RELEASE is live at $public."
