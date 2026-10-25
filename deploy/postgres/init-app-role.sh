#!/bin/sh
set -eu

# Executado apenas quando o volume de dados do PostgreSQL é criado.
psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --set ON_ERROR_STOP=1 \
  --set app_user="$DB_USER" --set app_password="$DB_PASSWORD" --set db_name="$POSTGRES_DB" <<'SQL'
CREATE ROLE :"app_user" LOGIN PASSWORD :'app_password'
  NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
GRANT CONNECT ON DATABASE :"db_name" TO :"app_user";
GRANT USAGE ON SCHEMA public TO :"app_user";
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO :"app_user";
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
  GRANT USAGE, SELECT ON SEQUENCES TO :"app_user";
SQL
