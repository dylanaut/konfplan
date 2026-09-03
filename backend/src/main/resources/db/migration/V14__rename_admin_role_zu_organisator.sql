-- Benennt die Rolle ADMIN in ORGANISATOR um und fuehrt die neue Rolle ADMINISTRATOR ein
-- (Administrator hat dieselben Rechte wie Organisator, zusaetzlich exklusiv Wartungshinweis
-- und Verzeichnis-Import - siehe WartungshinweisResource/VeranstaltungImportResource).
-- Constraint-Namen wie in V1__schema.sql von Postgres automatisch vergeben (unbenannte
-- inline CHECK-Klauseln), verifiziert per "\d nutzer" gegen die laufende Dev-Datenbank.

ALTER TABLE nutzer DROP CONSTRAINT nutzer_role_check;
ALTER TABLE nutzer DROP CONSTRAINT nutzer_check;
ALTER TABLE nutzer DROP CONSTRAINT nutzer_check1;
ALTER TABLE nutzer DROP CONSTRAINT nutzer_check2;

UPDATE nutzer SET role = 'ORGANISATOR' WHERE role = 'ADMIN';

ALTER TABLE nutzer ADD CONSTRAINT nutzer_role_check
    CHECK (role IN ('REFERENT', 'ORGANISATOR', 'ADMINISTRATOR', 'TEILNEHMER'));
ALTER TABLE nutzer ADD CONSTRAINT nutzer_check CHECK (role <> 'REFERENT' OR (is_active IS NOT NULL));
ALTER TABLE nutzer ADD CONSTRAINT nutzer_check1 CHECK (role <> 'ORGANISATOR' OR (is_active IS NOT NULL));
ALTER TABLE nutzer ADD CONSTRAINT nutzer_check2 CHECK (role <> 'TEILNEHMER' OR (is_active IS NOT NULL));
ALTER TABLE nutzer ADD CONSTRAINT nutzer_check3 CHECK (role <> 'ADMINISTRATOR' OR (is_active IS NOT NULL));
