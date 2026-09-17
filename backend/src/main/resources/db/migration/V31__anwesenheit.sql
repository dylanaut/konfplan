-- Anwesenheitserfassung per QR-Code-Scan (siehe #735): ein Teilnehmer kann sich fuer einen Slot
-- als anwesend registrieren, unabhaengig davon, ob er dort geplant war - der Abgleich mit dem
-- geplanten Zuweisungs-Stand erfolgt erst bei der Auswertung.

create table Anwesenheit
(
    id               bigint      not null,
    version          bigint      not null,
    teilnehmer_id    bigint      not null,
    veranstaltung_id bigint      not null,
    slot_id          bigint      not null,
    raum_id          bigint      not null,
    eingechecktAm    varchar(255),
    primary key (id)
);

alter table Anwesenheit
    add constraint UK_anwesenheit_teilnehmer_slot unique (teilnehmer_id, slot_id);

alter table Anwesenheit
    add constraint FK_anwesenheit_teilnehmer
        foreign key (teilnehmer_id) references Nutzer;

alter table Anwesenheit
    add constraint FK_anwesenheit_veranstaltung
        foreign key (veranstaltung_id) references Veranstaltung;

alter table Anwesenheit
    add constraint FK_anwesenheit_slot
        foreign key (slot_id) references Slot;

alter table Anwesenheit
    add constraint FK_anwesenheit_raum
        foreign key (raum_id) references Raum;
