-- Verhindert, dass fuer denselben Teilnehmer+Vortrag zwei Prioritaet-Zeilen entstehen koennen.
-- Bisher gab es hier gar keine Constraint - ein ungesichertes find-or-create in
-- PrioritaetService#savePrioritaeten/#updateSinglePrioritaet erlaubte bei zwei fast gleichzeitigen
-- Requests (z.B. Doppelklick auf "Speichern") eine Race Condition, bei der beide Requests je eine
-- neue Zeile anlegten statt die des jeweils anderen zu aktualisieren. Das blieb unbemerkt liegen,
-- bis Collectors.toMap beim Lesen (getVortragPrioritaeten) ohne Merge-Funktion mit
-- "Duplicate key"-IllegalStateException abstuerzte.
--
-- WICHTIG: Falls in Produktion bereits Duplikate existieren (z.B. das oben beschriebene Symptom
-- bereits aufgetreten ist), schlaegt diese Migration fehl, bis sie per
-- deploy/cleanup-duplicate-prioritaeten.sql bereinigt wurden - das muss VOR dem Deployment dieser
-- Version manuell in Produktion ausgefuehrt werden.
alter table prioritaet
    add constraint UK_prioritaet_teilnehmer_vortrag unique (teilnehmer_id, vortrag_id);
