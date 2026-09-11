-- Wird ein Teilnehmer bei bereits veröffentlichtem Plan als nicht mehr verfügbar markiert (z.B.
-- Krankmeldung), gibt UmplanungService#freigebenBeiNichtVerfuegbarkeit dessen Sitzplätze in den
-- betroffenen Zeitslots frei und benachrichtigt die jeweiligen Referenten über eine neue
-- Nachrichtenkategorie - erweitert den harten CHECK-Constraint aus V15__nachricht.sql (zuletzt
-- erweitert in V24).
alter table Nachricht
    drop constraint nachricht_kategorie_check;

alter table Nachricht
    add constraint nachricht_kategorie_check check (kategorie in ('VORTRAG_ZURUECKGEZOGEN', 'ORGANISATOR_NACHRICHT', 'VORTRAG_AUSGEFALLEN', 'TEILNEHMER_UMGEBUCHT', 'TEILNEHMER_NICHT_VERFUEGBAR'));
