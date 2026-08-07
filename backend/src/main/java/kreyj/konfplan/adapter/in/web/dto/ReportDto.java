package kreyj.konfplan.adapter.in.web.dto;

import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;

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
}
