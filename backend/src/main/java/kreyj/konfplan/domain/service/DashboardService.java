package kreyj.konfplan.domain.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import kreyj.konfplan.adapter.in.web.dto.NutzerDto;
import kreyj.konfplan.adapter.in.web.dto.RaumDto;
import kreyj.konfplan.adapter.in.web.dto.SlotDto;
import kreyj.konfplan.adapter.in.web.dto.TeilnehmerDto;
import kreyj.konfplan.adapter.in.web.dto.VeranstaltungDto;
import kreyj.konfplan.adapter.in.web.dto.VortragDto;
import kreyj.konfplan.adapter.in.web.dto.templating.BelegungDetail;
import kreyj.konfplan.adapter.in.web.dto.templating.DashboardData;
import kreyj.konfplan.adapter.in.web.dto.templating.Planungsstatistik;
import kreyj.konfplan.adapter.in.web.dto.templating.PrioReport;
import kreyj.konfplan.adapter.in.web.dto.templating.Stundenplan;
import kreyj.konfplan.adapter.in.web.dto.templating.TeilnehmerErfuellung;
import kreyj.konfplan.adapter.in.web.dto.templating.TeilnehmerReport;
import kreyj.konfplan.adapter.in.web.dto.templating.TeilnehmerSlotBelegung;
import kreyj.konfplan.adapter.in.web.dto.templating.TeilnehmerStundenplan;
import kreyj.konfplan.adapter.in.web.dto.templating.WahlErfuellungStats;
import kreyj.konfplan.adapter.in.web.dto.templating.WahlvortragStatus;
import kreyj.konfplan.persistence.NutzerVerfuegbarkeit;
import kreyj.konfplan.persistence.Planungsergebnis;
import kreyj.konfplan.persistence.Prioritaet;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.persistence.VeranstaltungsVerfuegbarkeit;
import kreyj.konfplan.util.StringHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.time.LocalDateTime.now;
import static java.util.stream.Collectors.toMap;
import static kreyj.konfplan.util.DateHelper.DATE_TIME_FORMATTER;

@ApplicationScoped
public class DashboardService {

    private final PrioritaetService prioService;
    private final PlanService planService;


    public DashboardService(PrioritaetService prioService, PlanService planService) {
        this.prioService = prioService;
        this.planService = planService;
    }


    /**
     * Lädt die für alle Reports gemeinsam benötigten Basisdaten. Die teureren, verschachtelten
     * Berechnungen (Belegungsdetails, Teilnehmer-Erfüllung, Teilnehmer-Stundenplan, Statistiken)
     * werden bewusst NICHT hier ausgeführt, sondern nur von dem jeweiligen Report angefordert,
     * der sie tatsächlich braucht (siehe getStundenplan/getTeilnehmerReport/getPrioReport).
     */
    private DashboardData buildBaseData(Veranstaltung veranstaltung) {
        Planungsergebnis.MinizincResult result = planService.getMinizincResult(veranstaltung);

        Map<Long, Set<Long>> nvMap =
            NutzerVerfuegbarkeit.<NutzerVerfuegbarkeit>list("veranstaltungId = ?1", veranstaltung.getId())
                .stream().collect(toMap(NutzerVerfuegbarkeit::getNutzerId,
                    VeranstaltungsVerfuegbarkeit::getVerfuegbareSlotIds));
        DashboardData dashboardData = new DashboardData(
            VeranstaltungDto.from(veranstaltung),
            result.besucht, result.instanz_slot, result.instanz_raum,
            nvMap,
            result.teilnehmer_oids, result.wahlvortrag_oids, result.slot_oids, result.raum_oids);
        dashboardData.teilnehmer = veranstaltung.teilnehmer().stream()
            .map(TeilnehmerDto::from)
            .collect(toMap(tn -> tn.id, Function.identity()));
        dashboardData.wahlvortraege = veranstaltung.getWahlvortraege().stream()
            .map(VortragDto::from)
            .collect(toMap(wv -> wv.id, Function.identity()));
        dashboardData.pflichtvortraege = veranstaltung.getPflichtvortraege().stream()
            .map(VortragDto::from)
            .collect(toMap(pv -> pv.id, Function.identity()));
        dashboardData.slots = veranstaltung.getSlots().stream()
            .map(SlotDto::from)
            .collect(toMap(s -> s.id, Function.identity()));
        dashboardData.raeume = veranstaltung.getRaeume().stream()
            .map(RaumDto::from)
            .collect(toMap(r -> r.id, Function.identity()));
        dashboardData.referenten = veranstaltung.referenten().stream()
            .map(NutzerDto::from)
            .collect(toMap(r -> r.id, Function.identity()));
        dashboardData.teilnehmerPrioritaeten =
            prioService.getVortragPrioritaetenByVeranstaltung(veranstaltung.getId());

        return dashboardData;
    }


