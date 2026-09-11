package kreyj.konfplan.domain.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import kreyj.konfplan.adapter.in.web.dto.NachbuchungsVorschlagDto;
import kreyj.konfplan.adapter.in.web.dto.TeilnehmerAktuelleZuweisungDto;
import kreyj.konfplan.adapter.in.web.dto.TeilnehmerUmbuchenAnfrageDto;
import kreyj.konfplan.adapter.in.web.dto.UmbuchungOptionDto;
import kreyj.konfplan.adapter.in.web.dto.UmplanungErgebnisDto;
import kreyj.konfplan.domain.exception.BusinessException;
import kreyj.konfplan.persistence.IdEntity;
import kreyj.konfplan.persistence.NachrichtKategorie;
import kreyj.konfplan.persistence.Organisator;
import kreyj.konfplan.persistence.Planungsergebnis;
import kreyj.konfplan.persistence.Prioritaet;
import kreyj.konfplan.persistence.Raum;
import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Slot;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.persistence.Wahlvortrag;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;

/**
 * Kurzfristige Korrektur eines bestehenden Planungsergebnisses: wenn ein Referent nach der
 * Planerstellung ausfällt, markiert diese Klasse eine einzelne Wahlvortrag-Instanz als
 * ausgefallen und verteilt deren bereits zugewiesene Teilnehmer - passend zu ihren Neigungen und
 * nur soweit Raumkapazität frei ist - auf andere Instanzen im selben Zeitslot. Im Gegensatz zu
 * {@link AuffuellungService} (läuft einmalig direkt nach der MiniZinc-Berechnung, vor dem
 * Speichern) patcht dieser Service ein BEREITS gespeichertes, ggf. veröffentlichtes
 * Planungsergebnis in-place.
 */
@ApplicationScoped
public class UmplanungService {
    private static final Logger LOG = Logger.getLogger(UmplanungService.class);

    private final PlanService planService;
    private final NachrichtService nachrichtService;


    public UmplanungService(PlanService planService, NachrichtService nachrichtService) {
        this.planService = planService;
        this.nachrichtService = nachrichtService;
    }


    private record Instanz(int wvIdx, int instIdx) {
    }


