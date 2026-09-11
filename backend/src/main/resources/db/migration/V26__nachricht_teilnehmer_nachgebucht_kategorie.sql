-- Wird ein Teilnehmer bei bereits veröffentlichtem Plan wieder als verfügbar markiert und vom
-- Organisator in einen passenden Wahlvortrag nachgebucht (siehe
-- UmplanungService#teilnehmerNachbuchen), benachrichtigt das den Teilnehmer über eine neue
-- Nachrichtenkategorie - erweitert den harten CHECK-Constraint aus V15__nachricht.sql (zuletzt
-- erweitert in V25).
alter table Nachricht
    drop constraint nachricht_kategorie_check;

alter table Nachricht
    add constraint nachricht_kategorie_check check (kategorie in ('VORTRAG_ZURUECKGEZOGEN', 'ORGANISATOR_NACHRICHT', 'VORTRAG_AUSGEFALLEN', 'TEILNEHMER_UMGEBUCHT', 'TEILNEHMER_NICHT_VERFUEGBAR', 'TEILNEHMER_NACHGEBUCHT'));
