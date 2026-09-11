-- Bereinigt doppelte Prioritaet-Zeilen (gleicher teilnehmer_id + vortrag_id), die durch eine
-- Race Condition im find-or-create von PrioritaetService#savePrioritaeten/#updateSinglePrioritaet
-- entstehen konnten (siehe V23__prioritaet_unique_teilnehmer_vortrag.sql). MUSS vor dem Deployment
-- der Version mit dieser Migration einmalig manuell in Produktion ausgefuehrt werden, sonst
-- schlaegt die Migration beim Start fehl.
--
-- Vorher ein Backup ziehen (Organisator-Dashboard -> "Datenbank-Export" oder pg_dump).
--
-- Aufruf: psql -U postgres -d konfplan -f cleanup-duplicate-prioritaeten.sql
-- (oder interaktiv: docker compose exec postgres psql -U postgres -d konfplan, dann Inhalt
-- Schritt fuer Schritt einfuegen)

-- 1. Vorschau: welche Duplikate gibt es, mit welchen (ggf. abweichenden) Werten?
SELECT teilnehmer_id, vortrag_id, count(*) AS anzahl,
       array_agg(id ORDER BY id)       AS ids,
       array_agg(priowert ORDER BY id) AS werte
FROM prioritaet
GROUP BY teilnehmer_id, vortrag_id
HAVING count(*) > 1;

-- 2. Bereinigung in einer Transaktion - erst nach Pruefung der obigen Vorschau COMMIT ausfuehren.
BEGIN;

-- Behaelt pro (teilnehmer_id, vortrag_id) nur die Zeile mit der hoechsten id (jeweils die zuletzt
-- geschriebene); loescht alle aelteren Duplikate. Bei identischen Werten (haeufigster Fall,
-- z.B. Doppelklick mit demselben abgesendeten Wert) ist das ohnehin gleichwertig; bei abweichenden
-- Werten gewinnt der zuletzt gespeicherte - das entspricht dem Verhalten, das der Teilnehmer beim
-- naechsten Speichern ohnehin gesehen haette.
DELETE FROM prioritaet a
USING prioritaet b
WHERE a.teilnehmer_id = b.teilnehmer_id
  AND a.vortrag_id = b.vortrag_id
  AND a.id < b.id;

-- 3. Kontrolle: muss 0 Zeilen liefern, sonst NICHT committen.
SELECT teilnehmer_id, vortrag_id, count(*)
FROM prioritaet
GROUP BY teilnehmer_id, vortrag_id
HAVING count(*) > 1;

-- Bei 0 Zeilen in Schritt 3:
COMMIT;
-- Andernfalls stattdessen:
-- ROLLBACK;
