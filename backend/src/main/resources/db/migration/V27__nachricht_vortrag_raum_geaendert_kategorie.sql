-- Wird eine Wahlvortrag-Instanz per "Raum umbuchen" (siehe UmplanungService#vortragsInstanzRaumUmbuchen)
-- in einen anderen Raum verlegt, benachrichtigt das den Referenten über eine neue
-- Nachrichtenkategorie - erweitert den harten CHECK-Constraint aus V15__nachricht.sql (zuletzt
-- erweitert in V26).
alter table Nachricht
    drop constraint nachricht_kategorie_check;

alter table Nachricht
    add constraint nachricht_kategorie_check check (kategorie in ('VORTRAG_ZURUECKGEZOGEN', 'ORGANISATOR_NACHRICHT', 'VORTRAG_AUSGEFALLEN', 'TEILNEHMER_UMGEBUCHT', 'TEILNEHMER_NICHT_VERFUEGBAR', 'TEILNEHMER_NACHGEBUCHT', 'VORTRAG_RAUM_GEAENDERT'));
