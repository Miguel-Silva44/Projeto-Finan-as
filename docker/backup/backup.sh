#!/usr/bin/env bash
set -Eeuo pipefail
umask 077
export PGPASSWORD="$(cat /run/secrets/admin_password)"
export GNUPGHOME
GNUPGHOME="$(mktemp -d)"
trap 'rm -rf -- "$GNUPGHOME"' EXIT
mode="${1:-once}"
interval="${BACKUP_INTERVAL_SECONDS:-86400}"
retention="${BACKUP_RETENTION_DAYS:-90}"
[[ "$interval" =~ ^[0-9]+$ && "$interval" -ge 60 ]] || { echo 'Invalid backup interval' >&2; exit 2; }
[[ "$retention" =~ ^[0-9]+$ && "$retention" -ge 1 && "$retention" -le 90 ]] || { echo 'Retention must be 1..90 days' >&2; exit 2; }
[[ "$(wc -c < /run/secrets/backup_password)" -ge 16 ]] || { echo 'Backup password must have at least 16 characters' >&2; exit 2; }
mkdir -p /backups
encrypt() { gpg --batch --yes --pinentry-mode loopback --passphrase-file /run/secrets/backup_password --symmetric --cipher-algo AES256 --s2k-digest-algo SHA512 --s2k-count 65011712 --output "$1"; }
decrypt() { gpg --batch --pinentry-mode loopback --passphrase-file /run/secrets/backup_password --decrypt "$1"; }
once() {
  local file="financas-$(date -u +%Y%m%dT%H%M%SZ)-$(cat /proc/sys/kernel/random/uuid).dump.gpg"
  local partial="/backups/$file.partial"
  if ! pg_dump --format=custom --no-owner --no-privileges | encrypt "$partial"; then
    rm -f -- "$partial"
    echo 'Backup failed; previous successful backups preserved' >&2
    return 1
  fi
  mv -- "$partial" "/backups/$file"
  (cd /backups && sha256sum "$file" > "$file.sha256")
  date +%s > /backups/last-success.tmp
  mv /backups/last-success.tmp /backups/last-success
  # Only this application's completed encrypted backups are eligible for retention.
  find /backups -maxdepth 1 -type f \( -name 'financas-*.dump.gpg' -o -name 'financas-*.dump.gpg.sha256' \) -mtime "+$retention" -delete
  echo "Backup encrypted and checksum written: $file"
}
case "$mode" in
  once) once ;;
  daemon) while true; do once || echo 'Backup failed; retry in 5 minutes' >&2; if test -f /backups/last-success && (( $(date +%s) - $(cat /backups/last-success) < interval )); then sleep "$interval"; else sleep 300; fi; done ;;
  verify)
    file="${2:?Specify backup basename}"
    [[ "$file" =~ ^financas-[A-Za-z0-9-]+\.dump\.gpg$ ]] || exit 2
    (cd /backups && sha256sum -c "$file.sha256")
    decrypt "/backups/$file" | pg_restore --list >/dev/null
    echo 'Checksum, decryption and archive structure OK'
    ;;
  restore)
    file="${2:?Specify backup basename}"; target="${3:?Specify a new database name}"
    [[ "$file" =~ ^financas-[A-Za-z0-9-]+\.dump\.gpg$ && "$target" =~ ^financas_restore_[a-z0-9_]+$ ]] || { echo 'Invalid backup or restore database name' >&2; exit 2; }
    (cd /backups && sha256sum -c "$file.sha256")
    # Authenticate the complete archive before restoring any records.
    decrypt "/backups/$file" | pg_restore --list >/dev/null
    # CREATE DATABASE fails if it already exists. Never clean/drop a populated target.
    createdb --owner=financas_migrator "$target"
    decrypt "/backups/$file" | pg_restore --dbname="$target" --role=financas_migrator --single-transaction --exit-on-error --no-owner --no-privileges
    psql --dbname="$target" --no-psqlrc --set ON_ERROR_STOP=1 --file=/opt/backup/runtime-grants.sql
    echo "Restored in independent database $target. Reconcile before switching application."
    ;;
  *) echo 'Use daemon, once, verify <file> or restore <file> <new_database>' >&2; exit 2 ;;
esac
