package kreyj.konfplan.domain.service;

import jakarta.enterprise.context.ApplicationScoped;
import kreyj.konfplan.persistence.IdEntity;
import kreyj.konfplan.persistence.NutzerVerfuegbarkeit;
import kreyj.konfplan.persistence.Planungsergebnis;
import kreyj.konfplan.persistence.Prioritaet;
import kreyj.konfplan.persistence.Raum;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranlagung;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.persistence.Wahlvortrag;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

import static java.util.stream.Collectors.toMap;

/**
 * Verteilt freie, nicht verplante Teilnehmer nachträglich auf bereits vom Solver erzeugte
 * Wahlvortrags-Instanzen mit Restkapazität (siehe {@link kreyj.konfplan.adapter.in.web.dto.SolverConfig#isAuffuellen()}).
 * Erzeugt oder verschiebt keine Instanzen, sondern befüllt nur bestehende.
 */
@ApplicationScoped
public class AuffuellungService {
    private static final Logger LOG = Logger.getLogger(AuffuellungService.class);


    private record Instanz(int wvIdx, int instIdx) {
    }


    public void fuelleAuf(Veranstaltung veranstaltung, Planungsergebnis.MinizincResult result) {
        fuelleAuf(veranstaltung, result, 0);
    }


    /**
     * @param maxWvsProTn maximale Anzahl Wahlvorträge, die einem Teilnehmer insgesamt zugeordnet werden dürfen
     *                    (bereits vom Solver zugewiesene eingeschlossen); 0 = kein Limit.
     */
    public void fuelleAuf(Veranstaltung veranstaltung, Planungsergebnis.MinizincResult result, int maxWvsProTn) {
        LOG.info("Auffuellen gestartet");
        long[] tnOids = result.teilnehmer_oids;
        long[] wvOids = result.wahlvortrag_oids;
        long[] slotOids = result.slot_oids;
        long[] raumOids = result.raum_oids;
        int[][] instanzSlot = result.instanz_slot;
        int[][] instanzRaum = result.instanz_raum;
        boolean[][][] besucht = result.besucht;

        int tnSize = tnOids.length;
        int wvSize = wvOids.length;
        int slotSize = slotOids.length;
        int maxInstanzen = wvSize > 0 ? instanzSlot[0].length : 0;

        Map<Long, Raum> raumByOid = veranstaltung.getRaeume().stream().collect(toMap(IdEntity::getId, Function.identity()));
        Map<Long, Wahlvortrag> wahlvortragByOid = veranstaltung.getWahlvortraege().stream()
            .collect(toMap(IdEntity::getId, Function.identity()));
        Map<Long, Teilnehmer> teilnehmerByOid = veranstaltung.teilnehmer().stream()
            .collect(toMap(IdEntity::getId, Function.identity()));

        Map<Long, NutzerVerfuegbarkeit> verfuegbarkeitByNutzerId =
            NutzerVerfuegbarkeit.<NutzerVerfuegbarkeit>list("veranstaltungId = ?1", veranstaltung.getId())
                .stream().collect(toMap(NutzerVerfuegbarkeit::getNutzerId, Function.identity()));

        Map<Long, List<Prioritaet>> eigenePrioritaetenByTnOid = new HashMap<>();
        for (Teilnehmer tn : teilnehmerByOid.values()) {
            eigenePrioritaetenByTnOid.put(tn.getId(),
                Prioritaet.list("teilnehmer = ?1 and prioWert > 0 order by prioWert desc", tn));
        }

        // Restkapazität je Instanz und aktive Instanzen je Slot (1-basierter MZ-Slot-Index) ermitteln.
        int[][] restkapazitaet = new int[wvSize][maxInstanzen];
        Map<Integer, List<Instanz>> instanzenProSlot = new HashMap<>();

        for (int wIdx = 0; wIdx < wvSize; wIdx++) {
            for (int iIdx = 0; iIdx < maxInstanzen; iIdx++) {
                int slotIdx1 = instanzSlot[wIdx][iIdx];
                int raumIdx1 = instanzRaum[wIdx][iIdx];
                if (slotIdx1 <= 0 || raumIdx1 <= 0) {
                    continue;
                }

                Raum raum = raumByOid.get(raumOids[raumIdx1 - 1]);
                if (raum == null) {
                    continue;
                }

                int belegt = 0;
                for (int pIdx = 0; pIdx < tnSize; pIdx++) {
                    if (besucht[pIdx][wIdx][iIdx]) {
                        belegt++;
                    }
                }
                restkapazitaet[wIdx][iIdx] = raum.getKapazitaet() - belegt;

                if (restkapazitaet[wIdx][iIdx] > 0) {
                    instanzenProSlot.computeIfAbsent(slotIdx1, k -> new ArrayList<>()).add(new Instanz(wIdx, iIdx));
                }
            }
        }

        // Bereits (vom Solver) zugewiesene Wahlvorträge je Teilnehmer zählen, um maxWvsProTn einzuhalten.
        int[] besuchteAnzahl = new int[tnSize];
        for (int pIdx = 0; pIdx < tnSize; pIdx++) {
            for (int wIdx = 0; wIdx < wvSize; wIdx++) {
                if (besuchtWahlvortragBereits(besucht, pIdx, wIdx, maxInstanzen)) {
                    besuchteAnzahl[pIdx]++;
                }
            }
        }

        for (int slotIdx1 = 1; slotIdx1 <= slotSize; slotIdx1++) {
            List<Instanz> kandidatenImSlot = instanzenProSlot.get(slotIdx1);
            if (null == kandidatenImSlot || kandidatenImSlot.isEmpty()) {
                continue;
            }
            long slotOid = slotOids[slotIdx1 - 1];

            for (int pIdx = 0; pIdx < tnSize; pIdx++) {
                if (maxWvsProTn > 0 && besuchteAnzahl[pIdx] >= maxWvsProTn) {
                    continue;
                }
                if (!istFreiInSlot(pIdx, slotIdx1, slotOid, wvSize, maxInstanzen, instanzSlot, besucht,
                    verfuegbarkeitByNutzerId, tnOids)) {
                    continue;
                }

                List<Instanz> eligibleKandidaten = new ArrayList<>();
                for (Instanz kandidat : kandidatenImSlot) {
                    if (restkapazitaet[kandidat.wvIdx()][kandidat.instIdx()] <= 0) {
                        continue;
                    }
                    if (besuchtWahlvortragBereits(besucht, pIdx, kandidat.wvIdx(), maxInstanzen)) {
                        continue;
                    }
                    eligibleKandidaten.add(kandidat);
                }

                if (eligibleKandidaten.isEmpty()) {
                    continue;
                }

                long tnOid = tnOids[pIdx];
                List<Prioritaet> eigenePrioritaeten = eigenePrioritaetenByTnOid.getOrDefault(tnOid, List.of());
                Instanz gewaehlt = waehleKandidat(eigenePrioritaeten, eligibleKandidaten, wvOids, wahlvortragByOid);

                besucht[pIdx][gewaehlt.wvIdx()][gewaehlt.instIdx()] = true;
                restkapazitaet[gewaehlt.wvIdx()][gewaehlt.instIdx()]--;
                besuchteAnzahl[pIdx]++;
            }
        }
    }


