-- Flyway Migration für SQLite - Deutsche Entitäten

DROP TABLE IF EXISTS User_SEQ;
DROP TABLE IF EXISTS Veranstaltung_SEQ;
DROP TABLE IF EXISTS EventSlot_SEQ;
DROP TABLE IF EXISTS Raum_SEQ;
DROP TABLE IF EXISTS Prioritaet_SEQ;
DROP TABLE IF EXISTS Verfügbarkeit_SEQ;
DROP TABLE IF EXISTS Vortrag_SEQ;

DROP TABLE IF EXISTS Verfügbarkeit;
DROP TABLE IF EXISTS Prioritaet;
DROP TABLE IF EXISTS Vortrag;
DROP TABLE IF EXISTS Raum_EventSlot;
DROP TABLE IF EXISTS Raum;
DROP TABLE IF EXISTS EventSlot;
DROP TABLE IF EXISTS Veranstaltung;
DROP TABLE IF EXISTS User;


-- 1. User Tabelle (Basis für Admin, Referent, Teilnehmer)
CREATE TABLE IF NOT EXISTS User
(
    id                 BIGINT PRIMARY KEY,
    email              VARCHAR(255) UNIQUE NOT NULL,
    password_hash      VARCHAR(255)        NOT NULL,
    role               VARCHAR(50)         NOT NULL, -- Discriminator
    first_name         VARCHAR(100),
    last_name          VARCHAR(100),
    version            BIGINT              NOT NULL DEFAULT 1,
    is_active          BOOLEAN                      DEFAULT 1,
    reset_token        VARCHAR(255),
    reset_token_expiry TIMESTAMP,

    -- Referent spezifisch
    biography          TEXT,

    -- Teilnehmer spezifisch
    organization       VARCHAR(255),
    job_role           VARCHAR(100)
);

-- 2. Veranstaltung
CREATE TABLE IF NOT EXISTS Veranstaltung
(
    id             BIGINT PRIMARY KEY,
    name           VARCHAR(255) NOT NULL,
    beginntAm      TIMESTAMP    NOT NULL,
    endetAm        TIMESTAMP,
    ort            VARCHAR(255) NOT NULL,
    logo           VARCHAR(255),
    logo_link      VARCHAR(255),
    organisator_id BIGINT       NOT NULL,
    version        BIGINT       NOT NULL DEFAULT 1,
    FOREIGN KEY (organisator_id) REFERENCES User (id)
);

-- 3. Zeit-Slots
CREATE TABLE IF NOT EXISTS EventSlot
(
    id          BIGINT PRIMARY KEY,
    startTime   TIMESTAMP NOT NULL,
    endTime     TIMESTAMP NOT NULL,
    description VARCHAR(255)
);

-- 4. Räume
CREATE TABLE IF NOT EXISTS Raum
(
    id         BIGINT PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    kapazitaet INTEGER      NOT NULL,
    etage      VARCHAR(100)
);

-- 5. Relation Raum <-> EventSlot
CREATE TABLE IF NOT EXISTS Raum_EventSlot
(
    raum_id      BIGINT NOT NULL,
    eventslot_id BIGINT NOT NULL,
    PRIMARY KEY (raum_id, eventslot_id),
    FOREIGN KEY (raum_id) REFERENCES Raum (id) ON DELETE CASCADE,
    FOREIGN KEY (eventslot_id) REFERENCES EventSlot (id) ON DELETE CASCADE
);

-- 6. Vorträge
CREATE TABLE IF NOT EXISTS Vortrag
(
    id                     BIGINT PRIMARY KEY,
    referent_id            BIGINT,
    title                  VARCHAR(255),
    abstractText           TEXT,
    targetAudience         VARCHAR(255),
    readyToRepeat          BOOLEAN         DEFAULT 0,
    istPflicht             BOOLEAN         DEFAULT 0, -- Neu: Pflichtvortrag-Flag
    maxPossibleRepetitions INTEGER         DEFAULT 1,
    maxRepetitions         INTEGER         DEFAULT 1,
    version                BIGINT NOT NULL DEFAULT 1,
    FOREIGN KEY (referent_id) REFERENCES User (id) ON DELETE CASCADE
);

