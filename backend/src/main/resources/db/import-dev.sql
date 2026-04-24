-- SUPER ADMIN
INSERT INTO Nutzer (email, password_hash, role, first_name, last_name, is_active, version)
VALUES ('juergenkrey@yahoo.de', '$2a$10$8920ztppz0N29GNOOw1FCuZJqMUIJhybpqw9tKwkS0rR3BmELJIlC', 'ADMIN', 'Jürgen',
        'Krey', TRUE, 1);

-- DEMO Referent
INSERT INTO Nutzer (email, password_hash, role, first_name, last_name, is_active, version)
VALUES ('r.ref@yahoo.de', '$2a$10$8920ztppz0N29GNOOw1FCuZJqMUIJhybpqw9tKwkS0rR3BmELJIlC', 'REFERENT',
        'Ref',
        'REFERENT', TRUE, 1);

-- Demo Teilnehmer
INSERT INTO Nutzer (email, password_hash, role, first_name, last_name, is_active, version)
VALUES ('t.tn@yahoo.de', '$2a$10$8920ztppz0N29GNOOw1FCuZJqMUIJhybpqw9tKwkS0rR3BmELJIlC', 'TEILNEHMER',
        'Theo',
        'TEILNEHMER', TRUE, 1);

