#!/usr/bin/env bash
set -Eeuo pipefail
# Init only, on an empty PGDATA. Password changes on existing databases require ALTER ROLE.
psql --no-psqlrc --set ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  --set=migration_password="$(cat /run/secrets/migration_password)" \
  --set=app_password="$(cat /run/secrets/app_password)" <<'SQL'
CREATE ROLE financas_migrator LOGIN PASSWORD :'migration_password' NOSUPERUSER NOCREATEDB NOCREATEROLE;
CREATE ROLE financas_app LOGIN PASSWORD :'app_password' NOSUPERUSER NOCREATEDB NOCREATEROLE;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
ALTER SCHEMA public OWNER TO financas_migrator;
GRANT USAGE ON SCHEMA public TO financas_app;
ALTER DEFAULT PRIVILEGES FOR ROLE financas_migrator IN SCHEMA public GRANT SELECT,INSERT,UPDATE,DELETE ON TABLES TO financas_app;
ALTER DEFAULT PRIVILEGES FOR ROLE financas_migrator IN SCHEMA public GRANT USAGE,SELECT ON SEQUENCES TO financas_app;
SQL
