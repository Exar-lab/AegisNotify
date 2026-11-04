-- Used by docker-compose.prod.yml only. The PostgreSQL image runs files in
-- /docker-entrypoint-initdb.d once, when the data volume is first created, as
-- the POSTGRES_USER superuser. Keycloak stores its state in this database.
-- On an existing volume, create it by hand (docs/deploy-vm.md, Troubleshooting).
CREATE DATABASE keycloak;