    @Transactional
    public UmplanungErgebnisDto vortragsInstanzUmplanen(Veranstaltung veranstaltung, Long ergebnisId,
                                                         Long wahlvortragId, int instanzIndex, String username) {
        Planungsergebnis ergebnis = planService.ladeErgebnisFuer(veranstaltung, ergebnisId);

        // Bewusst NICHT über planService.getMinizincResult(...): das liefert ein gecachtes,
        // ggf. geteiltes MinizincResult-Objekt zurück. Würde hier direkt hineingeschrieben und
        // schlägt die Verteilung später fehl (Exception), bliebe der Cache dauerhaft auf einem
        // nie persistierten Zwischenstand hängen (Invalidierung erfolgt nur über einen
        // @Version-Vergleich, der ohne echtes Speichern nie eintritt). Deshalb hier immer frisch
        // deserialisieren und erst nach vollständigem Erfolg zurückschreiben.
        Planungsergebnis.MinizincResult result = deserialisiere(ergebnis);

        long[] tnOids = result.teilnehmer_oids;
        long[] wvOids = result.wahlvortrag_oids;
        boolean[][][] besucht = result.besucht;
        int[][] instanzSlot = result.instanz_slot;
        int maxInstanzen = instanzSlot.length > 0 ? instanzSlot[0].length : 0;

        int wvIdx = indexOf(wvOids, wahlvortragId);
        if (wvIdx < 0) {
            throw new BusinessException("Wahlvortrag ist nicht Teil dieses Planungsergebnisses.");
        }
        if (instanzIndex < 0 || instanzIndex >= maxInstanzen || instanzSlot[wvIdx][instanzIndex] <= 0) {
            throw new BusinessException("Instanz nicht gefunden.");
        }
        if (result.istAusgefallen(wvIdx, instanzIndex)) {
            throw new BusinessException("Diese Instanz wurde bereits als ausgefallen markiert.");
        }

        int slotIdx1 = instanzSlot[wvIdx][instanzIndex];

        Map<Long, Teilnehmer> teilnehmerByOid = veranstaltung.teilnehmer().stream()
            .collect(toMap(IdEntity::getId, Function.identity()));
        Map<Long, Wahlvortrag> wahlvortragByOid = veranstaltung.getWahlvortraege().stream()
            .collect(toMap(IdEntity::getId, Function.identity()));
        Map<Long, Raum> raumByOid = veranstaltung.getRaeume().stream()
            .collect(toMap(IdEntity::getId, Function.identity()));

        // Betroffene Teilnehmer befreien.
        List<Integer> betroffenePIdx = new ArrayList<>();
        for (int pIdx = 0; pIdx < tnOids.length; pIdx++) {
            if (besucht[pIdx][wvIdx][instanzIndex]) {
                betroffenePIdx.add(pIdx);
                besucht[pIdx][wvIdx][instanzIndex] = false;
            }
        }

        if (null == result.instanz_ausgefallen) {
            result.instanz_ausgefallen = new boolean[wvOids.length][maxInstanzen];
        }
        result.instanz_ausgefallen[wvIdx][instanzIndex] = true;

        // Kandidaten-Instanzen im selben Slot mit ihrer aktuellen Restkapazität ermitteln.
        Map<Instanz, Integer> restkapazitaet = new HashMap<>();
        for (int wIdx2 = 0; wIdx2 < wvOids.length; wIdx2++) {
            for (int iIdx2 = 0; iIdx2 < instanzSlot[wIdx2].length; iIdx2++) {
                if (wIdx2 == wvIdx && iIdx2 == instanzIndex) {
                    continue;
                }
                if (instanzSlot[wIdx2][iIdx2] != slotIdx1 || result.istAusgefallen(wIdx2, iIdx2)) {
                    continue;
                }
                int rIdx = result.instanz_raum[wIdx2][iIdx2] - 1;
                if (rIdx < 0) {
                    continue;
                }
                Raum raum = raumByOid.get(result.raum_oids[rIdx]);
                if (null == raum) {
                    continue;
                }
                int belegt = 0;
                for (int pIdx = 0; pIdx < tnOids.length; pIdx++) {
                    if (besucht[pIdx][wIdx2][iIdx2]) {
                        belegt++;
                    }
                }
                restkapazitaet.put(new Instanz(wIdx2, iIdx2), raum.getKapazitaet() - belegt);
            }
        }

        Wahlvortrag ausgefallenerVortrag = wahlvortragByOid.get(wahlvortragId);
        List<UmplanungErgebnisDto.UmverteilterTeilnehmerDto> umverteilt = new ArrayList<>();
        List<Teilnehmer> nichtPlatzierteTeilnehmer = new ArrayList<>();
        List<Teilnehmer> umverteilteTeilnehmer = new ArrayList<>();
        List<String> neueVortragTitelProTeilnehmer = new ArrayList<>();

        for (int pIdx : betroffenePIdx) {
            Teilnehmer teilnehmer = teilnehmerByOid.get(tnOids[pIdx]);
            if (null == teilnehmer) {
                continue;
            }

            List<Instanz> eligibleKandidaten = restkapazitaet.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .map(Map.Entry::getKey)
                .filter(k -> !besuchtWahlvortragBereits(besucht, pIdx, k.wvIdx(), maxInstanzen))
                .toList();

            if (eligibleKandidaten.isEmpty()) {
                nichtPlatzierteTeilnehmer.add(teilnehmer);
                continue;
            }

            Instanz gewaehlt = waehleBestenKandidaten(teilnehmer, eligibleKandidaten, wvOids, wahlvortragByOid, restkapazitaet);

            besucht[pIdx][gewaehlt.wvIdx()][gewaehlt.instIdx()] = true;
            restkapazitaet.merge(gewaehlt, -1, Integer::sum);

            String neuerVortragTitel = wahlvortragByOid.get(wvOids[gewaehlt.wvIdx()]).getTitel();
            umverteilt.add(new UmplanungErgebnisDto.UmverteilterTeilnehmerDto(teilnehmer.getFullName(), neuerVortragTitel));
            umverteilteTeilnehmer.add(teilnehmer);
            neueVortragTitelProTeilnehmer.add(neuerVortragTitel);
        }

        ergebnis.setJsonErgebnis(result.toJson());

        benachrichtigeBeteiligte(veranstaltung, ausgefallenerVortrag, umverteilteTeilnehmer,
            neueVortragTitelProTeilnehmer, nichtPlatzierteTeilnehmer, username);

        LOG.infof("Kurzfristige Umplanung für Wahlvortrag '%s' (Instanz %d) in Veranstaltung '%s': %d umverteilt, %d nicht platziert.",
            ausgefallenerVortrag.getTitel(), instanzIndex, veranstaltung.getName(), umverteilt.size(), nichtPlatzierteTeilnehmer.size());

        List<String> nichtPlatziertNamen = nichtPlatzierteTeilnehmer.stream().map(Teilnehmer::getFullName).toList();
        return new UmplanungErgebnisDto(umverteilt, nichtPlatziertNamen);
    }


    private Planungsergebnis.MinizincResult deserialisiere(Planungsergebnis ergebnis) {
        return Planungsergebnis.MinizincResult.fromJson(ergebnis.getJsonErgebnis());
    }


