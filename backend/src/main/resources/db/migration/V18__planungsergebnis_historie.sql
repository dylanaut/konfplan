-- Ermoeglicht mehrere Planungsergebnisse pro Veranstaltung (Historie aller Planungslaeufe, siehe
-- Issue #461) statt bisher genau einem: Unique-Constraint auf veranstaltung_id entfernen (Name
-- variiert je nach Postgres-Version/-Instanz, da inline "unique" in V1__schema.sql erzeugt wurde -
-- daher dynamisch ermitteln statt hart zu kodieren) und neue Spalten fuer Ersteller/Zeitpunkt/
-- Publikationsstatus ergaenzen. Bestehende Zeilen (vor dieser Migration: genau eine je
-- Veranstaltung, sofort fuer alle sichtbar) werden als "publiziert" markiert, damit sich am
-- sichtbaren Verhalten fuer bereits geplante Veranstaltungen durch dieses Update nichts aendert.
DO $$
DECLARE
    unique_constraint_name text;
BEGIN
    SELECT tc.constraint_name INTO unique_constraint_name
    FROM information_schema.table_constraints tc
    JOIN information_schema.key_column_usage kcu
        ON tc.constraint_name = kcu.constraint_name
       AND tc.table_schema = kcu.table_schema
    WHERE tc.table_name = 'planungsergebnis'
      AND tc.constraint_type = 'UNIQUE'
      AND kcu.column_name = 'veranstaltung_id';

    IF unique_constraint_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE planungsergebnis DROP CONSTRAINT %I', unique_constraint_name);
    END IF;
END $$;

alter table Planungsergebnis
    add column ersteller varchar(255),
    add column erstelltAm varchar(255),
    add column publiziert boolean not null default false;

update Planungsergebnis set publiziert = true;
