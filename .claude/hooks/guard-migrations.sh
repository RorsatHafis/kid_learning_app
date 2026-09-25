#!/usr/bin/env bash
# Human-review guard for Flyway/schema migration changes.
# This hook is intentionally conservative: migration changes should be explicit.
if git diff --name-only 2>/dev/null | grep -E '(^|/)(db/migration|migrations)/.*\.(sql|xml)$' >/dev/null; then
  echo "MIGRATION CHANGE DETECTED: stop and obtain explicit approval before treating schema changes as accepted."
fi