-- 7. Prioritäten (Wahlen der Teilnehmer)
CREATE TABLE IF NOT EXISTS Prioritaet
(
    id             BIGINT PRIMARY KEY,
    teilnehmer_id  BIGINT,
    vortrag_id     BIGINT,
    priorityValue  INTEGER,
    lastUpdated    TIMESTAMP,
    version        BIGINT NOT NULL DEFAULT 1,
    FOREIGN KEY (teilnehmer_id) REFERENCES User (id) ON DELETE CASCADE,
    FOREIGN KEY (vortrag_id) REFERENCES Vortrag (id) ON DELETE CASCADE
);

-- 8. Verfügbarkeit der Referenten
CREATE TABLE IF NOT EXISTS Verfuegbarkeit
(
    id          BIGINT PRIMARY KEY,
    referent_id BIGINT,
    slot_id     BIGINT,
    isAvailable BOOLEAN DEFAULT 1,
    FOREIGN KEY (referent_id) REFERENCES User (id) ON DELETE CASCADE,
    FOREIGN KEY (slot_id) REFERENCES EventSlot (id) ON DELETE CASCADE
);


-- Sequenz-Tabellen für Hibernate
CREATE TABLE IF NOT EXISTS User_SEQ (next_val BIGINT);
CREATE TABLE IF NOT EXISTS Veranstaltung_SEQ (next_val BIGINT);
CREATE TABLE IF NOT EXISTS EventSlot_SEQ (next_val BIGINT);
CREATE TABLE IF NOT EXISTS Raum_SEQ (next_val BIGINT);
CREATE TABLE IF NOT EXISTS Prioritaet_SEQ (next_val BIGINT);
CREATE TABLE IF NOT EXISTS Verfuegbarkeit_SEQ (next_val BIGINT);
CREATE TABLE IF NOT EXISTS Vortrag_SEQ (next_val BIGINT);

INSERT INTO User_SEQ (next_val) VALUES (5);
INSERT INTO Veranstaltung_SEQ (next_val) VALUES (1);
INSERT INTO EventSlot_SEQ (next_val) VALUES (1);
INSERT INTO Raum_SEQ (next_val) VALUES (1);
INSERT INTO Prioritaet_SEQ (next_val) VALUES (1);
INSERT INTO Verfuegbarkeit_SEQ (next_val) VALUES (1);
INSERT INTO Vortrag_SEQ (next_val) VALUES (1);

-- STAMMDATEN
INSERT INTO User (id, email, password_hash, role, first_name, last_name, is_active, version)
VALUES (1, 'kathrin.jessen@rks-linz.de', '$2a$10$8920ztppz0N29GNOOw1FCuZJqMUIJhybpqw9tKwkS0rR3BmELJIlC', 'ADMIN', 'Kathrin', 'Jessen', 1, 1);

INSERT INTO User (id, email, password_hash, role, first_name, last_name, is_active, version)
VALUES (2, 'juergenkrey@yahoo.de', '$2a$10$8920ztppz0N29GNOOw1FCuZJqMUIJhybpqw9tKwkS0rR3BmELJIlC', 'ADMIN', 'Jürgen', 'Krey', 1, 1);

INSERT INTO User (id, email, password_hash, role, first_name, last_name, is_active, biography, version)
VALUES (3, 'speaker@rks-linz.de', '$2a$10$1GwWucNOnG9sePgSnEhLOeaBEVZuib1OLhWlHwQhDc8.fto5o3VCm', 'REFERENT', 'Jens', 'Riewa', 1, 'Erfahrener Nachrichtensprecher.', 1);

INSERT INTO User (id, email, password_hash, role, first_name, last_name, is_active, organization, job_role, version)
VALUES (4, 'schueler@rks-linz.de', '$2a$10$dCM9aDr8FRJRQKXI9o4IE.SWgxmGsMRSbtFtTJKrOBd51OWJfSUdi', 'TEILNEHMER', 'Peter', 'Schmitz', 1, 'RKS Linz', 'Schüler', 1);
