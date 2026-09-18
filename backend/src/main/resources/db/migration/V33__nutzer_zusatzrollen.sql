-- Mehrfachrollen (siehe #751): zusaetzlich zur Primaerrolle (Nutzer.role-Discriminator) gehaltene
-- Rollen. Nur TEILNEHMER/REFERENT/BETRACHTER sind als Zusatzrolle vergebbar - ORGANISATOR/
-- ADMINISTRATOR bleiben ausschliesslich Primaerrollen.

create table nutzer_zusatzrolle
(
    nutzer_id bigint      not null,
    rolle     varchar(50) not null,
    primary key (nutzer_id, rolle)
);

alter table nutzer_zusatzrolle
    add constraint FK_nutzerzusatzrolle_nutzer
        foreign key (nutzer_id) references Nutzer;

alter table nutzer_zusatzrolle
    add constraint nutzer_zusatzrolle_check
        check (rolle in ('TEILNEHMER', 'REFERENT', 'BETRACHTER'));
