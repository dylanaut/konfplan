-- Kurzfristige Umplanung bei Vortragsausfall (siehe UmplanungService) benachrichtigt betroffene
-- Teilnehmer, den Referenten und ggf. die Organisatoren über eine neue Nachrichtenkategorie -
-- erweitert den harten CHECK-Constraint aus V15__nachricht.sql (zuletzt erweitert in V19).
alter table Nachricht
    drop constraint nachricht_kategorie_check;

alter table Nachricht
    add constraint nachricht_kategorie_check check (kategorie in ('VORTRAG_ZURUECKGEZOGEN', 'ORGANISATOR_NACHRICHT', 'VORTRAG_AUSGEFALLEN'));
