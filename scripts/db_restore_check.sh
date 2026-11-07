#!/usr/bin/env bash
# Proves a backup restores: loads it into a new scratch database, compares it with the expectations below, and drops it.
#   ADMIN_DATABASE_URL=postgresql://user:pass@host:5432/postgres ./scripts/db_restore_check.sh backup.dump
# Point this at a separate server when possible. It never touches an existing database.
set -euo pipefail

: "${ADMIN_DATABASE_URL:?ADMIN_DATABASE_URL is required}"
BACKUP_FILE="${1:?usage: db_restore_check.sh <backup-file>}"
scratch="dudecide_restore_check_$(date -u +%Y%m%d%H%M%S)"
scratch_url="${ADMIN_DATABASE_URL%/*}/${scratch}"

cleanup() {
  psql --quiet --dbname="${ADMIN_DATABASE_URL}" -c "DROP DATABASE IF EXISTS ${scratch}" >/dev/null
}
trap cleanup EXIT

psql --quiet --dbname="${ADMIN_DATABASE_URL}" -c "CREATE DATABASE ${scratch}" >/dev/null
pg_restore --no-owner --exit-on-error --dbname="${scratch_url}" "${BACKUP_FILE}"

query() {
  psql --tuples-only --no-align --dbname="${scratch_url}" -c "$1"
}

migrations="$(query "SELECT count(*) FROM flyway_schema_history WHERE success")"
failed="$(query "SELECT count(*) FROM flyway_schema_history WHERE NOT success")"
if [[ "${migrations}" -eq 0 || "${failed}" -ne 0 ]]; then
  echo "restore check failed: ${migrations} applied migrations, ${failed} failed" >&2
  exit 1
fi

for table in meeting membership message decision notification; do
  echo "${table}: $(query "SELECT count(*) FROM ${table}")"
done
echo "restore check passed: ${migrations} migrations"
