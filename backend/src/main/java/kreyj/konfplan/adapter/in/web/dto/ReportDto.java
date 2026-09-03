package kreyj.konfplan.adapter.in.web.dto;

import kreyj.konfplan.persistence.Prioritaet;
import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.persistence.Vortrag;
import kreyj.konfplan.persistence.Wahlvortrag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class ReportDto {

    public static class LaufzettelTeilnehmerDto {
        public final VeranstaltungDto veranstaltung;
        public final NutzerDto teilnehmer;
        public final List<ZuweisungDto> plan;

        public LaufzettelTeilnehmerDto(Veranstaltung veranstaltung, Teilnehmer teilnehmer, List<ZuweisungDto> plan) {
            this.veranstaltung = VeranstaltungDto.from(veranstaltung);
            this.teilnehmer = NutzerDto.from(teilnehmer);
            this.plan = plan;
        }
    }

    public static class LaufzettelReferentDto {
        public final VeranstaltungDto veranstaltung;
        public final NutzerDto referent;
        public final List<ReferentVortragDto> plan;

        public LaufzettelReferentDto(Veranstaltung veranstaltung, Referent referent, List<ReferentVortragDto> plan) {
            this.veranstaltung = VeranstaltungDto.from(veranstaltung);
            this.referent = NutzerDto.from(referent);
            this.plan = plan;
        }
    }

    public static class RaumbelegungsplanDto {
        public final VeranstaltungDto veranstaltung;
        public final RaumDto raum;
        public final Map<Long, Map<Long, RaumplanEintragDto>> belegung;

        public RaumbelegungsplanDto(Veranstaltung veranstaltung, RaumDto raum, Map<Long, Map<Long, RaumplanEintragDto>> belegung) {
            this.veranstaltung = VeranstaltungDto.from(veranstaltung);
            this.raum = raum;
            this.belegung = belegung;
        }
    }

    public static class UebersichtRaeumeDto {
        public final VeranstaltungDto veranstaltung;
        public final List<RaumBelegungUebersicht> plan;

        public UebersichtRaeumeDto(Veranstaltung veranstaltung, List<RaumBelegungUebersicht> plan) {
            this.veranstaltung = VeranstaltungDto.from(veranstaltung);
            this.plan = plan;
        }
    }

    public static class RaumschilderDto {
        public final VeranstaltungDto veranstaltung;
        public final Map<Long, Map<Long, RaumplanEintragDto>> raumplan;
        public final List<RaumDto> raeume;
        public final List<SlotDto> slots;

        public RaumschilderDto(Veranstaltung veranstaltung, Map<Long, Map<Long, RaumplanEintragDto>> raumplan, List<RaumDto> raeume, List<SlotDto> slots) {
            this.veranstaltung = VeranstaltungDto.from(veranstaltung);
            this.raumplan = raumplan;
            this.raeume = raeume;
            this.slots = slots;
        }
    }

    public static class FreieSlotsDto {
        public final VeranstaltungDto veranstaltung;
        public final Map<Long, List<SlotDto>> freieSlots;
        public final List<NutzerDto> nutzer; // Kann Teilnehmer oder Referenten enthalten

        public FreieSlotsDto(Veranstaltung veranstaltung, Map<Long, List<SlotDto>> freieSlots, List<NutzerDto> nutzer) {
            this.veranstaltung = VeranstaltungDto.from(veranstaltung);
            this.freieSlots = freieSlots;
            this.nutzer = nutzer;
        }
    }

    public static class LaufzettelAlleDto {
        public final VeranstaltungDto veranstaltung;
        public final Map<Long, List<ZuweisungDto>> plaene;
        public final List<NutzerDto> teilnehmer;

        public LaufzettelAlleDto(Veranstaltung veranstaltung, Map<Long, List<ZuweisungDto>> plaene, List<NutzerDto> teilnehmer) {
            this.veranstaltung = VeranstaltungDto.from(veranstaltung);
            this.plaene = plaene;
            this.teilnehmer = teilnehmer;
        }
    }

    public static class LaufzettelAlleReferentenDto {
        public final VeranstaltungDto veranstaltung;
        public final Map<Long, List<ReferentVortragDto>> plaene;
        public final List<NutzerDto> referenten;

        public LaufzettelAlleReferentenDto(Veranstaltung veranstaltung, Map<Long, List<ReferentVortragDto>> plaene, List<NutzerDto> referenten) {
            this.veranstaltung = VeranstaltungDto.from(veranstaltung);
            this.plaene = plaene;
            this.referenten = referenten;
        }
    }

    public static class AbstimmungsfragebogenDto {
        public final VeranstaltungDto veranstaltung;
        public final List<LegendeEintragDto> legende;
        public final List<NutzerDto> teilnehmer;

        public AbstimmungsfragebogenDto(Veranstaltung veranstaltung) {
            this.veranstaltung = VeranstaltungDto.from(veranstaltung);

            List<Wahlvortrag> sortiert = veranstaltung.getWahlvortraege().stream()
                .sorted(Comparator.comparing(Vortrag::getTitel, String.CASE_INSENSITIVE_ORDER))
                .toList();

            List<LegendeEintragDto> eintraege = new ArrayList<>();
            int nummer = 1;
            for (Wahlvortrag v : sortiert) {
                eintraege.add(new LegendeEintragDto(nummer++, v));
            }
            this.legende = eintraege;

            this.teilnehmer = veranstaltung.teilnehmer().stream()
                .map(NutzerDto::from)
                .sorted(Comparator
                    .comparing((NutzerDto t) -> null == t.lastName ? "" : t.lastName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(t -> null == t.firstName ? "" : t.firstName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        }
    }

    public static class VortragAnmeldungenDto {
        public final VeranstaltungDto veranstaltung;
        public final String vortragTitel;
        public final List<AnmeldungDto> anmeldungen;

        public VortragAnmeldungenDto(Veranstaltung veranstaltung, Wahlvortrag vortrag, List<Prioritaet> prioritaeten) {
            this.veranstaltung = VeranstaltungDto.from(veranstaltung);
            this.vortragTitel = vortrag.getTitel();
            this.anmeldungen = prioritaeten.stream().map(AnmeldungDto::new).toList();
        }
    }

    public static class AnmeldungDto {
        public final String loginName;
        public final int prioWert;

        public AnmeldungDto(Prioritaet prioritaet) {
            this.loginName = prioritaet.getTeilnehmer().getLoginName();
            this.prioWert = prioritaet.getPrioWert();
        }
    }

    public static class LegendeEintragDto {
        public final int nummer;
        public final String titel;
        public final String inhalt;
        public final String referentName;
        public final String referentOrganisation;

        public LegendeEintragDto(int nummer, Wahlvortrag vortrag) {
            this.nummer = nummer;
            this.titel = vortrag.getTitel();
            this.inhalt = vortrag.getInhalt();
            this.referentName = vortrag.getReferent().getFullName();
            this.referentOrganisation = vortrag.getReferent().getOrganisation();
        }
    }
}
