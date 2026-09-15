-- Schema fuer strukturierte Teilnehmer-Gruppenkategorien (siehe #690): loest schrittweise das
-- bisher flache, unstrukturierte teilnehmer_gruppen ab. Die alten Tabellen (teilnehmer_gruppen,
-- veranstaltung_gruppen) bleiben vorerst unangetastet und weiterhin von der bestehenden
-- Anwendungslogik genutzt, bis alle Konsumenten in Folge-PRs umgestellt sind.

create table Gruppenkategorie
(
    id               bigint       not null,
    version          bigint       not null,
    veranstaltung_id bigint       not null,
    name             varchar(255) not null,
    mehrwertig       boolean      not null,
    pflicht          boolean      not null,
    primary key (id)
);

alter table Gruppenkategorie
    add constraint UK_GRUPPENKATEGORIE_VERANSTALTUNG_NAME unique (veranstaltung_id, name);

alter table Gruppenkategorie
    add constraint FK_gruppenkategorie_veranstaltung
        foreign key (veranstaltung_id) references Veranstaltung;

create table GruppenkategorieWert
(
    id                  bigint       not null,
    version             bigint       not null,
    gruppenkategorie_id bigint       not null,
    wert                varchar(255) not null,
    primary key (id)
);

alter table GruppenkategorieWert
    add constraint UK_GRUPPENKATEGORIEWERT_KATEGORIE_WERT unique (gruppenkategorie_id, wert);

alter table GruppenkategorieWert
    add constraint FK_gruppenkategoriewert_gruppenkategorie
        foreign key (gruppenkategorie_id) references Gruppenkategorie;

create table teilnehmer_gruppenwert
(
    teilnehmer_id           bigint not null,
    gruppenkategoriewert_id bigint not null,
    primary key (teilnehmer_id, gruppenkategoriewert_id)
);

alter table teilnehmer_gruppenwert
    add constraint FK_teilnehmergruppenwert_teilnehmer
        foreign key (teilnehmer_id) references Nutzer;

alter table teilnehmer_gruppenwert
    add constraint FK_teilnehmergruppenwert_gruppenkategoriewert
        foreign key (gruppenkategoriewert_id) references GruppenkategorieWert;


-- Datenmigration: aus den bestehenden flachen teilnehmer_gruppen-Werten (kein Kategorie-Konzept
-- existierte bisher) die drei in PROD tatsaechlich vorkommenden, impliziten Kategorien rein
-- anhand der Werte-Syntax ableiten:
--   Schule: Wert 'RKS' oder 'MGL'         (einwertig, Pflicht)
--   Messe:  Wert beginnt mit 'M_'         (mehrwertig, Kann)
--   Klasse: Wert beginnt mit einer Ziffer (einwertig, Pflicht)
-- Werte, die keinem der drei Muster entsprechen, werden NICHT migriert (bleiben unverändert in
-- teilnehmer_gruppen stehen) - laut Aufgabenstellung sollte es in der aktuellen PROD-Veranstaltung
-- keine solchen Werte geben.
--
-- Einschraenkung: ein Teilnehmer-Gruppenwert war bisher nicht auf eine einzelne Veranstaltung
-- skaliert (Teilnehmer.gruppen ist teilnehmer-global). Diese Migration ordnet ihn JEDER
-- Veranstaltung zu, der der Teilnehmer zugeordnet ist (Nutzer_Veranstaltung) - fuer Teilnehmer mit
-- Mitgliedschaft in genau einer Veranstaltung (der Normalfall) ist das korrekt; bei Teilnehmern in
-- mehreren gleichzeitigen Veranstaltungen wuerde derselbe Wert in mehrere Kategorien-Instanzen
-- uebernommen.

create temporary table gruppen_klassifikation as
select tg.teilnehmer_id,
       nv.veranstaltung_id,
       tg.gruppen as wert,
       case
           when tg.gruppen in ('RKS', 'MGL') then 'Schule'
           when tg.gruppen like 'M\_%' escape '\' then 'Messe'
           when tg.gruppen ~ '^[0-9]' then 'Klasse'
           else null
           end    as kategorie
from teilnehmer_gruppen tg
         join Nutzer_Veranstaltung nv on nv.nutzer_id = tg.teilnehmer_id;

insert into Gruppenkategorie (id, version, veranstaltung_id, name, mehrwertig, pflicht)
select nextval('id_sequence'),
       0,
       veranstaltung_id,
       kategorie,
       kategorie = 'Messe',
       kategorie <> 'Messe'
from (select distinct veranstaltung_id, kategorie from gruppen_klassifikation where kategorie is not null) k;

insert into GruppenkategorieWert (id, version, gruppenkategorie_id, wert)
select nextval('id_sequence'), 0, gk.id, w.wert
from (select distinct veranstaltung_id, kategorie, wert from gruppen_klassifikation where kategorie is not null) w
         join Gruppenkategorie gk on gk.veranstaltung_id = w.veranstaltung_id and gk.name = w.kategorie;

insert into teilnehmer_gruppenwert (teilnehmer_id, gruppenkategoriewert_id)
select distinct k.teilnehmer_id, gkw.id
from gruppen_klassifikation k
         join Gruppenkategorie gk on gk.veranstaltung_id = k.veranstaltung_id and gk.name = k.kategorie
         join GruppenkategorieWert gkw on gkw.gruppenkategorie_id = gk.id and gkw.wert = k.wert
where k.kategorie is not null;

drop table gruppen_klassifikation;
