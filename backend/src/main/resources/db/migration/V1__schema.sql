create sequence id_sequence start with 1 increment by 50;

create table Gebaeude
(
    id           bigint       not null,
    version      bigint       not null,
    hausnummer   varchar(255),
    name         varchar(255) not null unique,
    ort          varchar(255) not null,
    postleitzahl varchar(255) not null,
    strasse      varchar(255) not null,
    typ          varchar(255) not null check ((typ in ('SCHULE', 'KINO', 'SPORTHALLE', 'SAAL', 'EXTERN'))),
    primary key (id)
);

create table Nutzer
(
    is_active                 boolean,
    email_change_token_expiry timestamp(6),
    id                        bigint      not null,
    reset_token_expiry        timestamp(6),
    version                   bigint      not null,
    role                      varchar(31) not null check ((role in ('REFERENT', 'ADMIN', 'TEILNEHMER'))),
    biography                 TEXT,
    email                     varchar(255) unique,
    email_change_token        varchar(255),
    first_name                varchar(255),
    job_role                  varchar(255),
    last_name                 varchar(255),
    new_email                 varchar(255),
    organisation              varchar(255),
    password_hash             varchar(255),
    reset_token               varchar(255),
    slogan                    varchar(255),
    primary key (id),
    check (role <> 'REFERENT' or (is_active is not null)),
    check (role <> 'ADMIN' or (is_active is not null)),
    check (role <> 'TEILNEHMER' or (is_active is not null))
);

create table Nutzer_Veranstaltung
(
    nutzer_id        bigint not null,
    veranstaltung_id bigint not null,
    primary key (nutzer_id, veranstaltung_id)
);

create table NutzerVerfuegbarkeit
(
    nutzer_id          bigint not null,
    veranstaltung_id   bigint not null,
    verfuegbareSlotIds bigint array,
    primary key (nutzer_id, veranstaltung_id)
);

create table Planungsergebnis
(
    id               bigint not null,
    veranstaltung_id bigint not null unique,
    version          bigint not null,
    jsonErgebnis     oid    not null,
    solverConfig     jsonb,
    primary key (id)
);

create table Prioritaet
(
    prioWert      integer not null check ((prioWert >= 0) and (prioWert <= 10)),
    id            bigint  not null,
    teilnehmer_id bigint,
    vortrag_id    bigint,
    primary key (id)
);

create table Protokoll
(
    id               bigint not null,
    referenzId       bigint,
    veranstaltungId  bigint,
    akteur           varchar(255) not null,
    details          TEXT,
    ereignis         varchar(255) not null,
    kategorie        varchar(255) not null check ((kategorie in
                                                    ('LOGIN', 'PLANUNG', 'STAMMDATEN', 'SECURITY', 'SYSTEM',
                                                     'VERANSTALTUNG', 'NUTZER', 'VORTRAEGE', 'RAUM', 'GEBAEUDE'))),
    zeitpunkt        varchar(255) not null,
    primary key (id)
);

create table Raum
(
    kapazitaet  integer not null,
    gebaeude_id bigint,
    id          bigint  not null,
    version     bigint  not null,
    etage       varchar(255),
    name        varchar(255) not null,
    primary key (id)
);

create table RaumVerfuegbarkeit
(
    raum_id            bigint not null,
    veranstaltung_id   bigint not null,
    verfuegbareSlotIds bigint array,
    primary key (raum_id, veranstaltung_id)
);

create table Slot
(
    id               bigint not null,
    veranstaltung_id bigint not null,
    version          bigint not null,
    description      varchar(255),
    endTime          varchar(255),
    startTime        varchar(255),
    primary key (id)
);

create table teilnehmer_gruppen
(
    teilnehmer_id bigint not null,
    gruppen       varchar(255)
);

create table Veranstaltung
(
    id                 bigint       not null,
    version            bigint       not null,
    beginntAm          varchar(255) not null,
    deadlineReferenten varchar(255),
    deadlineTeilnehmer varchar(255),
    endetAm            varchar(255),
    logo               varchar(255),
    logo_link          varchar(255),
    name               varchar(255) not null,
    primary key (id),
    unique (name, beginntAm)
);

create table Veranstaltung_Gebaeude
(
    gebaeude_id      bigint not null,
    veranstaltung_id bigint not null,
    primary key (gebaeude_id, veranstaltung_id)
);

create table veranstaltung_gruppen
(
    veranstaltung_id bigint not null,
    gruppen          varchar(255)
);

