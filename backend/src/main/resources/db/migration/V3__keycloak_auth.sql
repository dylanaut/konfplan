-- Identitaet/Passwoerter wandern zu Keycloak - lokale Nutzer-Entitaet behaelt nur noch Fachdaten.
-- Da in Produktion praktisch keine echten Nutzer existieren (Nutzer entstehen fast ausschliesslich
-- per CSV-Import zusammen mit einer Veranstaltung), ist kein gestufter Rollout mit Uebergangsphase
-- noetig - Spalten koennen direkt entfernt werden. Der einzige echte Bestandsaccount (Bootstrap-Admin
-- aus V2__data.sql) wird manuell in Keycloak angelegt.
alter table Nutzer add column keycloak_id varchar(255);
alter table Nutzer add constraint UK_nutzer_keycloak_id unique (keycloak_id);

alter table Nutzer drop column password_hash;
alter table Nutzer drop column reset_token;
alter table Nutzer drop column reset_token_expiry;
alter table Nutzer drop column new_email;
alter table Nutzer drop column email_change_token;
alter table Nutzer drop column email_change_token_expiry;
