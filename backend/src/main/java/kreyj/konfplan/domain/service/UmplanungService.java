package kreyj.konfplan.domain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import kreyj.konfplan.adapter.in.web.dto.UmplanungErgebnisDto;
import kreyj.konfplan.domain.exception.BusinessException;
import kreyj.konfplan.persistence.IdEntity;
import kreyj.konfplan.persistence.NachrichtKategorie;
import kreyj.konfplan.persistence.Organisator;
import kreyj.konfplan.persistence.Planungsergebnis;
import kreyj.konfplan.persistence.Raum;
import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.persistence.Wahlvortrag;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final ObjectMapper objectMapper;


    public UmplanungService(PlanService planService, NachrichtService nachrichtService, ObjectMapper objectMapper) {
        this.planService = planService;
        this.nachrichtService = nachrichtService;
        this.objectMapper = objectMapper;
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

        ergebnis.setJsonErgebnis(result.toJson(objectMapper));

        benachrichtigeBeteiligte(veranstaltung, ausgefallenerVortrag, umverteilteTeilnehmer,
            neueVortragTitelProTeilnehmer, nichtPlatzierteTeilnehmer, username);

        LOG.infof("Kurzfristige Umplanung für Wahlvortrag '%s' (Instanz %d) in Veranstaltung '%s': %d umverteilt, %d nicht platziert.",
            ausgefallenerVortrag.getTitel(), instanzIndex, veranstaltung.getName(), umverteilt.size(), nichtPlatzierteTeilnehmer.size());

        List<String> nichtPlatziertNamen = nichtPlatzierteTeilnehmer.stream().map(Teilnehmer::getFullName).toList();
        return new UmplanungErgebnisDto(umverteilt, nichtPlatziertNamen);
    }


    private Planungsergebnis.MinizincResult deserialisiere(Planungsergebnis ergebnis) {
        try {
            return objectMapper.readValue(ergebnis.getJsonErgebnis(), Planungsergebnis.MinizincResult.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
