-- Lizenzierung (siehe #781): Instanzidentitaet (Ed25519-Schluesselpaar) und installierte Lizenz.
-- Singleton - hoechstens eine Zeile, vom LizenzStatusService angelegt.

create table Lizenzzustand
(
    id                bigint       not null,
    version           bigint       not null,
    instanzId         varchar(36)  not null,
    instanzPublicKey  varchar(255) not null,
    instanzPrivateKey varchar(255) not null,
    lizenzToken       text,
    zeitWasserstand   timestamp with time zone,
    letzteValidierung timestamp with time zone,
    primary key (id)
);