    /**
     * Liefert die aktuellen Wahlvortrag-Zuweisungen eines einzelnen Teilnehmers in einem
     * bestimmten Planungsergebnis, für die manuelle Einzel-Umbuchung im ErgebnisseTab (z.B. wenn
     * ein Teilnehmer nachträglich einen unpassenden Wahlvortrag priorisiert hat und die
     * Teilnehmer-Deadline bereits abgelaufen ist).
     */
    @Transactional
    public List<TeilnehmerAktuelleZuweisungDto> getAktuelleZuweisungen(Veranstaltung veranstaltung, Long ergebnisId, Long teilnehmerId) {
        Planungsergebnis ergebnis = planService.ladeErgebnisFuer(veranstaltung, ergebnisId);
        Planungsergebnis.MinizincResult result = planService.getMinizincResult(ergebnis);

        long[] tnOids = result.teilnehmer_oids;
        int pIdx = indexOf(tnOids, teilnehmerId);
        if (pIdx < 0) {
            return List.of();
        }

        long[] wvOids = result.wahlvortrag_oids;
        long[] slotOids = result.slot_oids;
        long[] raumOids = result.raum_oids;
        boolean[][][] besucht = result.besucht;
        int[][] instanzSlot = result.instanz_slot;
        int[][] instanzRaum = result.instanz_raum;

        Map<Long, Wahlvortrag> wahlvortragByOid = veranstaltung.getWahlvortraege().stream().collect(toMap(IdEntity::getId, Function.identity()));
        Map<Long, Slot> slotByOid = veranstaltung.getSlots().stream().collect(toMap(IdEntity::getId, Function.identity()));
        Map<Long, Raum> raumByOid = veranstaltung.getRaeume().stream().collect(toMap(IdEntity::getId, Function.identity()));

        List<TeilnehmerAktuelleZuweisungDto> zuweisungen = new ArrayList<>();
        for (int wvIdx = 0; wvIdx < wvOids.length; wvIdx++) {
            Wahlvortrag vortrag = wahlvortragByOid.get(wvOids[wvIdx]);
            if (null == vortrag) {
                continue;
            }
            for (int iIdx = 0; iIdx < instanzSlot[wvIdx].length; iIdx++) {
                if (!besucht[pIdx][wvIdx][iIdx]) {
                    continue;
                }
                int sIdx = instanzSlot[wvIdx][iIdx] - 1;
                int rIdx = instanzRaum[wvIdx][iIdx] - 1;
                if (sIdx < 0 || rIdx < 0) {
                    continue;
                }
                Slot slot = slotByOid.get(slotOids[sIdx]);
                Raum raum = raumByOid.get(raumOids[rIdx]);
                if (null == slot || null == raum) {
                    continue;
                }
                zuweisungen.add(new TeilnehmerAktuelleZuweisungDto(
                    vortrag.getId(), iIdx, vortrag.getTitel(),
                    slot.getStartTime().format(PlanService.TIME_FORMAT), raum.getName()));
            }
        }
        return zuweisungen;
    }


    /**
     * Liefert alternative Wahlvortrag-Instanzen im selben Zeitslot wie die angegebene aktuelle
     * Zuweisung, mit freier Raumkapazität, absteigend sortiert nach Neigungs-Übereinstimmung mit
     * dem Teilnehmer - als Entscheidungshilfe für den Organisator, um den Teilnehmer bei der Wahl
     * einer passenden Alternative zu beraten.
     */
    @Transactional
    public List<UmbuchungOptionDto> getUmbuchungsOptionen(Veranstaltung veranstaltung, Long ergebnisId, Long teilnehmerId,
                                                            Long altWahlvortragId, int altInstanzIndex) {
        Planungsergebnis ergebnis = planService.ladeErgebnisFuer(veranstaltung, ergebnisId);
        Planungsergebnis.MinizincResult result = planService.getMinizincResult(ergebnis);

        long[] tnOids = result.teilnehmer_oids;
        int pIdx = indexOf(tnOids, teilnehmerId);
        if (pIdx < 0) {
            throw new BusinessException("Teilnehmer ist nicht Teil dieses Planungsergebnisses.");
        }

        long[] wvOids = result.wahlvortrag_oids;
        int[][] instanzSlot = result.instanz_slot;

        int altWvIdx = indexOf(wvOids, altWahlvortragId);
        if (altWvIdx < 0 || altInstanzIndex < 0 || altInstanzIndex >= instanzSlot[altWvIdx].length) {
            throw new BusinessException("Aktuelle Zuweisung nicht gefunden.");
        }
        int slotIdx1 = instanzSlot[altWvIdx][altInstanzIndex];

        Teilnehmer teilnehmer = ladeTeilnehmer(veranstaltung, teilnehmerId);
        Map<Long, Wahlvortrag> wahlvortragByOid = veranstaltung.getWahlvortraege().stream().collect(toMap(IdEntity::getId, Function.identity()));
        Map<Long, Raum> raumByOid = veranstaltung.getRaeume().stream().collect(toMap(IdEntity::getId, Function.identity()));

        List<UmbuchungOptionDto> optionen = kandidatenInSlot(result, teilnehmer, pIdx, slotIdx1, altWvIdx, altInstanzIndex,
            wahlvortragByOid, raumByOid);

        return optionen.stream()
            .sorted(Comparator.comparingInt((UmbuchungOptionDto o) -> o.neigungsUeberschneidung).reversed()
                .thenComparing(Comparator.comparingInt((UmbuchungOptionDto o) -> o.kapazitaet - o.belegteAnzahl).reversed()))
            .toList();
    }


