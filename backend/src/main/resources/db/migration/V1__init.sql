-- Flyway Migration für SQLite - Vollständiges Schema inkl. Zuweisung

DROP TABLE IF EXISTS User_SEQ;
DROP TABLE IF EXISTS Veranstaltung_SEQ;
DROP TABLE IF EXISTS EventSlot_SEQ;
DROP TABLE IF EXISTS Raum_SEQ;
DROP TABLE IF EXISTS Prioritaet_SEQ;
DROP TABLE IF EXISTS Verfuegbarkeit_SEQ;
DROP TABLE IF EXISTS Vortrag_SEQ;
DROP TABLE IF EXISTS Zuweisung_SEQ;

DROP TABLE IF EXISTS Zuweisung;
DROP TABLE IF EXISTS Wahlvortrag_EventSlot;
DROP TABLE IF EXISTS Verfuegbarkeit;
DROP TABLE IF EXISTS Prioritaet;
DROP TABLE IF EXISTS Vortrag;
DROP TABLE IF EXISTS Teilnehmer_EventSlot;
DROP TABLE IF EXISTS Raum_EventSlot;
DROP TABLE IF EXISTS Raum;
DROP TABLE IF EXISTS EventSlot;
DROP TABLE IF EXISTS Veranstaltung;
DROP TABLE IF EXISTS User;


-- 1. User Tabelle
CREATE TABLE IF NOT EXISTS User
(
    id                 INTEGER PRIMARY KEY AUTOINCREMENT,
    email              VARCHAR(255) UNIQUE NOT NULL,
    password_hash      VARCHAR(255)        NOT NULL,
    role               VARCHAR(50)         NOT NULL,
    first_name         VARCHAR(100),
    last_name          VARCHAR(100),
    version            BIGINT              NOT NULL DEFAULT 1,
    is_active          BOOLEAN                      DEFAULT 1,
    reset_token        VARCHAR(255),
    reset_token_expiry TIMESTAMP,
    biography          TEXT,
    job_role           VARCHAR(100),
    organisation       VARCHAR(255),
    slogan             VARCHAR(255),
    gruppe             VARCHAR(255),
    veranstaltung_id   INTEGER,
    FOREIGN KEY (veranstaltung_id) REFERENCES Veranstaltung (id)
);

-- 2. Veranstaltung
CREATE TABLE IF NOT EXISTS Veranstaltung
(
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    name           VARCHAR(255) NOT NULL,
    beginntAm      TIMESTAMP    NOT NULL,
    endetAm        TIMESTAMP,
    ort            VARCHAR(255) NOT NULL,
    logo           VARCHAR(255),
    logo_link      VARCHAR(255),
    organisator_id INTEGER      NOT NULL,
    version        BIGINT       NOT NULL DEFAULT 1,
    UNIQUE (name, beginntAm),
    FOREIGN KEY (organisator_id) REFERENCES User (id)
);

-- 3. Zeit-Slots
CREATE TABLE IF NOT EXISTS EventSlot
(
    id                 INTEGER PRIMARY KEY AUTOINCREMENT,
    startTime          TIMESTAMP    NOT NULL,
    endTime            TIMESTAMP    NOT NULL,
    description        VARCHAR(255),
    veranstaltung_id   INTEGER      NOT NULL,
    FOREIGN KEY (veranstaltung_id) REFERENCES Veranstaltung (id) ON DELETE CASCADE
);

-- 4. Räume
CREATE TABLE IF NOT EXISTS Raum
(
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    name       VARCHAR(255) NOT NULL,
    kapazitaet INTEGER      NOT NULL,
    etage      VARCHAR(100)
);