    private boolean istFreiInSlot(int pIdx, int slotIdx1, long slotOid, int wvSize, int maxInstanzen,
                                  int[][] instanzSlot, boolean[][][] besucht,
                                  Map<Long, NutzerVerfuegbarkeit> verfuegbarkeitByNutzerId, long[] tnOids) {
        NutzerVerfuegbarkeit verfuegbarkeit = verfuegbarkeitByNutzerId.get(tnOids[pIdx]);
        if (null == verfuegbarkeit || !verfuegbarkeit.getVerfuegbareSlotIds().contains(slotOid)) {
            return false;
        }

        for (int wIdx = 0; wIdx < wvSize; wIdx++) {
            for (int iIdx = 0; iIdx < maxInstanzen; iIdx++) {
                if (instanzSlot[wIdx][iIdx] == slotIdx1 && besucht[pIdx][wIdx][iIdx]) {
                    return false;
                }
            }
        }
        return true;
    }


    private boolean besuchtWahlvortragBereits(boolean[][][] besucht, int pIdx, int wvIdx, int maxInstanzen) {
        for (int iIdx = 0; iIdx < maxInstanzen; iIdx++) {
            if (besucht[pIdx][wvIdx][iIdx]) {
                return true;
            }
        }
        return false;
    }


    private Instanz waehleKandidat(List<Prioritaet> eigenePrioritaeten, List<Instanz> eligibleKandidaten,
                                   long[] wvOids, Map<Long, Wahlvortrag> wahlvortragByOid) {
        // Tier 1: eigene weitere Prioritäten (beste zuerst), sofern für diesen Slot noch Kapazität besteht.
        for (Prioritaet prioritaet : eigenePrioritaeten) {
            long wvOid = prioritaet.getVortrag().getId();
            List<Instanz> treffer = eligibleKandidaten.stream()
                .filter(k -> wvOids[k.wvIdx()] == wvOid)
                .toList();
            if (!treffer.isEmpty()) {
                return treffer.get(ThreadLocalRandom.current().nextInt(treffer.size()));
            }
        }

        // Tier 2: Kandidat teilt mindestens eine Veranlagung mit einem priorisierten Vortrag.
        List<Veranlagung> praeferierteVeranlagungen = eigenePrioritaeten.stream()
            .flatMap(p -> p.getVortrag().getVeranlagungen().stream())
            .distinct()
            .toList();
        for (Veranlagung veranlagung : praeferierteVeranlagungen) {
            List<Instanz> treffer = eligibleKandidaten.stream()
                .filter(k -> wahlvortragByOid.get(wvOids[k.wvIdx()]).getVeranlagungen().contains(veranlagung))
                .toList();
            if (!treffer.isEmpty()) {
                return treffer.get(ThreadLocalRandom.current().nextInt(treffer.size()));
            }
        }

        // Tier 3: zufällige Auswahl unter allen verbleibenden Kandidaten.
        return eligibleKandidaten.get(ThreadLocalRandom.current().nextInt(eligibleKandidaten.size()));
    }
}
