-- Schema fuer die neue Rolle Betrachter (siehe #718): rein lesender Nutzer, der ueber konkrete
-- GruppenkategorieWert-Zuordnungen auf bestimmte Gruppen (z.B. eine Klasse) beschraenkt wird.
-- Kein neues Feld auf Nutzer noetig - SINGLE_TABLE nutzt die bestehende role-Spalte.

create table betrachter_gruppenwert
(
    betrachter_id           bigint not null,
    gruppenkategoriewert_id bigint not null,
    primary key (betrachter_id, gruppenkategoriewert_id)
);

alter table betrachter_gruppenwert
    add constraint FK_betrachtergruppenwert_betrachter
        foreign key (betrachter_id) references Nutzer;

alter table betrachter_gruppenwert
    add constraint FK_betrachtergruppenwert_gruppenkategoriewert
        foreign key (gruppenkategoriewert_id) references GruppenkategorieWert;
