-- Flyway Migration für SQLite - Gebäude- und Raumverwaltung mit Version

DROP TABLE IF EXISTS Veranstaltung_Gebaeude;
DROP TABLE IF EXISTS Zuweisung;
DROP TABLE IF EXISTS Wahlvortrag_EventSlot;
DROP TABLE IF EXISTS Verfuegbarkeit;
DROP TABLE IF EXISTS Prioritaet;
DROP TABLE IF EXISTS Vortrag;
DROP TABLE IF EXISTS Teilnehmer_EventSlot;
DROP TABLE IF EXISTS Raum_EventSlot;
DROP TABLE IF EXISTS Raum;
DROP TABLE IF EXISTS Gebaeude;
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
    logo           VARCHAR(255),
    logo_link      VARCHAR(255),
    organisator_id INTEGER      NOT NULL,
    version        BIGINT       NOT NULL DEFAULT 1,
    UNIQUE (name, beginntAm),
    FOREIGN KEY (organisator_id) REFERENCES User (id)
);

-- 3. Gebaeude
CREATE TABLE IF NOT EXISTS Gebaeude
(
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    name         VARCHAR(255) UNIQUE NOT NULL,
    typ          VARCHAR(50)         NOT NULL,
    strasse      VARCHAR(255)        NOT NULL,
    hausnummer   VARCHAR(20),
    postleitzahl VARCHAR(10)         NOT NULL,
    ort          VARCHAR(255)        NOT NULL,
    version      BIGINT              NOT NULL DEFAULT 1
);

-- 4. Zeit-Slots
CREATE TABLE IF NOT EXISTS EventSlot
(
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    startTime        TIMESTAMP NOT NULL,
    endTime          TIMESTAMP NOT NULL,
    description      VARCHAR(255),
    version          BIGINT    NOT NULL DEFAULT 1,
    veranstaltung_id INTEGER   NOT NULL,
    FOREIGN KEY (veranstaltung_id) REFERENCES Veranstaltung (id) ON DELETE CASCADE
);

-- 5. Räume
CREATE TABLE IF NOT EXISTS Raum
(
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    name        VARCHAR(255) NOT NULL,
    kapazitaet  INTEGER      NOT NULL,
    etage       VARCHAR(100),
    gebaeude_id INTEGER      NOT NULL,
    version     BIGINT       NOT NULL DEFAULT 1,
    FOREIGN KEY (gebaeude_id) REFERENCES Gebaeude (id) ON DELETE CASCADE
);