    /**
     * Bucht einen einzelnen Teilnehmer manuell von einer Wahlvortrag-Instanz auf eine andere im
     * selben Zeitslot um - im Gegensatz zu {@link #vortragsInstanzUmplanen} (verteilt ALLE
     * Teilnehmer einer ausgefallenen Instanz automatisch) hier gezielt für genau einen Teilnehmer
     * und eine vom Organisator (nach Beratung mit dem Teilnehmern) gewählte Ziel-Instanz.
     */
    @Transactional
    public void teilnehmerUmbuchen(Veranstaltung veranstaltung, Long ergebnisId, Long teilnehmerId,
                                    TeilnehmerUmbuchenAnfrageDto anfrage, String username) {
        Planungsergebnis ergebnis = planService.ladeErgebnisFuer(veranstaltung, ergebnisId);
        // Bewusst frisch deserialisiert statt über den Cache - siehe Kommentar in
        // vortragsInstanzUmplanen.
        Planungsergebnis.MinizincResult result = deserialisiere(ergebnis);

        long[] tnOids = result.teilnehmer_oids;
        int pIdx = indexOf(tnOids, teilnehmerId);
        if (pIdx < 0) {
            throw new BusinessException("Teilnehmer ist nicht Teil dieses Planungsergebnisses.");
        }

        long[] wvOids = result.wahlvortrag_oids;
        long[] raumOids = result.raum_oids;
        boolean[][][] besucht = result.besucht;
        int[][] instanzSlot = result.instanz_slot;
        int[][] instanzRaum = result.instanz_raum;
        int maxInstanzen = instanzSlot[0].length;

        int altWvIdx = indexOf(wvOids, anfrage.altWahlvortragId);
        int neuWvIdx = indexOf(wvOids, anfrage.neuWahlvortragId);
        if (altWvIdx < 0 || neuWvIdx < 0) {
            throw new BusinessException("Wahlvortrag ist nicht Teil dieses Planungsergebnisses.");
        }
        if (anfrage.altInstanzIndex < 0 || anfrage.altInstanzIndex >= instanzSlot[altWvIdx].length
            || !besucht[pIdx][altWvIdx][anfrage.altInstanzIndex]) {
            throw new BusinessException("Teilnehmer ist der angegebenen aktuellen Zuweisung nicht zugeteilt.");
        }
        if (anfrage.neuInstanzIndex < 0 || anfrage.neuInstanzIndex >= instanzSlot[neuWvIdx].length
            || instanzSlot[neuWvIdx][anfrage.neuInstanzIndex] <= 0) {
            throw new BusinessException("Ziel-Instanz nicht gefunden.");
        }
        if (result.istAusgefallen(neuWvIdx, anfrage.neuInstanzIndex)) {
            throw new BusinessException("Die Ziel-Instanz wurde als ausgefallen markiert.");
        }
        if (instanzSlot[altWvIdx][anfrage.altInstanzIndex] != instanzSlot[neuWvIdx][anfrage.neuInstanzIndex]) {
            throw new BusinessException("Umbuchung ist nur innerhalb desselben Zeitslots möglich.");
        }
        if (besuchtWahlvortragBereits(besucht, pIdx, neuWvIdx, maxInstanzen)) {
            throw new BusinessException("Teilnehmer ist diesem Wahlvortrag bereits in einer anderen Instanz zugeteilt.");
        }

        Map<Long, Raum> raumByOid = veranstaltung.getRaeume().stream().collect(toMap(IdEntity::getId, Function.identity()));
        int rIdx = instanzRaum[neuWvIdx][anfrage.neuInstanzIndex] - 1;
        Raum zielRaum = rIdx < 0 ? null : raumByOid.get(raumOids[rIdx]);
        if (null == zielRaum) {
            throw new BusinessException("Raum der Ziel-Instanz nicht gefunden.");
        }
        int belegteAnzahl = 0;
        for (int p2 = 0; p2 < tnOids.length; p2++) {
            if (besucht[p2][neuWvIdx][anfrage.neuInstanzIndex]) {
                belegteAnzahl++;
            }
        }
        if (belegteAnzahl >= zielRaum.getKapazitaet()) {
            throw new BusinessException("Ziel-Instanz hat keinen freien Platz mehr.");
        }

        besucht[pIdx][altWvIdx][anfrage.altInstanzIndex] = false;
        besucht[pIdx][neuWvIdx][anfrage.neuInstanzIndex] = true;
        ergebnis.setJsonErgebnis(result.toJson());

        Teilnehmer teilnehmer = veranstaltung.teilnehmer().stream()
            .filter(t -> t.getId().equals(teilnehmerId)).findFirst().orElse(null);
        Map<Long, Wahlvortrag> wahlvortragByOid = veranstaltung.getWahlvortraege().stream().collect(toMap(IdEntity::getId, Function.identity()));
        Wahlvortrag altVortrag = wahlvortragByOid.get(anfrage.altWahlvortragId);
        Wahlvortrag neuVortrag = wahlvortragByOid.get(anfrage.neuWahlvortragId);
        if (null != teilnehmer && null != altVortrag && null != neuVortrag) {
            String inhalt = "Du wurdest von '" + altVortrag.getTitel() + "' auf '" + neuVortrag.getTitel() + "' umgebucht.";
            nachrichtService.sendeNachricht(teilnehmer, "Deine Wahlvortrag-Zuteilung wurde geändert", inhalt,
                NachrichtKategorie.TEILNEHMER_UMGEBUCHT, veranstaltung.getId(), username);
        }

        LOG.infof("Teilnehmer %d manuell umgebucht von Wahlvortrag %d (Instanz %d) auf Wahlvortrag %d (Instanz %d) in Veranstaltung '%s'.",
            teilnehmerId, anfrage.altWahlvortragId, anfrage.altInstanzIndex, anfrage.neuWahlvortragId, anfrage.neuInstanzIndex, veranstaltung.getName());
    }


