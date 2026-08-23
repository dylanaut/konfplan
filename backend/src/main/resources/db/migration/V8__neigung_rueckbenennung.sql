-- Veranlagung -> Neigung: reiner Namenswechsel, Wertesatz (SOZIAL, ORGANISATORISCH, ...) bleibt
-- unveraendert, bestehende Zuordnungen bleiben erhalten.
alter table teilnehmer_veranlagungen
    rename to teilnehmer_neigungen;
alter table teilnehmer_neigungen
    rename column veranlagung to neigung;
alter table teilnehmer_neigungen
    rename constraint FK_teilnehmer_veranlagungen_teilnehmer to FK_teilnehmer_neigungen_teilnehmer;

alter table wahlvortrag_veranlagungen
    rename to wahlvortrag_neigungen;
alter table wahlvortrag_neigungen
    rename column veranlagung to neigung;
alter table wahlvortrag_neigungen
    rename constraint FK_wahlvortrag_veranlagungen_vortrag to FK_wahlvortrag_neigungen_vortrag;
