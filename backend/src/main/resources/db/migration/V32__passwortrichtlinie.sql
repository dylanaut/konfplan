-- Konfigurierbare Passwort-Syntax je Veranstaltung und Rolle (siehe #741): gilt fuer
-- Passwoerter, die ein Organisator/Administrator selbst setzt (Einzel-Reset, ZIP-Bulk-
-- Generierung) - nicht fuer Keycloaks eigenen Self-Service-Flow.

create table Passwortrichtlinie
(
    id                       bigint       not null,
    version                  bigint       not null,
    veranstaltung_id         bigint       not null,
    rolle                    varchar(255) not null,
    minLaenge                integer      not null,
    maxLaenge                integer,
    erfordertGrossbuchstabe  boolean      not null,
    erfordertKleinbuchstabe  boolean      not null,
    erfordertZiffer          boolean      not null,
    erfordertSonderzeichen   boolean      not null,
    nurZiffern               boolean      not null,
    primary key (id)
);

alter table Passwortrichtlinie
    add constraint UK_PASSWORTRICHTLINIE_VERANSTALTUNG_ROLLE unique (veranstaltung_id, rolle);

alter table Passwortrichtlinie
    add constraint FK_passwortrichtlinie_veranstaltung
        foreign key (veranstaltung_id) references Veranstaltung;