-- 5. Relationen
CREATE TABLE IF NOT EXISTS Raum_EventSlot
(
    raum_id INTEGER NOT NULL, eventslot_id INTEGER NOT NULL,
    PRIMARY KEY (raum_id, eventslot_id),
    FOREIGN KEY (raum_id) REFERENCES Raum (id) ON DELETE CASCADE,
    FOREIGN KEY (eventslot_id) REFERENCES EventSlot (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS Teilnehmer_EventSlot
(
    teilnehmer_id INTEGER NOT NULL, eventslot_id INTEGER NOT NULL,
    PRIMARY KEY (teilnehmer_id, eventslot_id),
    FOREIGN KEY (teilnehmer_id) REFERENCES User (id) ON DELETE CASCADE,
    FOREIGN KEY (eventslot_id) REFERENCES EventSlot (id) ON DELETE CASCADE
);

-- 6. Vorträge
CREATE TABLE IF NOT EXISTS Vortrag
(
    id                     INTEGER PRIMARY KEY AUTOINCREMENT,
    vortrag_typ            VARCHAR(50)  NOT NULL,
    referent_id            INTEGER      NOT NULL,
    veranstaltung_id       INTEGER      NOT NULL,
    titel                  VARCHAR(255) NOT NULL,
    inhalt                 TEXT,
    zielgruppe             VARCHAR(255),
    version                BIGINT       NOT NULL DEFAULT 1,
    pflichtraum_id         INTEGER,
    pflichtslot_id         INTEGER,
    wiederholbar           BOOLEAN               DEFAULT 0,
    maxWiederholungen      INTEGER               DEFAULT 1,
    FOREIGN KEY (referent_id) REFERENCES User (id) ON DELETE CASCADE,
    FOREIGN KEY (veranstaltung_id) REFERENCES Veranstaltung (id) ON DELETE CASCADE,
    FOREIGN KEY (pflichtraum_id) REFERENCES Raum (id),
    FOREIGN KEY (pflichtslot_id) REFERENCES EventSlot (id)
);

CREATE TABLE IF NOT EXISTS Wahlvortrag_EventSlot
(
    vortrag_id INTEGER NOT NULL, eventslot_id INTEGER NOT NULL,
    PRIMARY KEY (vortrag_id, eventslot_id),
    FOREIGN KEY (vortrag_id) REFERENCES Vortrag (id) ON DELETE CASCADE,
    FOREIGN KEY (eventslot_id) REFERENCES EventSlot (id) ON DELETE CASCADE
);

-- 7. Zuweisung (Ergebnis der Optimierung)
CREATE TABLE IF NOT EXISTS Zuweisung
(
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    teilnehmer_id INTEGER NOT NULL,
    vortrag_id    INTEGER NOT NULL,
    eventslot_id  INTEGER NOT NULL,
    raum_id       INTEGER NOT NULL,
    FOREIGN KEY (teilnehmer_id) REFERENCES User (id) ON DELETE CASCADE,
    FOREIGN KEY (vortrag_id) REFERENCES Vortrag (id) ON DELETE CASCADE,
    FOREIGN KEY (eventslot_id) REFERENCES EventSlot (id) ON DELETE CASCADE,
    FOREIGN KEY (raum_id) REFERENCES Raum (id) ON DELETE CASCADE
);

-- 8. Prioritäten & Verfügbarkeit
CREATE TABLE IF NOT EXISTS Prioritaet
(
    id INTEGER PRIMARY KEY AUTOINCREMENT, teilnehmer_id INTEGER, vortrag_id INTEGER, prioWert INTEGER, lastUpdated TIMESTAMP, version BIGINT NOT NULL DEFAULT 1,
    FOREIGN KEY (teilnehmer_id) REFERENCES User (id) ON DELETE CASCADE,
    FOREIGN KEY (vortrag_id) REFERENCES Vortrag (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS Verfuegbarkeit
(
    id INTEGER PRIMARY KEY AUTOINCREMENT, referent_id INTEGER, slot_id INTEGER, isAvailable BOOLEAN DEFAULT 1,
    FOREIGN KEY (referent_id) REFERENCES User (id) ON DELETE CASCADE,
    FOREIGN KEY (slot_id) REFERENCES EventSlot (id) ON DELETE CASCADE
);

-- STAMMDATEN
INSERT INTO User (id, email, password_hash, role, first_name, last_name, is_active, version)
VALUES (1, 'juergenkrey@yahoo.de', '$2a$10$8920ztppz0N29GNOOw1FCuZJqMUIJhybpqw9tKwkS0rR3BmELJIlC', 'ADMIN', 'Jürgen', 'Krey', 1, 1);
INSERT INTO User (id, email, password_hash, role, first_name, last_name, is_active, version)
VALUES (2, 'kathrin.jessen@rks-linz.de', '$2a$10$8920ztppz0N29GNOOw1FCuZJqMUIJhybpqw9tKwkS0rR3BmELJIlC', 'ADMIN', 'Kathrin', 'Jessen', 1, 1);

INSERT INTO Veranstaltung (id, name, beginntAm, endetAm, ort, organisator_id, version)
VALUES (1, 'Berufsorientierungstag 2024', '2024-09-01 09:00', '2024-09-01 17:00', 'RKS Linz', 1, 1);

INSERT INTO User (id, email, password_hash, role, first_name, last_name, is_active, biography, job_role, organisation, slogan, veranstaltung_id, version)
VALUES (3, 'speaker@rks-linz.de', '$2a$10$1GwWucNOnG9sePgSnEhLOeaBEVZuib1OLhWlHwQhDc8.fto5o3VCm', 'REFERENT', 'Jens', 'Riewa', 1, 'News', 'Moderator', 'ARD', 'Tagesschau', 1, 1);

INSERT INTO User (id, email, password_hash, role, first_name, last_name, is_active, gruppe, veranstaltung_id, version)
VALUES (4, 'schueler@rks-linz.de', '$2a$10$dCM9aDr8FRJRQKXI9o4IE.SWgxmGsMRSbtFtTJKrOBd51OWJfSUdi', 'TEILNEHMER', 'Peter', 'Schmitz', 1, '10a', 1, 1);
