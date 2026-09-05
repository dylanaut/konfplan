-- Organisatoren können jetzt manuell Nachrichten an ausgewählte Nutzer einer Veranstaltung
-- senden (siehe Issue #463) - bisher waren alle Nachrichten systemgeneriert ("System" als
-- impliziter Absender). Neue Spalte fuer den Absender (JWT-Principal-Name, analog
-- Planungsergebnis.ersteller) und ein neuer erlaubter Kategoriewert fuer den harten
-- CHECK-Constraint aus V15__nachricht.sql.
alter table Nachricht
    add column absender varchar(255);

alter table Nachricht
    drop constraint nachricht_kategorie_check;

alter table Nachricht
    add constraint nachricht_kategorie_check check (kategorie in ('VORTRAG_ZURUECKGEZOGEN', 'ORGANISATOR_NACHRICHT'));
