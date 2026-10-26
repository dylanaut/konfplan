-- Online-Aktivierung (siehe #781): letzter Revalidierungszaehler (Klon-Erkennung, kein Zuruecksetzen
-- durch wiedereingespielte alte Antworten) sowie Zeitpunkt und Fehler des letzten Revalidierungsversuchs.

alter table Lizenzzustand add column letzterZaehler bigint;
alter table Lizenzzustand add column revalidierungVersuch timestamp with time zone;
alter table Lizenzzustand add column revalidierungFehler varchar(1000);
