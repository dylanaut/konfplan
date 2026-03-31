-- Flyway Migration für SQLite - BIGINT für Hibernate Kompatibilität

DROP TABLE IF EXISTS User;
DROP TABLE IF EXISTS User_SEQ;
DROP TABLE IF EXISTS EventSlot;
DROP TABLE IF EXISTS EventSlot_SEQ;
DROP TABLE IF EXISTS Talk;
DROP TABLE IF EXISTS Talk_SEQ;
DROP TABLE IF EXISTS Priority;
DROP TABLE IF EXISTS Priority_SEQ;
DROP TABLE IF EXISTS SpeakerAvailability;
DROP TABLE IF EXISTS SpeakerAvailability_SEQ;


-- 1. User Tabelle
CREATE TABLE IF NOT EXISTS User
(
    id                 BIGINT PRIMARY KEY,
    email              VARCHAR(255) UNIQUE NOT NULL,
    password_hash      VARCHAR(255)        NOT NULL,
    role               VARCHAR(50)         NOT NULL,
    first_name         VARCHAR(100),
    last_name          VARCHAR(100),
    organization       VARCHAR(255),
    job_role           VARCHAR(100),
    version            BIGINT              NOT NULL DEFAULT 1,
    is_active          BOOLEAN                      DEFAULT 1,
    reset_token        VARCHAR(255),
    reset_token_expiry TIMESTAMP
);

-- 2. Zeit-Slots
CREATE TABLE IF NOT EXISTS EventSlot
(
    id          BIGINT PRIMARY KEY,
    startTime   TIMESTAMP NOT NULL,
    endTime     TIMESTAMP NOT NULL,
    description VARCHAR(255)
);

-- 3. Vorträge
CREATE TABLE IF NOT EXISTS Talk
(
    id                     BIGINT PRIMARY KEY,
    speaker_id             BIGINT,
    title                  VARCHAR(255),
    abstractText           TEXT,
    targetAudience         VARCHAR(255),
    readyToRepeat          BOOLEAN         DEFAULT 0,
    maxPossibleRepetitions INTEGER         DEFAULT 1,
    maxRepetitions         INTEGER         DEFAULT 1,
    version                BIGINT NOT NULL DEFAULT 1,
    FOREIGN KEY (speaker_id) REFERENCES User (id) ON DELETE CASCADE
);

-- 4. Prioritäten
CREATE TABLE IF NOT EXISTS Priority
(
    id             BIGINT PRIMARY KEY,
    participant_id BIGINT,
    talk_id        BIGINT,
    priorityValue  INTEGER,
    lastUpdated    TIMESTAMP,
    version        BIGINT NOT NULL DEFAULT 1,
    FOREIGN KEY (participant_id) REFERENCES User (id) ON DELETE CASCADE,
    FOREIGN KEY (talk_id) REFERENCES Talk (id) ON DELETE CASCADE
);

-- 5. Verfügbarkeit
CREATE TABLE IF NOT EXISTS SpeakerAvailability
(
    id          BIGINT PRIMARY KEY,
    speaker_id  BIGINT,
    slot_id     BIGINT,
    isAvailable BOOLEAN DEFAULT 1,
    FOREIGN KEY (speaker_id) REFERENCES User (id) ON DELETE CASCADE,
    FOREIGN KEY (slot_id) REFERENCES EventSlot (id) ON DELETE CASCADE
);

-- Sequenz-Tabellen für Hibernate Panache (BIGINT für Hibernate-Check)
CREATE TABLE IF NOT EXISTS EventSlot_SEQ
(
    next_val BIGINT
);
CREATE TABLE IF NOT EXISTS Priority_SEQ
(
    next_val BIGINT
);
CREATE TABLE IF NOT EXISTS SpeakerAvailability_SEQ
(
    next_val BIGINT
);
CREATE TABLE IF NOT EXISTS Talk_SEQ
(
    next_val BIGINT
);
CREATE TABLE IF NOT EXISTS User_SEQ
(
    next_val BIGINT
);

INSERT INTO EventSlot_SEQ (next_val)
VALUES (1);
INSERT INTO Priority_SEQ (next_val)
VALUES (1);
INSERT INTO SpeakerAvailability_SEQ (next_val)
VALUES (1);
INSERT INTO Talk_SEQ (next_val)
VALUES (1);
INSERT INTO User_SEQ (next_val)
VALUES (1);

-- STAMMDATEN
INSERT INTO User (id, email, password_hash, role, first_name, last_name, is_active, version) -- admin1..3
VALUES (1, 'kathrin.jessen@rks-linz.de', '$2a$10$8920ztppz0N29GNOOw1FCuZJqMUIJhybpqw9tKwkS0rR3BmELJIlC', 'ADMIN',
        'Kathrin', 'Jessen', 1, 1);

INSERT INTO User (id, email, password_hash, role, first_name, last_name, is_active, version) -- admin1..3
VALUES (2, 'juergenkrey@yahoo.de', '$2a$10$8920ztppz0N29GNOOw1FCuZJqMUIJhybpqw9tKwkS0rR3BmELJIlC', 'ADMIN', 'Jürgen',
        'Krey', 1, 1);

INSERT INTO User (id, email, password_hash, role, first_name, last_name, is_active, version) -- speaker1..3
VALUES (3, 'speaker@rks-linz.de', '$2a$10$1GwWucNOnG9sePgSnEhLOeaBEVZuib1OLhWlHwQhDc8.fto5o3VCm', 'SPEAKER', 'Jens',
        'Riewa', 1, 1);

INSERT INTO User (id, email, password_hash, role, first_name, last_name, is_active, version) -- schueler1..3
VALUES (4, 'schueler@rks-linz.de', '$2a$10$dCM9aDr8FRJRQKXI9o4IE.SWgxmGsMRSbtFtTJKrOBd51OWJfSUdi', 'PARTICIPANT', 'Peter',
        'Schmitz', 1, 1);

INSERT INTO User_SEQ (next_val)
VALUES (5);