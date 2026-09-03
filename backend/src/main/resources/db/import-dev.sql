-- SUPER ORGANISATOR
-- juergenkrey/juergenkrey@yahoo.de wird jetzt ueber organisatoren.csv im "medium"-Dataset
-- importiert (siehe DevDataInitService) - dabei entsteht auch das Keycloak-Gegenstueck.
-- Ein zusaetzlicher roher SQL-Insert hier wuerde mit demselben login_name kollidieren
-- (unique constraint) und den Import des gesamten Datasets zum Scheitern bringen.

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

