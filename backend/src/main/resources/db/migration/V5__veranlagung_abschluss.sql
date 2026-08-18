-- Neigung -> Veranlagung: komplett neuer Wertesatz, keine sinnvolle Alt->Neu-Zuordnung möglich,
-- daher werden bestehende Zuordnungen verworfen (mit dem Kunden abgestimmt).
drop table teilnehmer_neigungen;
drop table wahlvortrag_neigungen;

create table teilnehmer_veranlagungen
(
    teilnehmer_id bigint      not null,
    veranlagung   varchar(50) not null check ((veranlagung in
                                                ('SOZIAL', 'ORGANISATORISCH', 'MEDIZINISCH', 'TECHNISCH',
                                                 'KAUFMAENNISCH', 'KREATIV', 'SPORTLICH', 'HANDWERKLICH',
                                                 'WISSENSCHAFTLICH', 'OEFFENTLICH', 'RECHTLICH', 'OEKOLOGISCH')))
);

alter table teilnehmer_veranlagungen
    add constraint FK_teilnehmer_veranlagungen_teilnehmer
        foreign key (teilnehmer_id)
            references Nutzer;

create table wahlvortrag_veranlagungen
(
    vortrag_id  bigint      not null,
    veranlagung varchar(50) not null check ((veranlagung in
                                              ('SOZIAL', 'ORGANISATORISCH', 'MEDIZINISCH', 'TECHNISCH',
                                               'KAUFMAENNISCH', 'KREATIV', 'SPORTLICH', 'HANDWERKLICH',
                                               'WISSENSCHAFTLICH', 'OEFFENTLICH', 'RECHTLICH', 'OEKOLOGISCH')))
);

alter table wahlvortrag_veranlagungen
    add constraint FK_wahlvortrag_veranlagungen_vortrag
        foreign key (vortrag_id)
            references Vortrag;

-- Neues, einwertiges, optionales Attribut Abschluss auf Vortrag (Wahl- und Pflichtvortrag).
alter table Vortrag
    add column abschluss varchar(50)
        check ((abschluss in
                ('BERUFSREIFE', 'MITTLERE_REIFE', 'ALLGEMEINE_HOCHSCHULREIFE',
                 'FACHHOCHSCHULREIFE', 'HOCHSCHULABSCHLUSS')));
