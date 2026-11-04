#!/bin/sh
# Runs once, when the PostgreSQL data directory is first created. Gives Keycloak its own
# database so its realm, users and sessions survive container recreation and instance stops.
set -eu
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<SQL
CREATE DATABASE keycloak OWNER "$POSTGRES_USER";
SQL
