create table Nachricht
(
    id             bigint       not null,
    version        bigint       not null,
    empfaenger_id  bigint,
    titel          varchar(255) not null,
    inhalt         TEXT         not null,
    kategorie      varchar(255) not null check ((kategorie in ('VORTRAG_ZURUECKGEZOGEN'))),
    erstelltAm     varchar(255) not null,
    gelesenAm      varchar(255),
    veranstaltungId bigint,
    primary key (id)
);
