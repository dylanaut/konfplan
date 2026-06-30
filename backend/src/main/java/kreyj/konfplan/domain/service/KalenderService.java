package kreyj.konfplan.domain.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import kreyj.konfplan.adapter.in.web.dto.ReferentVortragDto;
import kreyj.konfplan.adapter.in.web.dto.ZuweisungDto;
import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.Version;

import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class KalenderService {

    @Inject
    PlanService planService;


    public Calendar generateAdminCalendar(Veranstaltung veranstaltung) {
        List<ZuweisungDto> zuweisungen = planService.getGesamtplan(veranstaltung);
        return createCalendarFromZuweisungen(zuweisungen);
    }


    public Calendar generateTeilnehmerCalendar(Veranstaltung veranstaltung, Teilnehmer teilnehmer) {
        List<ZuweisungDto> zuweisungen = planService.getPlanFuerTeilnehmer(teilnehmer, veranstaltung);
        return createCalendarFromZuweisungen(zuweisungen);
    }


    public Calendar generateReferentCalendar(Veranstaltung veranstaltung, Referent referent) {
        List<ReferentVortragDto> planFuerReferent = planService.getPlanFuerReferent(referent, veranstaltung);
        List<ZuweisungDto> zuweisungen = planFuerReferent.stream().map(KalenderService::maptoZuweisungDto).collect(Collectors.toList());
        return createCalendarFromZuweisungen(zuweisungen);
    }


    private static ZuweisungDto maptoZuweisungDto(ReferentVortragDto dto) {
        return new ZuweisungDto("", dto.vortragTitel, dto.slotBeginn, dto.slotEnde, dto.raumName, dto.gebaeudeName, dto.referentName);
    }


    private Calendar createCalendarFromZuweisungen(List<ZuweisungDto> zuweisungen) {
        Calendar calendar = new Calendar();
        calendar.add(new ProdId("-//KonfPlan//iCal4j 1.0//DE"));
        calendar.add(new Version());

        for (ZuweisungDto zuweisung : zuweisungen) {
            String titel = zuweisung.vortragTitel;
            String location = zuweisung.raumName;
            String description = "Referent: " + zuweisung.referentName;

            VEvent event = new VEvent(zuweisung.slotBeginn, zuweisung.slotEnde, titel);
            event.add(new Uid(Integer.toString(zuweisung.hashCode())));
            event.add(new net.fortuna.ical4j.model.property.Location(location));
            event.add(new net.fortuna.ical4j.model.property.Description(description));

            calendar.add(event);
        }

        return calendar;
    }
}