    /**
     * Gibt die Sitzplätze eines Teilnehmers in den angegebenen, neu als nicht verfügbar
     * markierten Zeitslots frei (z.B. Krankmeldung) - nur relevant, falls es für die Veranstaltung
     * bereits ein VERÖFFENTLICHTES Planungsergebnis gibt (sonst wirkt die Verfügbarkeit ohnehin
     * erst als Eingabe für den nächsten Planungslauf, siehe {@code OrganisatorResource#updateVerfuegbarkeit}).
     * Anders als {@link #teilnehmerUmbuchen} wird der frei werdende Platz NICHT automatisch neu
     * belegt - das wäre bei nur einem betroffenen Teilnehmer keine "Umverteilung" (dafür gibt es
     * niemanden), sondern eine bewusste separate Organisator-Entscheidung. Pflichtvorträge sind
     * nicht betroffen - die sind gruppen-/slotbasiert fix zugeteilt, nicht einzeln kapazitätsgezählt.
     */
    @Transactional
    public void freigebenBeiNichtVerfuegbarkeit(Veranstaltung veranstaltung, Teilnehmer teilnehmer,
                                                  Set<Long> neuNichtVerfuegbareSlotIds, String username) {
        if (neuNichtVerfuegbareSlotIds.isEmpty()) {
            return;
        }
        Planungsergebnis ergebnis = Planungsergebnis.getPlanungsergebnis(veranstaltung);
        if (null == ergebnis) {
            return;
        }
        Planungsergebnis.MinizincResult result = deserialisiere(ergebnis);

        long[] tnOids = result.teilnehmer_oids;
        int pIdx = indexOf(tnOids, teilnehmer.getId());
        if (pIdx < 0) {
            return;
        }

        long[] wvOids = result.wahlvortrag_oids;
        long[] slotOids = result.slot_oids;
        boolean[][][] besucht = result.besucht;
        int[][] instanzSlot = result.instanz_slot;

        Map<Long, Wahlvortrag> wahlvortragByOid = veranstaltung.getWahlvortraege().stream().collect(toMap(IdEntity::getId, Function.identity()));

        boolean geaendert = false;
        for (int wvIdx = 0; wvIdx < wvOids.length; wvIdx++) {
            for (int iIdx = 0; iIdx < instanzSlot[wvIdx].length; iIdx++) {
                if (!besucht[pIdx][wvIdx][iIdx]) {
                    continue;
                }
                int sIdx = instanzSlot[wvIdx][iIdx] - 1;
                if (sIdx < 0 || sIdx >= slotOids.length || !neuNichtVerfuegbareSlotIds.contains(slotOids[sIdx])) {
                    continue;
                }

                besucht[pIdx][wvIdx][iIdx] = false;
                geaendert = true;

                Wahlvortrag vortrag = wahlvortragByOid.get(wvOids[wvIdx]);
                if (null != vortrag) {
                    LOG.infof("Sitzplatz für Teilnehmer %d in Wahlvortrag '%s' (Instanz %d) freigegeben - "
                            + "in Veranstaltung '%s' nicht mehr verfügbar.",
                        teilnehmer.getId(), vortrag.getTitel(), iIdx, veranstaltung.getName());
                    Referent referent = vortrag.getReferent();
                    if (null != referent) {
                        String inhalt = "Teilnehmer " + teilnehmer.getFullName() + " wurde als nicht verfügbar markiert "
                            + "und aus deinem Wahlvortrag '" + vortrag.getTitel() + "' ausgetragen.";
                        nachrichtService.sendeNachricht(referent, "Teilnehmerzahl in deinem Vortrag geändert", inhalt,
                            NachrichtKategorie.TEILNEHMER_NICHT_VERFUEGBAR, veranstaltung.getId(), username);
                    }
                }
            }
        }

        if (geaendert) {
            ergebnis.setJsonErgebnis(result.toJson());
        }
    }


    /**
     * Ermittelt für neu verfügbar gewordene Zeitslots eines Teilnehmers (z.B. Krankmeldung wieder
     * zurückgenommen) passende Wahlvortrag-Instanzen mit freier Kapazität - absteigend sortiert
     * nach unerfüllter Priorität (Wahlvortraege, die der Teilnehmer selbst priorisiert, aber nicht
     * zugeteilt bekommen hat, zuerst) und danach nach Neigungs-Übereinstimmung. Dient als
     * Entscheidungshilfe für den Organisator im modalen Nachbuchungs-Dialog - berücksichtigt nur
     * Slots, in denen der Teilnehmer aktuell weder einem Wahl- noch einem Pflichtvortrag zugeteilt
     * ist, und nur, falls bereits ein VERÖFFENTLICHTES Planungsergebnis existiert.
     */
    @Transactional
    public List<NachbuchungsVorschlagDto> ermittleNachbuchungsVorschlaege(Veranstaltung veranstaltung, Teilnehmer teilnehmer,
                                                                            Set<Long> neuVerfuegbareSlotIds) {
        if (neuVerfuegbareSlotIds.isEmpty()) {
            return List.of();
        }
        Planungsergebnis ergebnis = Planungsergebnis.getPlanungsergebnis(veranstaltung);
        if (null == ergebnis) {
            return List.of();
        }
        Planungsergebnis.MinizincResult result = planService.getMinizincResult(ergebnis);

        long[] tnOids = result.teilnehmer_oids;
        int pIdx = indexOf(tnOids, teilnehmer.getId());
        if (pIdx < 0) {
            return List.of();
        }

        long[] slotOids = result.slot_oids;
        boolean[][][] besucht = result.besucht;
        int[][] instanzSlot = result.instanz_slot;

        Map<Long, Wahlvortrag> wahlvortragByOid = veranstaltung.getWahlvortraege().stream().collect(toMap(IdEntity::getId, Function.identity()));
        Map<Long, Raum> raumByOid = veranstaltung.getRaeume().stream().collect(toMap(IdEntity::getId, Function.identity()));

        List<NachbuchungsVorschlagDto> vorschlaege = new ArrayList<>();
        for (Long slotId : neuVerfuegbareSlotIds) {
            int slotIdx0 = indexOf(slotOids, slotId);
            if (slotIdx0 < 0) {
                // Slot ist nicht Teil dieses Planungsergebnisses (z.B. erst danach angelegt).
                continue;
            }
            int slotIdx1 = slotIdx0 + 1;

            if (besuchtIrgendeinWahlvortragInSlot(besucht, pIdx, instanzSlot, slotIdx1)
                || pflichtvortragDecktSlotAb(veranstaltung, teilnehmer, slotId)) {
                continue;
            }

            List<UmbuchungOptionDto> optionen = kandidatenInSlot(result, teilnehmer, pIdx, slotIdx1, -1, -1, wahlvortragByOid, raumByOid)
                .stream()
                .sorted(Comparator.comparingInt((UmbuchungOptionDto o) -> o.prioWert).reversed()
                    .thenComparing(Comparator.comparingInt((UmbuchungOptionDto o) -> o.neigungsUeberschneidung).reversed())
                    .thenComparing(Comparator.comparingInt((UmbuchungOptionDto o) -> o.kapazitaet - o.belegteAnzahl).reversed()))
                .toList();

            Slot slot = Slot.findById(slotId);
            vorschlaege.add(new NachbuchungsVorschlagDto(slotId,
                null == slot ? "" : slot.getStartTime().format(PlanService.TIME_FORMAT), optionen));
        }
        return vorschlaege;
    }


