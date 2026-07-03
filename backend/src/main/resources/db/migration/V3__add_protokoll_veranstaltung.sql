alter table if exists Protokoll
    add column if not exists veranstaltungId bigint;
