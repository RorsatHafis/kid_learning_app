#!/usr/bin/env bash
# Checks a running Docker Compose stack of Bookeyond Kid the way a deployment is used: through the
# web front (nginx), with the real database, media volume and backend image. Prints PASS/FAIL per
# check and exits non-zero if any check failed. Needs bash, curl, node and docker.
#
#   WEB=http://localhost:5173 ADMIN_EMAIL=... ADMIN_PASSWORD=... scripts/verify-runtime.sh
#
# Optional: COMPOSE (the compose command for this stack, default "docker compose"), BACKEND / DB
# (container names, default bookeyond-kid-backend / bookeyond-kid-db), DB_USER / DB_NAME
# (default bookeyond_kid), LEDGER (an ERASURE_LEDGER_FILE path to expect, default none).
#
# It uploads one 1x1 picture to the Studio's media library, restarts and replaces the backend (on a
# new address) and runs `up -d` again - so run it against a stack for testing, never one holding real data.
set -u
# Git Bash on Windows rewrites arguments that look like paths; container paths must reach docker as written.
docker() { MSYS_NO_PATHCONV=1 command docker "$@"; }
WEB="${WEB:-http://localhost:5173}"
COMPOSE="${COMPOSE:-docker compose}"
BACKEND="${BACKEND:-bookeyond-kid-backend}"
DB="${DB:-bookeyond-kid-db}"
DB_USER="${DB_USER:-bookeyond_kid}"
DB_NAME="${DB_NAME:-bookeyond_kid}"
LEDGER="${LEDGER:-}"
: "${ADMIN_EMAIL:?set ADMIN_EMAIL}" "${ADMIN_PASSWORD:?set ADMIN_PASSWORD}"
work="$(mktemp -d)"
# Git Bash: give curl (a Windows program there) the folder as Windows knows it.
command -v cygpath >/dev/null 2>&1 && work="$(cygpath -m "$work")"
failures=0
pass() { echo "PASS $*"; }
fail() { echo "FAIL $*"; failures=$((failures + 1)); }
field() { node -e "let d='';process.stdin.on('data',c=>d+=c).on('end',()=>{try{const v=JSON.parse(d)$1;console.log(v===undefined?'':v)}catch{console.log('')}})"; }
api_status() { curl -s -o /dev/null -w "%{http_code}" -X POST "$WEB/api/v1/auth/login" -H "Content-Type: application/json" -d '{"email":"nobody@example.test","password":"not-the-password-0000"}'; }
healthy() { for _ in $(seq 1 60); do [ "$(docker inspect -f '{{.State.Health.Status}}' "$1" 2>/dev/null)" = healthy ] && return 0; sleep 3; done; return 1; }
ip() { docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' "$BACKEND"; }
signin() { curl -s -X POST "$WEB/api/v1/auth/login" -H "Content-Type: application/json" -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}" | field .accessToken; }

# --- the web front -------------------------------------------------------------------------------
[ "$(curl -s -o /dev/null -w '%{http_code}' "$WEB/")" = 200 ] && pass "the app is served" || fail "the app is not served"
headers="$(curl -sI "$WEB/")"
for h in content-security-policy x-frame-options x-content-type-options referrer-policy permissions-policy; do
  echo "$headers" | grep -qi "^$h:" && pass "header $h" || fail "header $h missing"
done
echo "$headers" | grep -qi "^strict-transport-security:" && fail "HSTS sent over plain http" || pass "no HSTS over plain http"
curl -sI -H "X-Forwarded-Proto: https" "$WEB/" | grep -qi "^strict-transport-security: max-age=31536000"   && pass "HSTS when reached over https (behind a TLS proxy)" || fail "no HSTS over https"
echo "$headers" | grep -qi "^server: nginx/[0-9]" && fail "nginx version disclosed" || pass "no server version disclosed"
[ "$(curl -s -o /dev/null -w '%{http_code}' "$WEB/some/deep/link")" = 200 ] && pass "a deep link serves the app" || fail "deep link"

# --- database ------------------------------------------------------------------------------------
q() { docker exec "$DB" psql -U "$DB_USER" -d "$DB_NAME" -tAc "$1"; }
[ "$(q "select count(*) from flyway_schema_history where not success")" = 0 ] \
  && pass "migrations applied: $(q "select count(*) from flyway_schema_history where success and version is not null"), latest V$(q "select max(version::int) from flyway_schema_history where success")" \
  || fail "a migration failed"

# --- sign-in, invalid requests -------------------------------------------------------------------
token="$(signin)"
[ -n "$token" ] && pass "the administrator signs in through the web front" || { fail "administrator sign-in"; exit 1; }
[ "$(api_status)" = 401 ] && pass "a wrong password is refused (401)" || fail "wrong password -> $(api_status)"
# A browser on the app's own address is the same origin, whatever the host or port; another site is not.
origin_status() { curl -s -o /dev/null -w "%{http_code}" -X POST "$WEB/api/v1/auth/login" -H "Origin: $1" -H "Content-Type: application/json" -d '{"email":"nobody@example.test","password":"not-the-password-0000"}'; }
[ "$(origin_status "$WEB")" = 401 ] && pass "a request from the app's own origin is accepted" || fail "own origin -> $(origin_status "$WEB")"
# Behind a TLS proxy (X-Forwarded-Proto: https) the app's https origin is its own.
proxied_status() { curl -s -o /dev/null -w "%{http_code}" -X POST "$WEB/api/v1/auth/login" -H "X-Forwarded-Proto: https" -H "Origin: $1" -H "Content-Type: application/json" -d '{"email":"nobody@example.test","password":"not-the-password-0000"}'; }
https_origin="https://${WEB#*://}"
[ "$(proxied_status "$https_origin")" = 401 ] && pass "behind a TLS proxy the app's https origin is its own" || fail "proxied https origin -> $(proxied_status "$https_origin")"
[ "$(origin_status "https://elsewhere.example")" = 403 ] && pass "a request from another site is refused (403)" || fail "foreign origin -> $(origin_status https://elsewhere.example)"
[ "$(curl -s -o /dev/null -w '%{http_code}' "$WEB/api/v1/account")" = 401 ] && pass "no token, no account (401)" || fail "anonymous /account"
[ "$(curl -s -o /dev/null -w '%{http_code}' -X POST "$WEB/api/v1/auth/login" -H 'Content-Type: application/json' -d '{not json')" = 400 ] \
  && pass "malformed JSON is refused (400)" || fail "malformed JSON"
head -c 1100000 /dev/zero | tr '\0' 'a' > "$work/big.json"
[ "$(curl -s -o /dev/null -w '%{http_code}' -X POST "$WEB/api/v1/auth/login" -H 'Content-Type: application/json' --data-binary @"$work/big.json")" = 413 ] \
  && pass "an oversized API body stops at the web front (413)" || fail "oversized API body"

# --- media: upload through nginx, served back, stored by the app user ----------------------------
printf '\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR\x00\x00\x00\x01\x00\x00\x00\x01\x08\x06\x00\x00\x00\x1f\x15\xc4\x89\x00\x00\x00\rIDATx\x9cc\xf8\x0f\x00\x00\x01\x01\x00\x05\x18\xd8N\x00\x00\x00\x00IEND\xaeB`\x82' > "$work/pixel.png"
id="$(curl -s -X POST "$WEB/api/v1/studio/media" -H "Authorization: Bearer $token" -F "file=@$work/pixel.png;type=image/png" | field .id)"
[ -n "$id" ] && pass "a picture uploads through the web front" || fail "media upload"
printf 'GIF89a not really' > "$work/fake.png"
[ "$(curl -s -o /dev/null -w '%{http_code}' -X POST "$WEB/api/v1/studio/media" -H "Authorization: Bearer $token" -F "file=@$work/fake.png;type=image/png")" = 422 ] \
  && pass "a file that isn't what it claims is refused (422)" || fail "fake picture accepted"
head -c 6000000 /dev/zero > "$work/big.bin"
[ "$(curl -s -o /dev/null -w '%{http_code}' -X POST "$WEB/api/v1/studio/media" -H "Authorization: Bearer $token" -F "file=@$work/big.bin;type=image/png")" = 413 ] \
  && pass "an upload over the limit is refused (413)" || fail "oversized upload"
fetch_media() {
  local url
  url="$(curl -s "$WEB/api/v1/studio/media?size=100" -H "Authorization: Bearer $(signin)" | field ".items.find(i=>i.id==='$id')?.url")"
  [ -n "$url" ] && curl -s -o "$work/back.png" "$WEB$url" && cmp -s "$work/back.png" "$work/pixel.png"
}
fetch_media && pass "the picture is served back byte for byte" || fail "media not served back"
url="$(curl -s "$WEB/api/v1/studio/media?size=100" -H "Authorization: Bearer $token" | field ".items.find(i=>i.id==='$id')?.url")"
[ "$(curl -s -o /dev/null -w '%{http_code}' "$WEB$(echo "$url" | sed 's/s=[^&]*/s=AAAA/')")" = 404 ] && pass "a tampered media signature is refused (404)" || fail "tampered signature"
docker exec "$BACKEND" sh -c 'stat -c %U /var/lib/bookeyond/media; find /var/lib/bookeyond/media -type f -exec stat -c %U {} + | sort -u' | grep -qv '^app$' \
  && fail "media not all owned by app" || pass "media directory and files owned by app"
[ "$(docker exec "$BACKEND" id -un)" = app ] && pass "the backend runs as a non-root user" || fail "backend user"
if [ -n "$LEDGER" ]; then
  docker exec "$BACKEND" sh -c "test -w '$LEDGER'" && pass "the erasure ledger is writable ($LEDGER)" || fail "erasure ledger $LEDGER"
fi

# --- restart: everything is still there ----------------------------------------------------------
docker restart "$BACKEND" >/dev/null && healthy "$BACKEND" && pass "the backend restarts healthy" || fail "backend restart"
fetch_media && pass "the picture survives a backend restart" || fail "media lost on restart"

# --- the backend replaced on a new address: the web front finds it without a restart -------------
old="$(ip)"
$COMPOSE stop backend >/dev/null 2>&1
network="$(docker inspect -f '{{range $k, $v := .NetworkSettings.Networks}}{{$k}}{{end}}' "$BACKEND")"
docker run -d --rm --name verify-runtime-placeholder --network "$network" --ip "$old" busybox sleep 600 >/dev/null 2>&1
[ "$(api_status)" = 502 ] && pass "while the backend is down the API answers 502" || fail "backend down -> $(api_status)"
$COMPOSE up -d --force-recreate --no-deps backend >/dev/null 2>&1 && healthy "$BACKEND"
new="$(ip)"
recovered=""; for _ in $(seq 1 10); do [ "$(api_status)" = 401 ] && { recovered=yes; break; }; sleep 2; done
docker rm -f verify-runtime-placeholder >/dev/null 2>&1
[ "$old" != "$new" ] && [ -n "$recovered" ] && pass "the replaced backend ($old -> $new) is reached without restarting the web front" \
  || fail "after replacing the backend ($old -> $new) the web front did not reach it"
fetch_media && pass "the picture survives replacing the backend container" || fail "media lost on replacement"

rm -rf "$work"
echo "$failures check(s) failed"
exit "$failures"
