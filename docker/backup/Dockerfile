ARG POSTGRES_IMAGE=postgres:18.6-bookworm@sha256:f1ea2a1a4fb7b68d4836be1fdcb8a6466a89b072158bf9d97663600538c294c5
FROM ${POSTGRES_IMAGE}
RUN apt-get update && apt-get install -y --no-install-recommends gnupg \
    && rm -rf /var/lib/apt/lists/* && mkdir -p /backups /opt/backup \
    && chown postgres:postgres /backups /opt/backup
COPY --chown=postgres:postgres docker/backup/backup.sh /opt/backup/backup.sh
COPY src/main/resources/db/postgresql/V7__runtime_grants.sql /opt/backup/runtime-grants.sql
# Backup bind mounts on Windows/Linux can have different host owners. The job uses
# the container's root by default; override user in a deployment with a prepared UID.
ENTRYPOINT ["bash", "/opt/backup/backup.sh"]
CMD ["daemon"]
