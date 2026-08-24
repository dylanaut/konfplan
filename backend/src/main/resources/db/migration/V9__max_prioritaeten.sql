-- Optionale, pro Veranstaltung konfigurierbare Obergrenze für die Anzahl der von einem
-- Teilnehmer insgesamt vergebbaren (nicht-null) Prioritaeten. NULL = keine Beschraenkung.
alter table Veranstaltung
    add column maxPrioritaeten integer;
