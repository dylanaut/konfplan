-- CREATE DATABASE IF NOT EXISTS event_planner;
-- USE event_planner;

-- 1. User Tabelle (Admin, Referenten, Teilnehmer)
CREATE TABLE IF NOT EXISTS users
(
    id               INTEGER AUTO_INCREMENT PRIMARY KEY,
    email            VARCHAR(255) UNIQUE NOT NULL,
    password_hash    VARCHAR(255)        NOT NULL,
    role             VARCHAR(50)         NOT NULL,
    firstName        VARCHAR(100),
    lastName         VARCHAR(100),
    organization     VARCHAR(255),
    jobRole          VARCHAR(100),
    version          INTEGER              NOT NULL DEFAULT 1,
    isActive         BOOLEAN                      DEFAULT TRUE,
    resetToken       VARCHAR(255),
    resetTokenExpiry DATETIME
);

-- 2. Zeit-Slots für das Event
CREATE TABLE IF NOT EXISTS event_slots
(
    id          INTEGER AUTO_INCREMENT PRIMARY KEY,
    startTime   DATETIME NOT NULL,
    endTime     DATETIME NOT NULL,
    description VARCHAR(255)
);

-- 3. Vorträge
CREATE TABLE IF NOT EXISTS talks
(
    id                     INTEGER AUTO_INCREMENT PRIMARY KEY,
    speaker_id             INTEGER,
    title                  VARCHAR(255),
    abstractText           TEXT,
    targetAudience         VARCHAR(255),
    willingToRepeat        BOOLEAN         DEFAULT FALSE,
    maxPossibleRepetitions INT             DEFAULT 1,
    version                INTEGER NOT NULL DEFAULT 1,
    FOREIGN KEY (speaker_id) REFERENCES users (id) ON DELETE CASCADE
);

-- 4. Prioritäten (Wahlen der Teilnehmer)
CREATE TABLE IF NOT EXISTS priorities
(
    id             INTEGER AUTO_INCREMENT PRIMARY KEY,
    participant_id INTEGER,
    talk_id        INTEGER,
    priorityValue  INT CHECK (priorityValue BETWEEN 1 AND 10),
    lastUpdated    DATETIME,
    version        INTEGER NOT NULL DEFAULT 1,
    FOREIGN KEY (participant_id) REFERENCES users (id) ON DELETE CASCADE,
    FOREIGN KEY (talk_id) REFERENCES talks (id) ON DELETE CASCADE
);

-- 5. Verfügbarkeit der Referenten
CREATE TABLE IF NOT EXISTS speaker_availabilities
(
    id          INTEGER AUTO_INCREMENT PRIMARY KEY,
    speaker_id  INTEGER,
    slot_id     INTEGER,
    isAvailable BOOLEAN DEFAULT TRUE,
    FOREIGN KEY (speaker_id) REFERENCES users (id) ON DELETE CASCADE,
    FOREIGN KEY (slot_id) REFERENCES event_slots (id) ON DELETE CASCADE
);

-- TESTDATEN
-- Passwort für Admin: 'admin123' (In Produktion gehasht!)
INSERT INTO users (email, password_hash, role, firstName, lastName, isActive, version) -- admin1..3
VALUES ('kathrin.jessen@rks-linz.de', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd00DMxs.TVuHOn2', 'ADMIN',
        'Kathrin', 'Jessen', 1, 1);

INSERT INTO users (email, password_hash, role, firstName, lastName, isActive, version) -- admin1..3
VALUES ('juergenkrey@yahoo.de', '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd00DMxs.TVuHOn2', 'ADMIN', 'Jürgen',
        'Krey', 1, 1);

INSERT INTO users (email, password_hash, role, firstName, lastName, isActive, version) -- speaker1..3
VALUES ('speaker@rks-linz.de', '$2a$12$yTWFQ3peHdJwMqikIRF3fOfbOZoOXAZ8vdEo8GcRFwrsFN2Y/vaGi', 'SPEAKER', 'Jürgen',
        'Krey', 1, 1);

INSERT INTO users (email, password_hash, role, firstName, lastName, isActive, version) -- schueler1..3
VALUES ('schueler@rks-linz.de', '$2a$12$gkyZqcWSL/sc8IB9ZfUBbeBUd.kC98/yGEJp1N.uUGl9nFYtp.xzW', 'USER', 'Jürgen',
        'Krey', 1, 1);

-- Beispiel Slots
INSERT INTO event_slots (startTime, endTime, description)
VALUES ('2024-10-10 09:00:00', '2024-10-10 10:00:00', 'Slot A - Tag 1'),
       ('2024-10-10 10:15:00', '2024-10-10 11:15:00', 'Slot B - Tag 1'),
       ('2024-10-11 09:00:00', '2024-10-11 10:00:00', 'Slot C - Tag 2');