    /**
     * Bucht einen Teilnehmer, der bei bereits veröffentlichtem Plan wieder verfügbar gemeldet
     * wurde, in eine vom Organisator ausgewählte Wahlvortrag-Instanz (siehe
     * {@link #ermittleNachbuchungsVorschlaege}) - reine Neuzuteilung ohne vorherige Zuweisung, im
     * Gegensatz zu {@link #teilnehmerUmbuchen} (Wechsel zwischen zwei bestehenden Zuweisungen).
     */
    @Transactional
    public void teilnehmerNachbuchen(Veranstaltung veranstaltung, Teilnehmer teilnehmer, Long wahlvortragId, int instanzIndex, String username) {
        Planungsergebnis ergebnis = Planungsergebnis.getPlanungsergebnis(veranstaltung);
        if (null == ergebnis) {
            throw new BusinessException("Für diese Veranstaltung liegt kein veröffentlichtes Planungsergebnis vor.");
        }
        // Bewusst frisch deserialisiert statt über den Cache - siehe Kommentar in
        // vortragsInstanzUmplanen.
        Planungsergebnis.MinizincResult result = deserialisiere(ergebnis);

        long[] tnOids = result.teilnehmer_oids;
        int pIdx = indexOf(tnOids, teilnehmer.getId());
        if (pIdx < 0) {
            throw new BusinessException("Teilnehmer ist nicht Teil dieses Planungsergebnisses.");
        }

        long[] wvOids = result.wahlvortrag_oids;
        long[] raumOids = result.raum_oids;
        boolean[][][] besucht = result.besucht;
        int[][] instanzSlot = result.instanz_slot;
        int[][] instanzRaum = result.instanz_raum;
        int maxInstanzen = instanzSlot[0].length;

        int wvIdx = indexOf(wvOids, wahlvortragId);
        if (wvIdx < 0 || instanzIndex < 0 || instanzIndex >= instanzSlot[wvIdx].length || instanzSlot[wvIdx][instanzIndex] <= 0) {
            throw new BusinessException("Wahlvortrag-Instanz nicht gefunden.");
        }
        if (result.istAusgefallen(wvIdx, instanzIndex)) {
            throw new BusinessException("Diese Instanz wurde als ausgefallen markiert.");
        }
        if (besuchtWahlvortragBereits(besucht, pIdx, wvIdx, maxInstanzen)) {
            throw new BusinessException("Teilnehmer ist diesem Wahlvortrag bereits in einer anderen Instanz zugeteilt.");
        }

        Map<Long, Raum> raumByOid = veranstaltung.getRaeume().stream().collect(toMap(IdEntity::getId, Function.identity()));
        int rIdx = instanzRaum[wvIdx][instanzIndex] - 1;
        Raum raum = rIdx < 0 ? null : raumByOid.get(raumOids[rIdx]);
        if (null == raum) {
            throw new BusinessException("Raum der Instanz nicht gefunden.");
        }
        int belegteAnzahl = 0;
        for (int p2 = 0; p2 < tnOids.length; p2++) {
            if (besucht[p2][wvIdx][instanzIndex]) {
                belegteAnzahl++;
            }
        }
        if (belegteAnzahl >= raum.getKapazitaet()) {
            throw new BusinessException("Instanz hat keinen freien Platz mehr.");
        }

        besucht[pIdx][wvIdx][instanzIndex] = true;
        ergebnis.setJsonErgebnis(result.toJson());

        Map<Long, Wahlvortrag> wahlvortragByOid = veranstaltung.getWahlvortraege().stream().collect(toMap(IdEntity::getId, Function.identity()));
        Wahlvortrag vortrag = wahlvortragByOid.get(wahlvortragId);
        if (null != vortrag) {
            String inhalt = "Du bist wieder als verfügbar gemeldet und wurdest dem Wahlvortrag '"
                + vortrag.getTitel() + "' zugeteilt.";
            nachrichtService.sendeNachricht(teilnehmer, "Du wurdest einem Wahlvortrag zugeteilt", inhalt,
                NachrichtKategorie.TEILNEHMER_NACHGEBUCHT, veranstaltung.getId(), username);
        }

        LOG.infof("Teilnehmer %d nachgebucht in Wahlvortrag %d (Instanz %d) in Veranstaltung '%s'.",
            teilnehmer.getId(), wahlvortragId, instanzIndex, veranstaltung.getName());
    }


