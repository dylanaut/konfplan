-- SUPER ADMIN
-- Hinweis: dieser per rohem SQL-Insert angelegte Nutzer hat kein Keycloak-Gegenstueck
-- (keycloak_id bleibt NULL) und kann sich daher nicht einloggen - fuer echtes lokales Login
-- die per CSV importierten Nutzer aus dem "medium"-Dataset verwenden (siehe DevDataInitService).
INSERT INTO Nutzer (id, login_name, email, role, first_name, last_name, is_active, version)
VALUES (0, 'juergenkrey', 'juergenkrey@yahoo.de', 'ADMIN', 'Jürgen',
        'Krey', true, 0);

-- DEMO Referent
-- INSERT INTO Nutzer (login_name, email, role, first_name, last_name, is_active, version)
-- VALUES ('r.ref', 'r.ref@yahoo.de', 'REFERENT',
--         'Ref',
--         'REFERENT', true, 1);

-- Demo Teilnehmer
-- INSERT INTO Nutzer (login_name, email, role, first_name, last_name, is_active, version)
-- VALUES ('t.tn', 't.tn@yahoo.de', 'TEILNEHMER',
--         'Theo',
--         'TEILNEHMER', true, 1);

