-- Runs once on first PostgreSQL startup (empty data dir).
-- Creates the dedicated database Keycloak uses; the 'occupi' database for room
-- metadata is created by POSTGRES_DB. Both are owned by the POSTGRES_USER.
CREATE DATABASE keycloak;
