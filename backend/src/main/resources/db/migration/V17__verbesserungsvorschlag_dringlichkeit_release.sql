-- Erweitert Verbesserungsvorschlag um Dringlichkeit (Enum) und Release (bei Erstellung
-- automatisch befuellte App-Versionsnummer) und ergaenzt den Status um IN_BEARBEITUNG.
-- Der bestehende status-Check wurde in V12 als einzelne inline-Spaltenklausel angelegt und
-- erhielt daher Postgres' Standardnamen fuer einen Spalten-Check (<tabelle>_<spalte>_check),
-- analog zu nutzer_role_check in V14.

ALTER TABLE verbesserungsvorschlag DROP CONSTRAINT verbesserungsvorschlag_status_check;
ALTER TABLE verbesserungsvorschlag ADD CONSTRAINT verbesserungsvorschlag_status_check
    CHECK (status IN ('OFFEN', 'IN_BEARBEITUNG', 'ERLEDIGT'));

ALTER TABLE verbesserungsvorschlag ADD COLUMN dringlichkeit VARCHAR(255) NOT NULL DEFAULT 'MITTEL'
    CHECK (dringlichkeit IN ('NIEDRIG', 'MITTEL', 'HOCH', 'KRITISCH'));
ALTER TABLE verbesserungsvorschlag ALTER COLUMN dringlichkeit DROP DEFAULT;

ALTER TABLE verbesserungsvorschlag ADD COLUMN release VARCHAR(255) NOT NULL DEFAULT 'unbekannt';
ALTER TABLE verbesserungsvorschlag ALTER COLUMN release DROP DEFAULT;