-- 6. Relationen
CREATE TABLE IF NOT EXISTS Veranstaltung_Gebaeude
(
    veranstaltung_id INTEGER NOT NULL,
    gebaeude_id      INTEGER NOT NULL,
    PRIMARY KEY (veranstaltung_id, gebaeude_id),
    FOREIGN KEY (veranstaltung_id) REFERENCES Veranstaltung (id) ON DELETE CASCADE,
    FOREIGN KEY (gebaeude_id) REFERENCES Gebaeude (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS Raum_EventSlot
(
    raum_id      INTEGER NOT NULL,
    eventslot_id INTEGER NOT NULL,
    PRIMARY KEY (raum_id, eventslot_id),
    FOREIGN KEY (raum_id) REFERENCES Raum (id) ON DELETE CASCADE,
    FOREIGN KEY (eventslot_id) REFERENCES EventSlot (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS Teilnehmer_EventSlot
(
    teilnehmer_id INTEGER NOT NULL,
    eventslot_id  INTEGER NOT NULL,
    PRIMARY KEY (teilnehmer_id, eventslot_id),
    FOREIGN KEY (teilnehmer_id) REFERENCES User (id) ON DELETE CASCADE,
    FOREIGN KEY (eventslot_id) REFERENCES EventSlot (id) ON DELETE CASCADE
);

-- 7. Vorträge
CREATE TABLE IF NOT EXISTS Vortrag
(
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    vortrag_typ       VARCHAR(50)  NOT NULL,
    referent_id       INTEGER      NOT NULL,
    veranstaltung_id  INTEGER      NOT NULL,
    titel             VARCHAR(255) NOT NULL,
    inhalt            TEXT,
    pflichtgruppe     VARCHAR(255),
    version           BIGINT       NOT NULL DEFAULT 1,
    pflichtraum_id    INTEGER,
    pflichtslot_id    INTEGER,
    wiederholbar      BOOLEAN               DEFAULT 0,
    maxWiederholungen INTEGER               DEFAULT 1,
    FOREIGN KEY (referent_id) REFERENCES User (id) ON DELETE CASCADE,
    FOREIGN KEY (veranstaltung_id) REFERENCES Veranstaltung (id) ON DELETE CASCADE,
    FOREIGN KEY (pflichtraum_id) REFERENCES Raum (id),
    FOREIGN KEY (pflichtslot_id) REFERENCES EventSlot (id)
);

CREATE TABLE IF NOT EXISTS Wahlvortrag_EventSlot
(
    vortrag_id   INTEGER NOT NULL,
    eventslot_id INTEGER NOT NULL,
    PRIMARY KEY (vortrag_id, eventslot_id),
    FOREIGN KEY (vortrag_id) REFERENCES Vortrag (id) ON DELETE CASCADE,
    FOREIGN KEY (eventslot_id) REFERENCES EventSlot (id) ON DELETE CASCADE
);

-- 8. Zuweisung
CREATE TABLE IF NOT EXISTS Zuweisung
(
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    teilnehmer_id INTEGER NOT NULL,
    vortrag_id    INTEGER NOT NULL,
    eventslot_id  INTEGER NOT NULL,
    raum_id       INTEGER NOT NULL,
    version       BIGINT  NOT NULL DEFAULT 1,
    FOREIGN KEY (teilnehmer_id) REFERENCES User (id) ON DELETE CASCADE,
    FOREIGN KEY (vortrag_id) REFERENCES Vortrag (id) ON DELETE CASCADE,
    FOREIGN KEY (eventslot_id) REFERENCES EventSlot (id) ON DELETE CASCADE,
    FOREIGN KEY (raum_id) REFERENCES Raum (id) ON DELETE CASCADE
);

-- 9. Prioritäten & Verfügbarkeit
CREATE TABLE IF NOT EXISTS Prioritaet
(
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    teilnehmer_id INTEGER,
    vortrag_id    INTEGER,
    prioWert      INTEGER,
    lastUpdated   TIMESTAMP,
    version       BIGINT NOT NULL DEFAULT 1,
    FOREIGN KEY (teilnehmer_id) REFERENCES User (id) ON DELETE CASCADE,
    FOREIGN KEY (vortrag_id) REFERENCES Vortrag (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS Verfuegbarkeit
(
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id     INTEGER,
    slot_id     INTEGER,
    isAvailable BOOLEAN         DEFAULT 1,
    version     BIGINT NOT NULL DEFAULT 1,
    FOREIGN KEY (user_id) REFERENCES User (id) ON DELETE CASCADE,
    FOREIGN KEY (slot_id) REFERENCES EventSlot (id) ON DELETE CASCADE
);

-- SUPER ADMIN
INSERT INTO User (id, email, password_hash, role, first_name, last_name, is_active, version)
VALUES (1, 'juergenkrey@yahoo.de', '$2a$10$8920ztppz0N29GNOOw1FCuZJqMUIJhybpqw9tKwkS0rR3BmELJIlC', 'ADMIN', 'Jürgen',
        'Krey', 1, 1);

--- more admins
INSERT INTO User (id, email, password_hash, role, first_name, last_name, is_active, version)
VALUES (2, 'kathrin.jessen@rks-linz.de', '$2a$10$8920ztppz0N29GNOOw1FCuZJqMUIJhybpqw9tKwkS0rR3BmELJIlC', 'ADMIN',
        'Kathrin',
        'Jessen', 1, 1);

-- DEMO Referent
INSERT INTO User (id, email, password_hash, role, first_name, last_name, is_active, version)
VALUES (10, 'beau.referent@yahoo.de', '$2a$10$8920ztppz0N29GNOOw1FCuZJqMUIJhybpqw9tKwkS0rR3BmELJIlC', 'REFERENT',
        'Beau',
        'Referent', 1, 1);

-- Demo Teilnehmer
INSERT INTO User (id, email, password_hash, role, first_name, last_name, is_active, version)
VALUES (20, 'beau.teilnehmer@yahoo.de', '$2a$10$8920ztppz0N29GNOOw1FCuZJqMUIJhybpqw9tKwkS0rR3BmELJIlC', 'TEILNEHMER',
        'Beau',
        'Teilnehmer', 1, 1);



INSERT INTO Gebaeude(id, name, typ, strasse, hausnummer, postleitzahl, ort, version)
VALUES (1, 'RKS Linz', 'SCHULE', 'Im Rosengarten', '2', '53545', 'Linz am Rhein', 1);


INSERT INTO Raum(name, kapazitaet, etage, gebaeude_id, version)
VALUES ('B-002', 35, 'EG', 1, 1);

INSERT INTO Raum(name, kapazitaet, etage, gebaeude_id, version)
VALUES ('A-0.12', 27, 'EG', 1, 1);

INSERT INTO Raum(name, kapazitaet, etage, gebaeude_id, version)
VALUES ('A-0.13', 27, 'EG', 1, 1);

INSERT INTO Raum(name, kapazitaet, etage, gebaeude_id, version)
VALUES ('A-1.01', 25, '1.OG', 1, 1);

INSERT INTO Raum(name, kapazitaet, etage, gebaeude_id, version)
VALUES ('A-1.04', 27, '1.OG', 1, 1);

INSERT INTO Raum(name, kapazitaet, etage, gebaeude_id, version)
VALUES ('A-1.07', 27, '1.OG', 1, 1);

INSERT INTO Raum(name, kapazitaet, etage, gebaeude_id, version)
VALUES ('A-2.01', 25, '2.OG', 1, 1);

INSERT INTO Raum(name, kapazitaet, etage, gebaeude_id, version)
VALUES ('A-2.03', 27, '2.OG', 1, 1);

INSERT INTO Raum(name, kapazitaet, etage, gebaeude_id, version)
VALUES ('A-2.04', 27, '2.OG', 1, 1);

INSERT INTO Raum(name, kapazitaet, etage, gebaeude_id, version)
VALUES ('A-2.06', 27, '2.OG', 1, 1);

INSERT INTO Raum(name, kapazitaet, etage, gebaeude_id, version)
VALUES ('V-0.05', 27, 'EG', 1, 1);

INSERT INTO Raum(name, kapazitaet, etage, gebaeude_id, version)
VALUES ('A-2.07', 25, '2.OG', 1, 1);