    private Instanz waehleBestenKandidaten(Teilnehmer teilnehmer, List<Instanz> eligibleKandidaten, long[] wvOids,
                                           Map<Long, Wahlvortrag> wahlvortragByOid, Map<Instanz, Integer> restkapazitaet) {
        int besteUeberschneidung = -1;
        List<Instanz> beste = new ArrayList<>();
        for (Instanz kandidat : eligibleKandidaten) {
            Wahlvortrag kandidatVortrag = wahlvortragByOid.get(wvOids[kandidat.wvIdx()]);
            long ueberschneidung = kandidatVortrag.getNeigungen().stream()
                .filter(teilnehmer.getNeigungen()::contains)
                .count();
            if (ueberschneidung > besteUeberschneidung) {
                besteUeberschneidung = (int) ueberschneidung;
                beste.clear();
                beste.add(kandidat);
            } else if (ueberschneidung == besteUeberschneidung) {
                beste.add(kandidat);
            }
        }

        // Bei Gleichstand (auch bei 0 gemeinsamen Neigungen): meiste Restkapazität zuerst
        // (Lastverteilung), danach zufällig.
        int maxRestkapazitaet = beste.stream().mapToInt(restkapazitaet::get).max().orElse(0);
        List<Instanz> besteMitKapazitaet = beste.stream().filter(k -> restkapazitaet.get(k) == maxRestkapazitaet).toList();

        return besteMitKapazitaet.get(ThreadLocalRandom.current().nextInt(besteMitKapazitaet.size()));
    }


    private boolean besuchtWahlvortragBereits(boolean[][][] besucht, int pIdx, int wvIdx, int maxInstanzen) {
        for (int iIdx = 0; iIdx < maxInstanzen; iIdx++) {
            if (besucht[pIdx][wvIdx][iIdx]) {
                return true;
            }
        }
        return false;
    }


    private boolean besuchtIrgendeinWahlvortragInSlot(boolean[][][] besucht, int pIdx, int[][] instanzSlot, int slotIdx1) {
        for (int wIdx = 0; wIdx < instanzSlot.length; wIdx++) {
            for (int iIdx = 0; iIdx < instanzSlot[wIdx].length; iIdx++) {
                if (instanzSlot[wIdx][iIdx] == slotIdx1 && besucht[pIdx][wIdx][iIdx]) {
                    return true;
                }
            }
        }
        return false;
    }


    private boolean pflichtvortragDecktSlotAb(Veranstaltung veranstaltung, Teilnehmer teilnehmer, Long slotId) {
        return veranstaltung.getPflichtvortraege().stream()
            .anyMatch(pv -> teilnehmer.getGruppen().contains(pv.getPflichtgruppe()) && pv.getPflichtslot().getId().equals(slotId));
    }


    private Teilnehmer ladeTeilnehmer(Veranstaltung veranstaltung, Long teilnehmerId) {
        return veranstaltung.teilnehmer().stream()
            .filter(t -> t.getId().equals(teilnehmerId)).findFirst()
            .orElseThrow(() -> new BusinessException("Teilnehmer nicht gefunden."));
    }


    private int ermittlePrioWert(Teilnehmer teilnehmer, Wahlvortrag vortrag) {
        Prioritaet p = Prioritaet.find("teilnehmer = ?1 and vortrag = ?2", teilnehmer, vortrag).firstResult();
        return null == p ? 0 : p.getPrioWert();
    }


