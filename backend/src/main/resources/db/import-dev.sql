-- SUPER ADMIN
INSERT INTO Nutzer (id, login_name, email, password_hash, role, first_name, last_name, is_active, version)
VALUES (0, 'juergenkrey', 'juergenkrey@yahoo.de', '$2a$12$.TNCv.nyCvC7kIHObHbvqu7Z6AJ0Uf/sn3HS5qSPl/4H.Et1K8lri', 'ADMIN', 'Jürgen',
        'Krey', true, 0);

-- DEMO Referent
-- INSERT INTO Nutzer (login_name, email, password_hash, role, first_name, last_name, is_active, version)
-- VALUES ('r.ref', 'r.ref@yahoo.de', '$2a$12$.TNCv.nyCvC7kIHObHbvqu7Z6AJ0Uf/sn3HS5qSPl/4H.Et1K8lri', 'REFERENT',
--         'Ref',
--         'REFERENT', true, 1);

-- Demo Teilnehmer
-- INSERT INTO Nutzer (login_name, email, password_hash, role, first_name, last_name, is_active, version)
-- VALUES ('t.tn', 't.tn@yahoo.de', '$2a$12$.TNCv.nyCvC7kIHObHbvqu7Z6AJ0Uf/sn3HS5qSPl/4H.Et1K8lri', 'TEILNEHMER',
--         'Theo',
--         'TEILNEHMER', true, 1);

