-- Ob Teilnehmer ihre persönlichen Verfügbarkeiten im Teilnehmer-Dashboard selbst ändern dürfen.
-- Standardmäßig deaktiviert; nur Organisatoren (Admins) können dies pro Veranstaltung freischalten.
alter table Veranstaltung
    add column teilnehmerAendernVerfuegbarkeit boolean not null default false;
