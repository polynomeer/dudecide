#!/usr/bin/env bash
# Writes a compressed logical backup and removes old ones. Needs a pg_dump matching the server's major version.
#   DATABASE_URL=postgresql://user:pass@host:5432/dudecide ./scripts/db_backup.sh /path/to/backups
# RETENTION_DAYS (default 14) must match what the privacy policy promises: backups keep deleted chat imports until
# they rotate out. Store the directory encrypted, because backups hold conversation text.
set -euo pipefail

: "${DATABASE_URL:?DATABASE_URL is required}"
BACKUP_DIR="${1:?usage: db_backup.sh <backup-dir>}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"

mkdir -p "${BACKUP_DIR}"
umask 077
target="${BACKUP_DIR}/dudecide-$(date -u +%Y%m%dT%H%M%SZ).dump"

# Written under a temporary name so a failed run never looks like a usable backup.
pg_dump --format=custom --no-owner --dbname="${DATABASE_URL}" --file="${target}.partial"
mv "${target}.partial" "${target}"

find "${BACKUP_DIR}" -name 'dudecide-*.dump' -type f -mtime "+${RETENTION_DAYS}" -delete
echo "${target}"
