-- SUPER ADMIN
INSERT INTO "User" (email, password_hash, role, first_name, last_name, is_active, version)
VALUES ('juergenkrey@yahoo.de', '$2a$10$8920ztppz0N29GNOOw1FCuZJqMUIJhybpqw9tKwkS0rR3BmELJIlC', 'ADMIN', 'Jürgen',
        'Krey', TRUE, 1);

--- more admins
INSERT INTO "User" (email, password_hash, role, first_name, last_name, is_active, version)
VALUES ('kathrin.jessen@rks-linz.de', '$2a$10$8920ztppz0N29GNOOw1FCuZJqMUIJhybpqw9tKwkS0rR3BmELJIlC', 'ADMIN',
        'Kathrin',
        'Jessen', TRUE, 1);

-- DEMO Referent
INSERT INTO "User" (email, password_hash, role, first_name, last_name, is_active, version)
VALUES ('r.ref@yahoo.de', '$2a$10$8920ztppz0N29GNOOw1FCuZJqMUIJhybpqw9tKwkS0rR3BmELJIlC', 'REFERENT',
        'Ref',
        'Referent', TRUE, 1);

-- Demo Teilnehmer
INSERT INTO "User" (email, password_hash, role, first_name, last_name, is_active, version)
VALUES ('t.tn@yahoo.de', '$2a$10$8920ztppz0N29GNOOw1FCuZJqMUIJhybpqw9tKwkS0rR3BmELJIlC', 'TEILNEHMER',
        'Theo',
        'Teilnehmer', TRUE, 1);

INSERT INTO Gebaeude(name, typ, strasse, hausnummer, postleitzahl, ort, version)
VALUES ('RKS Linz', 'SCHULE', 'Im Rosengarten', '2', '53545', 'Linz am Rhein', 1);

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
