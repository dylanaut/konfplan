package kreyj.konfplan.adapter.in.web.dto;

import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Slot;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;

import java.util.List;
import java.util.Map;

public class ReportDto {

    public static class LaufzettelTeilnehmerDto {
        public VeranstaltungDto veranstaltung;
        public NutzerDto teilnehmer;
        public List<ZuweisungDto> plan;

        public LaufzettelTeilnehmerDto(Veranstaltung veranstaltung, Teilnehmer teilnehmer, List<ZuweisungDto> plan) {
            this.veranstaltung = VeranstaltungDto.from(veranstaltung);
            this.teilnehmer = NutzerDto.from(teilnehmer);
            this.plan = plan;
        }
    }

    public static class LaufzettelReferentDto {
        public VeranstaltungDto veranstaltung;
        public NutzerDto referent;
        public List<ReferentVortragDto> plan;

        public LaufzettelReferentDto(Veranstaltung veranstaltung, Referent referent, List<ReferentVortragDto> plan) {
            this.veranstaltung = VeranstaltungDto.from(veranstaltung);
            this.referent = NutzerDto.from(referent);
            this.plan = plan;
        }
    }

    public static class RaumbelegungsplanDto {
        public VeranstaltungDto veranstaltung;
        public RaumDto raum;
        public Map<Long, Map<Long, RaumplanEintragDto>> belegung;

        public RaumbelegungsplanDto(Veranstaltung veranstaltung, RaumDto raum, Map<Long, Map<Long, RaumplanEintragDto>> belegung) {
            this.veranstaltung = VeranstaltungDto.from(veranstaltung);
            this.raum = raum;
            this.belegung = belegung;
        }
    }

    public static class UebersichtRaeumeDto {
        public VeranstaltungDto veranstaltung;
        public List<RaumBelegungUebersicht> plan;

        public UebersichtRaeumeDto(Veranstaltung veranstaltung, List<RaumBelegungUebersicht> plan) {
            this.veranstaltung = VeranstaltungDto.from(veranstaltung);
            this.plan = plan;
        }
    }

    public static class RaumschilderDto {
        public VeranstaltungDto veranstaltung;
        public Map<Long, Map<Long, RaumplanEintragDto>> raumplan;
        public List<RaumDto> raeume;
        public List<SlotDto> slots;

        public RaumschilderDto(Veranstaltung veranstaltung, Map<Long, Map<Long, RaumplanEintragDto>> raumplan, List<RaumDto> raeume, List<SlotDto> slots) {
            this.veranstaltung = VeranstaltungDto.from(veranstaltung);
            this.raumplan = raumplan;
            this.raeume = raeume;
            this.slots = slots;
        }
    }

    public static class FreieSlotsDto {
        public VeranstaltungDto veranstaltung;
        public Map<Long, List<Slot>> freieSlots;
        public List<NutzerDto> nutzer; // Kann Teilnehmer oder Referenten enthalten

        public FreieSlotsDto(Veranstaltung veranstaltung, Map<Long, List<Slot>> freieSlots, List<NutzerDto> nutzer) {
            this.veranstaltung = VeranstaltungDto.from(veranstaltung);
            this.freieSlots = freieSlots;
            this.nutzer = nutzer;
        }
    }
}
