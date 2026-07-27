-- SUPER ADMIN
INSERT INTO Nutzer (id, email, password_hash, role, first_name, last_name, is_active, version)
VALUES (0, 'juergenkrey@yahoo.de', '$2a$12$.TNCv.nyCvC7kIHObHbvqu7Z6AJ0Uf/sn3HS5qSPl/4H.Et1K8lri', 'ADMIN', 'Jürgen',
        'Krey', 1, 0);

-- DEMO Referent
-- INSERT INTO Nutzer (email, password_hash, role, first_name, last_name, is_active, version)
-- VALUES ('r.ref@yahoo.de', '$2a$12$.TNCv.nyCvC7kIHObHbvqu7Z6AJ0Uf/sn3HS5qSPl/4H.Et1K8lri', 'REFERENT',
--         'Ref',
--         'REFERENT', 1, 1);

-- Demo Teilnehmer
-- INSERT INTO Nutzer (email, password_hash, role, first_name, last_name, is_active, version)
-- VALUES ('t.tn@yahoo.de', '$2a$12$.TNCv.nyCvC7kIHObHbvqu7Z6AJ0Uf/sn3HS5qSPl/4H.Et1K8lri', 'TEILNEHMER',
--         'Theo',
--         'TEILNEHMER', 1, 1);

