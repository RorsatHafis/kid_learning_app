#!/usr/bin/env bash
# Checks deploy/production.settings before a release: every required value set and no placeholder left,
# secrets long enough and not the local ones, the public address consistent with the domain, mail and
# legal settings coherent, nothing that would make the backend refuse to start, and the production
# Compose file resolving with these values. Prints PROBLEM/WARNING lines; exits 1 if there is a problem.
#
#   scripts/check-production-settings.sh deploy/production.settings
#
# REHEARSAL=1 allows a local mail catcher (for the local rehearsal, DEPLOYMENT.md section 5a).
set -u
file="${1:?usage: scripts/check-production-settings.sh <settings file>}"
[ -f "$file" ] || { echo "PROBLEM $file does not exist (copy deploy/production.settings.example)"; exit 1; }
problems=0
problem() { echo "PROBLEM $*"; problems=$((problems + 1)); }
warning() { echo "WARNING $*"; }

# KEY=value lines only; comments and blanks ignored; the last value of a key wins, as for Compose.
value() { grep -E "^[[:space:]]*$1=" "$file" | tail -1 | sed -E "s/^[[:space:]]*$1=//; s/[[:space:]]+#.*$//; s/^['\"]//; s/['\"]$//; s/\r$//"; }
has() { grep -qE "^[[:space:]]*$1=" "$file"; }

for key in APP_DOMAIN APP_PUBLIC_URL CORS_ALLOWED_ORIGINS ACME_EMAIL JWT_SECRET JWT_ISSUER DB_PASSWORD MAIL_HOST MAIL_FROM; do
  [ -n "$(value "$key")" ] || problem "$key is not set"
done
while read -r line; do problem "still a placeholder: line $line"; done < <(grep -nE "^[^#]*CHANGE_ME" "$file" | sed 's/=.*/=.../')

domain="$(value APP_DOMAIN)"
public="$(value APP_PUBLIC_URL)"
if [ -n "$domain" ]; then
  echo "$domain" | grep -qE '^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$' \
    || problem "APP_DOMAIN '$domain' is not a domain name (lower case, no scheme or path)"
  echo "$public" | grep -qE "^https://$domain(:[0-9]+)?$" \
    || problem "APP_PUBLIC_URL must be https://$domain (it is '$public')"
fi
cors="$(value CORS_ALLOWED_ORIGINS)"
echo ",$cors," | tr -d ' ' | grep -qF ",$public," || problem "CORS_ALLOWED_ORIGINS must include APP_PUBLIC_URL ($public)"
echo "$cors" | grep -q '\*' && problem "CORS_ALLOWED_ORIGINS must not contain *"
echo "$(value ACME_EMAIL)" | grep -qE '^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$' || problem "ACME_EMAIL is not an email address"

secret="$(value JWT_SECRET)"
[ "$(printf '%s' "$secret" | wc -c)" -ge 32 ] || problem "JWT_SECRET must be at least 32 bytes (openssl rand -base64 48)"
case "$secret" in *local-only*|*ci-only*|*test-secret*|*example*) problem "JWT_SECRET is a local/test key - make a new one";; esac
db="$(value DB_PASSWORD)"
[ "$(printf '%s' "$db" | wc -c)" -ge 16 ] || problem "DB_PASSWORD must be at least 16 characters"
[ "$db" = "bookeyond-kid-local" ] && problem "DB_PASSWORD is the local default"

echo "$(value MAIL_FROM)" | grep -qE '^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$' || problem "MAIL_FROM is not an email address"
case "$(value MAIL_HOST)" in
  localhost|127.0.0.1|mail|mailpit)
    [ "${REHEARSAL:-}" = 1 ] && warning "MAIL_HOST is a local mail catcher (rehearsal only)" \
      || problem "MAIL_HOST is a local mail catcher; use your mail provider's SMTP host";;
esac
if [ "$(value MAIL_SMTP_AUTH)" != false ]; then
  [ -n "$(value MAIL_USERNAME)" ] && [ -n "$(value MAIL_PASSWORD)" ] || problem "MAIL_USERNAME and MAIL_PASSWORD are needed (or MAIL_SMTP_AUTH=false)"
fi

legal=0
for key in LEGAL_PRIVACY_URL LEGAL_TERMS_URL LEGAL_VERSION; do [ -n "$(value "$key")" ] && legal=$((legal + 1)); done
case "$legal" in
  0) warning "no privacy notice or terms of use (LEGAL_*): needed before real families sign up (PD-18)";;
  3) for key in LEGAL_PRIVACY_URL LEGAL_TERMS_URL; do value "$key" | grep -qE '^https://' || problem "$key must be an https:// address"; done;;
  *) problem "set all three of LEGAL_PRIVACY_URL, LEGAL_TERMS_URL and LEGAL_VERSION, or none";;
esac

if [ "$(value PLATFORM_BOOTSTRAPADMIN_ENABLED)" = true ]; then
  [ -n "$(value PLATFORM_BOOTSTRAPADMIN_EMAIL)" ] && [ -n "$(value PLATFORM_BOOTSTRAPADMIN_PASSWORD)" ] \
    || problem "the first administrator needs PLATFORM_BOOTSTRAPADMIN_EMAIL and _PASSWORD"
  [ "$(printf '%s' "$(value PLATFORM_BOOTSTRAPADMIN_PASSWORD)" | wc -c)" -ge 12 ] || problem "the first administrator's password needs 12+ characters"
  warning "the first-administrator lines are set: remove them after the first start"
fi

# Variables the backend refuses because they bypass the documented ones (ConfigurationContract), or that
# deploy/compose.production.yml sets itself.
for key in SPRING_PROFILES_ACTIVE SPRING_DATASOURCE_URL SPRING_DATASOURCE_USERNAME SPRING_DATASOURCE_PASSWORD \
           PLATFORM_SECURITY_JWT_SECRET PLATFORM_MAIL_FROM PLATFORM_MEDIA_ROOT DB_URL DB_USERNAME MEDIA_ROOT ERASURE_LEDGER_FILE; do
  has "$key" && problem "$key must not be in the settings file (the production Compose file sets it, or it bypasses a documented setting)"
done

if command -v docker >/dev/null 2>&1; then
  here="$(cd "$(dirname "$0")/.." && pwd)"
  absolute="$(cd "$(dirname "$file")" && pwd)/$(basename "$file")"
  if RELEASE=check SETTINGS_FILE="$absolute" docker compose --env-file "$file" -f "$here/deploy/compose.production.yml" config -q 2> "${TMPDIR:-/tmp}/compose-check.$$"; then
    echo "OK deploy/compose.production.yml resolves with these settings"
  else
    problem "deploy/compose.production.yml does not resolve: $(head -3 "${TMPDIR:-/tmp}/compose-check.$$")"
  fi
  rm -f "${TMPDIR:-/tmp}/compose-check.$$"
fi

if [ "$problems" -eq 0 ]; then echo "OK $file is ready"; else echo "$problems problem(s) - fix them before releasing"; fi
[ "$problems" -eq 0 ]
