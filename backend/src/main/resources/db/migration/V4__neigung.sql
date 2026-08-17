create table teilnehmer_neigungen
(
    teilnehmer_id bigint      not null,
    neigung       varchar(50) not null check ((neigung in
                                                ('EMPATHISCH', 'KOMMUNIKATIV', 'HILFSBEREIT', 'TEAMFAEHIG',
                                                 'DURCHSETZUNGSSTARK', 'ANALYTISCH', 'SORGFAELTIG', 'STRUKTURIERT',
                                                 'LOESUNGSORIENTIERT', 'AUSDAUERND', 'KREATIV', 'NEUGIERIG',
                                                 'IMPROVISATIONSBEREIT', 'VISIONAER', 'PRAGMATISCH', 'BELASTBAR',
                                                 'SELBSTSTAENDIG')))
);

alter table teilnehmer_neigungen
    add constraint FK_teilnehmer_neigungen_teilnehmer
        foreign key (teilnehmer_id)
            references Nutzer;

create table wahlvortrag_neigungen
(
    vortrag_id bigint      not null,
    neigung    varchar(50) not null check ((neigung in
                                             ('EMPATHISCH', 'KOMMUNIKATIV', 'HILFSBEREIT', 'TEAMFAEHIG',
                                              'DURCHSETZUNGSSTARK', 'ANALYTISCH', 'SORGFAELTIG', 'STRUKTURIERT',
                                              'LOESUNGSORIENTIERT', 'AUSDAUERND', 'KREATIV', 'NEUGIERIG',
                                              'IMPROVISATIONSBEREIT', 'VISIONAER', 'PRAGMATISCH', 'BELASTBAR',
                                              'SELBSTSTAENDIG')))
);

alter table wahlvortrag_neigungen
    add constraint FK_wahlvortrag_neigungen_vortrag
        foreign key (vortrag_id)
            references Vortrag;
