#!/usr/bin/env bash
# Read-only checks of a deployed Bookeyond Kid through its public address - safe against production:
# it creates nothing and changes nothing (one failed sign-in for an address that has no account).
#
#   scripts/smoke-test.sh https://app.example.org
#
# Optional: CACERT (a CA certificate to trust, for the local rehearsal's own certificate authority),
# RESOLVE (curl --resolve value, e.g. bookeyond.localhost:8443:127.0.0.1), HTTP_URL (the plain-http
# address, to check it redirects to https; default http://<host>).
set -u
url="${1:?usage: scripts/smoke-test.sh https://your-domain}"
url="${url%/}"
host="$(echo "$url" | sed -E 's#^https://([^/:]+).*#\1#')"
curl_opts=(-s --max-time 20)
[ -n "${CACERT:-}" ] && curl_opts+=(--cacert "$CACERT")
[ -n "${RESOLVE:-}" ] && curl_opts+=(--resolve "$RESOLVE")
failures=0
pass() { echo "PASS $*"; }
fail() { echo "FAIL $*"; failures=$((failures + 1)); }
c() { curl "${curl_opts[@]}" "$@"; }

echo "$url" | grep -q '^https://' || fail "the address must be https://"
code="$(c -o /dev/null -w '%{http_code}' "$url/")"
[ "$code" = 200 ] && pass "the app is served over https with a certificate this machine trusts" || fail "GET $url/ -> $code (certificate or proxy problem?)"
headers="$(c -I "$url/")"
for h in strict-transport-security content-security-policy x-frame-options x-content-type-options referrer-policy; do
  echo "$headers" | grep -qi "^$h:" && pass "header $h" || fail "header $h missing"
done
echo "$headers" | grep -qi "^server: nginx/[0-9]" && fail "nginx version disclosed" || pass "no server version disclosed"

plain="${HTTP_URL:-http://$host}"
redirect="$(c -o /dev/null -w '%{http_code} %{redirect_url}' "$plain/")"
echo "$redirect" | grep -qE '^30[1278] https://' && pass "plain http redirects to https" || fail "http -> $redirect"

legal="$(c -w ' %{http_code}' "$url/api/v1/auth/legal")"
echo "$legal" | grep -qE ' 200$' && pass "the API answers through the proxy (legal documents: $(echo "$legal" | sed -E 's/ 200$//'))" || fail "/api/v1/auth/legal -> $legal"
login() { c -o /dev/null -w '%{http_code}' -X POST "$url/api/v1/auth/login" -H "Origin: $1" -H 'Content-Type: application/json' \
  -d '{"email":"smoke-test-no-account@invalid.example","password":"not-the-password-0000"}'; }
[ "$(login "$url")" = 401 ] && pass "a sign-in from the app's own origin reaches the backend (refused: no such account)" || fail "own-origin sign-in -> $(login "$url")"
[ "$(login https://elsewhere.example)" = 403 ] && pass "a request from another site is refused (403)" || fail "foreign origin -> $(login https://elsewhere.example)"
actuator="$(c -o /dev/null -w '%{http_code} %{content_type}' "$url/actuator/health")"
echo "$actuator" | grep -qi json && fail "the backend's actuator is reachable from outside ($actuator)" || pass "the backend's actuator is not reachable from outside"

echo "$failures check(s) failed"
exit "$failures"
