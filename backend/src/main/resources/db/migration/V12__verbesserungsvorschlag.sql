create table Verbesserungsvorschlag
(
    id          bigint       not null,
    version     bigint       not null,
    titel       varchar(255) not null,
    beschreibung TEXT        not null,
    erstelltAm  varchar(255) not null,
    ersteller_id bigint,
    status      varchar(255) not null check ((status in ('OFFEN', 'ERLEDIGT'))),
    primary key (id)
);
