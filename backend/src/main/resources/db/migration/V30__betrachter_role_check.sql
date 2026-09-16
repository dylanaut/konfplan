-- Ergaenzt den in V14 eingefuehrten Rollen-Enum-Check um BETRACHTER (siehe #718/#725): das
-- Nutzer.role-Feld ist Hibernate-seitig nur die SINGLE_TABLE-Diskriminatorspalte ohne eigene
-- Wertebeschraenkung, die Datenbank erzwingt die erlaubten Werte aber zusaetzlich per
-- CHECK-Constraint. Ohne dieses Update schlaegt jedes Anlegen eines Betrachters in Prod mit
-- "violates check constraint nutzer_role_check" fehl - in Tests unbemerkt, da
-- %test.quarkus.flyway.enabled=false ist und die H2-Testdatenbank ihr Schema stattdessen aus den
-- Hibernate-Annotations ableitet (dort existiert kein CHECK-Constraint auf role).

ALTER TABLE nutzer DROP CONSTRAINT nutzer_role_check;
ALTER TABLE nutzer ADD CONSTRAINT nutzer_role_check
    CHECK (role IN ('REFERENT', 'ORGANISATOR', 'ADMINISTRATOR', 'TEILNEHMER', 'BETRACHTER'));

ALTER TABLE nutzer ADD CONSTRAINT nutzer_check4 CHECK (role <> 'BETRACHTER' OR (is_active IS NOT NULL));