    /**
     * Sammelt alle Wahlvortrag-Instanzen im angegebenen Zeitslot mit freier Raumkapazität, die dem
     * Teilnehmer noch nicht zugeteilt sind (weder in der ggf. auszuschließenden aktuellen
     * Instanz - Umbuchung - noch in irgendeiner anderen Instanz desselben Wahlvortrags) - gemeinsam
     * genutzt von {@link #getUmbuchungsOptionen} und {@link #ermittleNachbuchungsVorschlaege}, die
     * sich nur in Ausschluss-Kriterium und Sortierung unterscheiden. ausschlussWvIdx/-InstanzIdx auf
     * -1 setzen, wenn es keine auszuschließende aktuelle Instanz gibt (Nachbuchung).
     */
    private List<UmbuchungOptionDto> kandidatenInSlot(Planungsergebnis.MinizincResult result, Teilnehmer teilnehmer, int pIdx,
                                                       int slotIdx1, int ausschlussWvIdx, int ausschlussInstanzIdx,
                                                       Map<Long, Wahlvortrag> wahlvortragByOid, Map<Long, Raum> raumByOid) {
        long[] tnOids = result.teilnehmer_oids;
        long[] wvOids = result.wahlvortrag_oids;
        long[] raumOids = result.raum_oids;
        boolean[][][] besucht = result.besucht;
        int[][] instanzSlot = result.instanz_slot;
        int[][] instanzRaum = result.instanz_raum;
        int maxInstanzen = instanzSlot[0].length;

        List<UmbuchungOptionDto> optionen = new ArrayList<>();
        for (int wIdx = 0; wIdx < wvOids.length; wIdx++) {
            Wahlvortrag kandidatVortrag = wahlvortragByOid.get(wvOids[wIdx]);
            if (null == kandidatVortrag) {
                continue;
            }
            for (int iIdx = 0; iIdx < instanzSlot[wIdx].length; iIdx++) {
                if (wIdx == ausschlussWvIdx && iIdx == ausschlussInstanzIdx) {
                    continue;
                }
                if (instanzSlot[wIdx][iIdx] != slotIdx1 || result.istAusgefallen(wIdx, iIdx)) {
                    continue;
                }
                if (besuchtWahlvortragBereits(besucht, pIdx, wIdx, maxInstanzen)) {
                    continue;
                }
                int rIdx = instanzRaum[wIdx][iIdx] - 1;
                if (rIdx < 0) {
                    continue;
                }
                Raum raum = raumByOid.get(raumOids[rIdx]);
                if (null == raum) {
                    continue;
                }
                int belegteAnzahl = 0;
                for (int p2 = 0; p2 < tnOids.length; p2++) {
                    if (besucht[p2][wIdx][iIdx]) {
                        belegteAnzahl++;
                    }
                }
                if (belegteAnzahl >= raum.getKapazitaet()) {
                    continue;
                }
                long ueberschneidung = kandidatVortrag.getNeigungen().stream()
                    .filter(teilnehmer.getNeigungen()::contains)
                    .count();
                optionen.add(new UmbuchungOptionDto(
                    kandidatVortrag.getId(), iIdx, kandidatVortrag.getTitel(),
                    kandidatVortrag.getReferent().getFullName(), raum.getName(),
                    raum.getKapazitaet(), belegteAnzahl, (int) ueberschneidung,
                    ermittlePrioWert(teilnehmer, kandidatVortrag)));
            }
        }
        return optionen;
    }


    private int indexOf(long[] arr, long value) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == value) {
                return i;
            }
        }
        return -1;
    }


    private void benachrichtigeBeteiligte(Veranstaltung veranstaltung, Wahlvortrag ausgefallenerVortrag,
                                          List<Teilnehmer> umverteilteTeilnehmer, List<String> neueVortragTitelProTeilnehmer,
                                          List<Teilnehmer> nichtPlatzierteTeilnehmer, String absender) {
        for (int i = 0; i < umverteilteTeilnehmer.size(); i++) {
            Teilnehmer teilnehmer = umverteilteTeilnehmer.get(i);
            String neuerTitel = neueVortragTitelProTeilnehmer.get(i);
            String inhalt = "Der Wahlvortrag '" + ausgefallenerVortrag.getTitel()
                + "' ist kurzfristig ausgefallen. Du wurdest automatisch dem Wahlvortrag '"
                + neuerTitel + "' zugeteilt.";
            nachrichtService.sendeNachricht(teilnehmer, "Dein Wahlvortrag wurde kurzfristig geändert", inhalt,
                NachrichtKategorie.VORTRAG_AUSGEFALLEN, veranstaltung.getId(), absender);
        }

        for (Teilnehmer teilnehmer : nichtPlatzierteTeilnehmer) {
            String inhalt = "Der Wahlvortrag '" + ausgefallenerVortrag.getTitel()
                + "' ist kurzfristig ausgefallen. Es konnte leider kein Ersatz-Vortrag mit freiem Platz für dich "
                + "gefunden werden - bitte wende dich an die Organisation vor Ort.";
            nachrichtService.sendeNachricht(teilnehmer, "Dein Wahlvortrag ist ausgefallen", inhalt,
                NachrichtKategorie.VORTRAG_AUSGEFALLEN, veranstaltung.getId(), absender);
        }

        Referent referent = ausgefallenerVortrag.getReferent();
        if (null != referent) {
            String inhalt = "Dein Wahlvortrag '" + ausgefallenerVortrag.getTitel()
                + "' wurde im Plan als ausgefallen markiert. " + umverteilteTeilnehmer.size()
                + " Teilnehmer wurden auf andere Vorträge umverteilt"
                + (nichtPlatzierteTeilnehmer.isEmpty() ? "." : (", " + nichtPlatzierteTeilnehmer.size() + " konnten nicht platziert werden."));
            nachrichtService.sendeNachricht(referent, "Dein Vortrag wurde als ausgefallen markiert", inhalt,
                NachrichtKategorie.VORTRAG_AUSGEFALLEN, veranstaltung.getId(), absender);
        }

        if (!nichtPlatzierteTeilnehmer.isEmpty()) {
            String namen = nichtPlatzierteTeilnehmer.stream().map(Teilnehmer::getFullName)
                .collect(joining(", "));
            String inhalt = "Beim Ausfall von '" + ausgefallenerVortrag.getTitel() + "' konnten "
                + nichtPlatzierteTeilnehmer.size()
                + " Teilnehmer nicht automatisch umverteilt werden (kein freier Platz im selben Zeitslot): "
                + namen + ". Bitte manuell kümmern.";
            for (Organisator organisator : veranstaltung.organisatoren()) {
                nachrichtService.sendeNachricht(organisator, "Umplanung unvollständig", inhalt,
                    NachrichtKategorie.VORTRAG_AUSGEFALLEN, veranstaltung.getId(), absender);
            }
        }
    }
}