    @Transactional
    public Stundenplan getStundenplan(Veranstaltung veranstaltung) {
        DashboardData dd = buildBaseData(veranstaltung);

        berechneBelegungUndFreieSlots(dd);
        createTeilnehmerErfuellung(dd);
        calculatePrefsFillUpStats(dd);
        berechnePlanungsstatistik(dd);

        String geplantAm = now().format(DATE_TIME_FORMATTER);
        return new Stundenplan(dd.veranstaltung, dd.slots, dd.raeume, dd.belegungDetails,
            dd.freieTnProSlot, dd.planungsstatistik,
            dd.wahlErfuellungStats, dd.wahlvortraege, geplantAm);
    }


    @Transactional
    public TeilnehmerReport getTeilnehmerReport(Veranstaltung veranstaltung) {
        DashboardData dd = buildBaseData(veranstaltung);

        createTeilnehmerStundenplan(dd);

        return new TeilnehmerReport(dd.veranstaltung,
            dd.teilnehmer.get(dd.mzTeilnehmerOids[0]), dd.slots, dd.teilnehmerStundenplan, computeGruppen(dd));
    }


    @Transactional
    public PrioReport getPrioReport(Veranstaltung veranstaltung) {
        DashboardData dd = buildBaseData(veranstaltung);

        createTeilnehmerErfuellung(dd);

        List<Integer> numInstanzenProWv =
            Arrays.stream(dd.instanzSlot).map(sub -> (int) Arrays.stream(sub).filter(x -> x > 0).count()).toList();
        String geplantAm = now().format(DATE_TIME_FORMATTER);

        return new PrioReport(dd.veranstaltung, dd.slots, dd.raeume,
            dd.instanzRaum, dd.instanzSlot, numInstanzenProWv,
            dd.teilnehmerErfuellung, dd.wahlvortraege, dd.referenten,
            computeGruppen(dd), geplantAm,
            dd.mzWahlvortragOids, dd.mzSlotOids, dd.mzRaumOids);
    }


    private List<String> computeGruppen(DashboardData dd) {
        return dd.teilnehmer.values().stream()
            .flatMap(tn -> tn.gruppen.stream())
            .distinct()
            .sorted()
            .toList();
    }


    private void calculatePrefsFillUpStats(DashboardData dd) {
        Map<Integer, Integer> prioPrefs = new HashMap<>();
        Map<Long, Integer> wvPrefs = new HashMap<>();
        int totalPrefs = 0;
        Map<Integer, Integer> prioFillUps = new HashMap<>();
        Map<Long, Integer> wvFillUps = new HashMap<>();
        int totalFillUps = 0;

        for (int tnIdx = 0; tnIdx < dd.besucht.length; tnIdx++) {
            boolean[][] tn_besucht = dd.besucht[tnIdx];
            TeilnehmerDto tn = dd.teilnehmer.get(dd.mzTeilnehmerOids[tnIdx]);
            Map<Long, Integer> prios = dd.getPrioritaeten(tn.id);

            for (int wvIdx = 0; wvIdx < dd.mzWahlvortragOids.length; wvIdx++) {
                boolean[] tn_v_besucht = tn_besucht[wvIdx];
                long wvOid = dd.mzWahlvortragOids[wvIdx];
                int vPrio = prios.getOrDefault(wvOid, 0);
                if (vPrio > 0) {
                    totalPrefs++;
                    prioPrefs.merge(vPrio, 1, Integer::sum);
                    prioFillUps.putIfAbsent(vPrio, 0);

                    wvPrefs.merge(wvOid, 1, Integer::sum);
                    wvFillUps.putIfAbsent(wvOid, 0);

                    if (IntStream.range(0, tn_v_besucht.length).anyMatch(i -> tn_v_besucht[i])) {
                        prioFillUps.merge(vPrio, 1, Integer::sum);
                        wvFillUps.merge(wvOid, 1, Integer::sum);
                        totalFillUps++;
                    }
                }
            }
        }
        dd.wahlErfuellungStats = new WahlErfuellungStats(totalPrefs, prioPrefs, wvPrefs, totalFillUps,
            prioFillUps, wvFillUps);
    }


