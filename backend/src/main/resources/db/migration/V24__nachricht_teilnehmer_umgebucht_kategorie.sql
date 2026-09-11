-- Manuelle Einzel-Umbuchung eines Teilnehmers in einen anderen Wahlvortrag im selben Zeitslot
-- (siehe UmplanungService#teilnehmerUmbuchen) benachrichtigt den betroffenen Teilnehmer über eine
-- neue Nachrichtenkategorie - erweitert den harten CHECK-Constraint aus V15__nachricht.sql
-- (zuletzt erweitert in V21).
alter table Nachricht
    drop constraint nachricht_kategorie_check;

alter table Nachricht
    add constraint nachricht_kategorie_check check (kategorie in ('VORTRAG_ZURUECKGEZOGEN', 'ORGANISATOR_NACHRICHT', 'VORTRAG_AUSGEFALLEN', 'TEILNEHMER_UMGEBUCHT'));