create table Vortrag
(
    maxWiederholungen integer,
    wiederholbar      boolean,
    id                bigint      not null,
    pflichtraum_id    bigint,
    pflichtslot_id    bigint,
    referent_id       bigint      not null,
    veranstaltung_id  bigint      not null,
    version           bigint      not null,
    vortrag_typ       varchar(31) not null check ((vortrag_typ in ('WAHL', 'PFLICHT'))),
    berufsfeld        varchar(100) check ((berufsfeld in
                                           ('LAND_FORST_TIERWIRTSCHAFT_UND_GARTENBAU',
                                            'ROHSTOFFGEWINNUNG_PRODUKTION_UND_FERTIGUNG',
                                            'BAU_ARCHITEKTUR_VERMESSUNG_UND_GEBAEUDETECHNIK',
                                            'NATURWISSENSCHAFT_GEOGRAFIE_UND_INFORMATIK',
                                            'VERKEHR_LOGISTIK_SCHUTZ_UND_SICHERHEIT', 'ELEKTROTECHNIK',
                                            'METALL_MASCHINEN_UND_FAHRZEUGBAU', 'IT_UND_COMPUTER',
                                            'CHEMIE_KUNSTSTOFF_GLAS_TEXTIL_UND_HOLZ',
                                            'GASTRONOMIE_LEBENSMITTEL_UND_HAUSWIRTSCHAFT', 'GESUNDHEIT',
                                            'SOZIALES_PAEDAGOGIK_UND_THEOLOGIE',
                                            'KREATIVBERUFE_MEDIEN_UND_GESTALTUNG',
                                            'WIRTSCHAFT_VERWALTUNG_RECHT_UND_GESELLSCHAFT',
                                            'UNTERNEHMENSFUEHRUNG_ORGANISATION_EINKAUF_VERTRIEB_UND_MARKETING',
                                            'TOURISMUS_SPORT_UND_KULTUR'))),
    titel             varchar(120) not null,
    ausstattung       varchar(255),
    inhalt            TEXT,
    pflichtgruppe     varchar(255),
    primary key (id),
    check (vortrag_typ <> 'WAHL' or (maxWiederholungen is not null and wiederholbar is not null)),
    check (vortrag_typ <> 'PFLICHT' or (pflichtraum_id is not null and pflichtslot_id is not null))
);

create table VortragVerfuegbarkeit
(
    veranstaltung_id   bigint not null,
    vortrag_id         bigint not null,
    verfuegbareSlotIds bigint array,
    primary key (veranstaltung_id, vortrag_id)
);

alter table if exists Nutzer_Veranstaltung
    add constraint FKkhrm6bthspukyelg6m0hcxrn9
        foreign key (veranstaltung_id)
            references Veranstaltung;

alter table if exists Nutzer_Veranstaltung
    add constraint FK3ncjyr1g518d6cbp5moom636t
        foreign key (nutzer_id)
            references Nutzer;

alter table if exists Planungsergebnis
    add constraint FKm4eiuxbl64ox74y069kljc48u
        foreign key (veranstaltung_id)
            references Veranstaltung;

alter table if exists Prioritaet
    add constraint FKrgn2iupj4ndbav3fl0qt6uqyu
        foreign key (teilnehmer_id)
            references Nutzer;

alter table if exists Prioritaet
    add constraint FKje3bphsr0jtft452j2165u15x
        foreign key (vortrag_id)
            references Vortrag;

alter table if exists Raum
    add constraint FKp4omtwq0ahcw4ru0ji27xh3fi
        foreign key (gebaeude_id)
            references Gebaeude;

alter table if exists Slot
    add constraint FKgxuv9ue09xmug30sjtfteogiu
        foreign key (veranstaltung_id)
            references Veranstaltung;

alter table if exists teilnehmer_gruppen
    add constraint FKar4fnbwb9xxgbr97ym1qf4v8g
        foreign key (teilnehmer_id)
            references Nutzer;

alter table if exists Veranstaltung_Gebaeude
    add constraint FKb20732ctgp9axyb6mgej78yc8
        foreign key (gebaeude_id)
            references Gebaeude;

alter table if exists Veranstaltung_Gebaeude
    add constraint FKfgsbdf30p9h55k450oi713tnj
        foreign key (veranstaltung_id)
            references Veranstaltung;

alter table if exists veranstaltung_gruppen
    add constraint FK88tswehpfqg40yp9jqh2p30ui
        foreign key (veranstaltung_id)
            references Veranstaltung;

alter table if exists Vortrag
    add constraint FKpe40i38wpnx4vssrjevk2umsw
        foreign key (referent_id)
            references Nutzer;

alter table if exists Vortrag
    add constraint FKce0t66m2ms7l9rsuwllfsk6v5
        foreign key (veranstaltung_id)
            references Veranstaltung;

alter table if exists Vortrag
    add constraint FKqf00utmxmbkfctdn5l3k2hltx
        foreign key (pflichtraum_id)
            references Raum;

alter table if exists Vortrag
    add constraint FKbjrlyq9pa1hh7wdjwsk27filf
        foreign key (pflichtslot_id)
            references Slot;
