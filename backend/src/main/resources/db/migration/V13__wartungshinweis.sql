create table Wartungshinweis
(
    id             bigint not null,
    version        bigint not null,
    startZeitpunkt varchar(255),
    endeZeitpunkt  varchar(255),
    primary key (id)
);
