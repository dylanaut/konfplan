-- Kurzes, eindeutiges Kürzel (3-4 Buchstaben) für ein Gebäude, um Raumnamen in Reports/
-- Plandarstellungen kompakt mit Gebäudekontext anzuzeigen ("{Kürzel} {Raumname}" statt des
-- bisherigen "Raum: {Raumname}"-Präfixes). Nullable, da bestehende Gebäude noch keins haben.
alter table Gebaeude
    add column kuerzel varchar(4);

alter table Gebaeude
    add constraint UK_GEBAEUDE_KUERZEL unique (kuerzel);
