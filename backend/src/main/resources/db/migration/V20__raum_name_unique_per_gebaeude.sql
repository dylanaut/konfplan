-- Raumnamen müssen innerhalb eines Gebäudes eindeutig sein, um Verwechslungen bei der
-- Raumzuweisung (Planerstellung, Verfügbarkeiten) zu vermeiden.
alter table Raum
    add constraint UK_RAUM_GEBAEUDE_NAME unique (gebaeude_id, name);