    private void berechnePlanungsstatistik(DashboardData dd) {
        long belegtePlaetze = dd.belegungDetails.values().stream().mapToLong(bd -> bd.anzahl).sum();
        long kapazitaetTotal = dd.slots.size() * dd.raeume.values().stream().mapToLong(r -> r.kapazitaet).sum();

        long totalWuenscheErfuellt = 0;
        long unerfuellte = 0;
        long prio1 = 0;
        long prio2 = 0;
        long prio3 = 0;
        long anzahlAuffuellungen = 0;

        for (TeilnehmerErfuellung t : dd.teilnehmerErfuellung) {
            for (WahlvortragStatus vStatus : t.wvStatuus().values()) {
                if ("+".equals(vStatus.status())) {
                    totalWuenscheErfuellt++;
                    // Höchste Priorität = Prioritaet.PRIO_MAX (10), absteigend für Platz 2/3.
                    if (vStatus.prioWert() == Prioritaet.PRIO_MAX) {
                        prio1++;
                    } else if (vStatus.prioWert() == Prioritaet.PRIO_MAX - 1) {
                        prio2++;
                    } else if (vStatus.prioWert() == Prioritaet.PRIO_MAX - 2) {
                        prio3++;
                    }
                } else if ("-".equals(vStatus.status())) {
                    unerfuellte++;
                } else if ("f".equals(vStatus.status())) {
                    anzahlAuffuellungen++;
                }
            }
        }

        dd.planungsstatistik = new Planungsstatistik(belegtePlaetze, kapazitaetTotal,
            unerfuellte, totalWuenscheErfuellt,
            prio1, prio2, prio3,
            anzahlAuffuellungen);
    }


    private void createTeilnehmerErfuellung(DashboardData dd) {
        for (int tnIdx = 0; tnIdx < dd.mzTeilnehmerOids.length; tnIdx++) {
            long tnOid = dd.mzTeilnehmerOids[tnIdx];
            Map<Long, WahlvortragStatus> wahlVortragStatuus = new LinkedHashMap<>();
            Map<Long, Integer> wvPrios = dd.getPrioritaeten(tnOid);

            for (int wvIdx = 0; wvIdx < dd.mzWahlvortragOids.length; wvIdx++) {
                long wvOid = dd.mzWahlvortragOids[wvIdx];
                int[] wvInstSlots = dd.instanzSlot[wvIdx];
                boolean[] besuchteInstanzen = dd.besucht[tnIdx][wvIdx];

                int besuchteInstanz = 0;
                for (int instIdx = 0; instIdx < wvInstSlots.length; instIdx++) {
                    boolean slotInstanzBesucht = wvInstSlots[instIdx] > 0;
                    if (slotInstanzBesucht && besuchteInstanzen[instIdx]) {
                        besuchteInstanz = instIdx + 1;
                        break;
                    }
                }

                String status = "0";
                int prioWert = wvPrios.getOrDefault(wvOid, 0);
                if (prioWert > 0) {
                    status = (besuchteInstanz > 0) ? "+" : "-";
                } else if (besuchteInstanz > 0) {
                    status = "f";
                }
                wahlVortragStatuus.put(wvOid, new WahlvortragStatus(status, prioWert, besuchteInstanz));
            }

            dd.teilnehmerErfuellung.add(new TeilnehmerErfuellung(dd.teilnehmer.get(tnOid), wahlVortragStatuus));
        }
    }


