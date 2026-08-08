-- Laeuft nur beim allerersten Start von Postgres mit leerem Datenverzeichnis (offizielles
-- Postgres-Image fuehrt alles unter /docker-entrypoint-initdb.d/ einmalig aus). Die App-eigene
-- Datenbank "konfplan" legt POSTGRES_DB bereits an - Keycloak braucht eine zweite, separate
-- Datenbank auf derselben Instanz.
CREATE DATABASE keycloak;