    /**
     * Berechnet Belegungsdetails (welcher Vortrag läuft in welchem Slot/Raum) und die Liste
     * der freien Teilnehmer pro Slot. Wird nur vom Stundenplan-Report benötigt.
     */
    private void berechneBelegungUndFreieSlots(DashboardData dd) {
        Map<Long, Set<Long>> verplanteTnProSlot
            = dd.slots.keySet().stream().collect(Collectors.toMap(Function.identity(),
            k -> new HashSet<>()));

        // Verarbeite Pflichtvorträge: erzeuge Belegungsdetails
        for (VortragDto pv : dd.pflichtvortraege.values()) {
            long pflSlotId = pv.pflichtSlotId;
            long pflRaumId = pv.pflichtRaumId;
            String pflichtGruppe = pv.pflichtGruppe;

            List<String> namen = new ArrayList<>();
            for (TeilnehmerDto tn : dd.teilnehmer.values()) {
                if (tn.gruppen.contains(pflichtGruppe)) {
                    namen.add(tn.getFullname());
                    verplanteTnProSlot.get(pflSlotId).add(tn.id);
                }
            }

            namen = StringHelper.sortNames(namen);

            NutzerDto ref = dd.referenten.get(pv.referentId);
            String key = pflSlotId + "_" + pflRaumId;
            dd.belegungDetails.put(key,
                new BelegungDetail(pv.titel, ref.getFullname(), ref.organisation, true,
                    namen, namen.size()));
        }

        // Verarbeite Wahlvorträge: erzeuge Belegungsdetails
        for (int wvIdx = 0; wvIdx < dd.instanzSlot.length; wvIdx++) {
            long wvOid = dd.mzWahlvortragOids[wvIdx];
            int[] wvInstanzSlot = dd.instanzSlot[wvIdx];
            for (int instIdx = 0; instIdx < wvInstanzSlot.length; instIdx++) {
                int slotIdx = wvInstanzSlot[instIdx];

                if (slotIdx > 0) {
                    long slotOid = dd.mzSlotOids[slotIdx - 1];
                    int[] wvInstanzRaum = dd.instanzRaum[wvIdx];
                    int raumIdx = wvInstanzRaum[instIdx];
                    long raumOid = dd.mzRaumOids[raumIdx - 1];
                    List<String> wvNamen = new ArrayList<>();
                    for (int tnIdx = 0; tnIdx < dd.tnAnzahl; tnIdx++) {
                        if (dd.besucht[tnIdx][wvIdx][instIdx]) {
                            long tnOid = dd.mzTeilnehmerOids[tnIdx];
                            TeilnehmerDto tn = dd.teilnehmer.get(tnOid);
                            wvNamen.add(tn.gName());
                            verplanteTnProSlot.get(slotOid).add(tnOid);
                        }
                    }

                    if (!wvNamen.isEmpty()) {
                        String key = slotOid + "_" + raumOid;
                        VortragDto wv = dd.wahlvortraege.get(wvOid);
                        NutzerDto ref = dd.referenten.get(wv.referentId);
                        wvNamen = StringHelper.sortNames(wvNamen);
                        dd.belegungDetails.put(key,
                            new BelegungDetail(wv.titel, ref.getFullname(), ref.organisation, false,
                                wvNamen, wvNamen.size()));
                    }
                }
            }
        }

        // Determine free participants per slot
        for (Long slotOid : dd.slots.keySet()) {
            List<String> freieTn = new ArrayList<>();
            for (long tnOid : dd.teilnehmer.keySet()) {
                if (!verplanteTnProSlot.get(slotOid).contains(tnOid)
                    && dd.isVerfuegbarInSlot(tnOid, slotOid)) {
                    TeilnehmerDto tn = dd.teilnehmer.get(tnOid);
                    freieTn.add(tn.gName());
                }
            }

            dd.freieTnProSlot.put(slotOid, freieTn);
        }
    }


    private void createTeilnehmerStundenplan(DashboardData dd) {
        for (int tnIdx = 0; tnIdx < dd.mzTeilnehmerOids.length; tnIdx++) {
            TeilnehmerDto tn = dd.teilnehmer.get(dd.mzTeilnehmerOids[tnIdx]);
            long tnOid = tn.id;
            Set<String> tnGruppen = tn.gruppen;
            Map<Long, Integer> wvPrios = dd.getPrioritaeten(tnOid);
            Map<Long, TeilnehmerSlotBelegung> tnSlotsBelegungen = new LinkedHashMap<>();

            for (int slotIdx = 1; slotIdx <= dd.mzSlotOids.length; slotIdx++) {
                long slotOid = dd.mzSlotOids[slotIdx - 1];
                SlotDto slot = dd.slots.get(slotOid);
                TeilnehmerSlotBelegung belegung = new TeilnehmerSlotBelegung("frei", null, "frei");

                if (!dd.isVerfuegbarInSlot(tn, slot)) {
                    belegung = new TeilnehmerSlotBelegung("Abwesend", null, "abwesend");
                }

                for (VortragDto pv : dd.pflichtvortraege.values()) {
                    if (tnGruppen.contains(pv.pflichtGruppe) && Objects.equals(slotOid, pv.pflichtSlotId)) {
                        belegung = new TeilnehmerSlotBelegung(pv.titel,
                            dd.raeume.get(pv.pflichtRaumId).name, "pflicht");
                    }
                }

                for (int wvIdx = 0; wvIdx < dd.instanzSlot.length; wvIdx++) {
                    VortragDto wv = dd.wahlvortraege.get(dd.mzWahlvortragOids[wvIdx]);
                    long wvOid = wv.id;
                    boolean[] tnVortragBesucht = dd.besucht[tnIdx][wvIdx];
                    int[] wahlRaumInstanz = dd.instanzRaum[wvIdx];

                    for (int instIdx = 0; instIdx < dd.instanzSlot[wvIdx].length; instIdx++) {
                        if (dd.instanzSlot[wvIdx][instIdx] == slotIdx && tnVortragBesucht[instIdx]) {
                            long raumOid = dd.mzRaumOids[wahlRaumInstanz[instIdx] - 1];
                            String raumName = dd.raeume.get(raumOid).name;
                            String typ = wvPrios.getOrDefault(wvOid, 0) == 0 ? "auffuellung" : "wahl";
                            belegung = new TeilnehmerSlotBelegung(wv.titel, raumName, typ);
                        }
                    }
                }
                tnSlotsBelegungen.put(slotOid, belegung);
            }

            dd.teilnehmerStundenplan.add(new TeilnehmerStundenplan(tn, tnSlotsBelegungen));
        }
    }
}
