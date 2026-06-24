package kreyj.konfplan.adapter.in.web.dto.templating;

import io.quarkus.runtime.annotations.RegisterForReflection;
import kreyj.konfplan.adapter.in.web.dto.NutzerDto;
import kreyj.konfplan.adapter.in.web.dto.RaumDto;
import kreyj.konfplan.adapter.in.web.dto.SlotDto;
import kreyj.konfplan.adapter.in.web.dto.TeilnehmerDto;
import kreyj.konfplan.adapter.in.web.dto.VeranstaltungDto;
import kreyj.konfplan.adapter.in.web.dto.VortragDto;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RegisterForReflection
@NoArgsConstructor
public class DashboardData {
    public VeranstaltungDto veranstaltung;
    /**
     * in welchem Slot (MZ indiziert) findet der n-te Wahlvortrag in der m-ten Instanz statt
     */
    public int[][] instanzSlot;
    /**
     * in welchem Raum (MZ indiziert) findet der n-te Wahlvortrag in der m-ten Instanz statt
     */
    public int[][] instanzRaum;

    /**
     * besucht ein (MZ-indizierter) Teilnehmer den n-ten Wahlvortrag in der m-ten Instanz?
     */
    public boolean[][][] besucht;


    public long[] mzTeilnehmerOids;
    public long[] mzWahlvortragOids;
    public long[] mzSlotOids;
    public long[] mzRaumOids;

    public Map<Long, TeilnehmerDto> teilnehmer;
    public Map<Long, VortragDto> wahlvortraege;
    public Map<Long, VortragDto> pflichtvortraege;
    public Map<Long, SlotDto> slots;
    public Map<Long, RaumDto> raeume;
    public Map<Long, NutzerDto> referenten;
    public Set<Auffueller> auffuellungSet = new HashSet<>();

    public List<String> tnNamen;
    public int tnAnzahl;

    private Map<Long, Set<Long>> tnVerfuegbar;

    /* String key aus MZ slotId und raumId */
    public Map<String, BelegungDetail> belegungDetails = new HashMap<>();
    public Map<Long, List<String>> freieTnProSlot = new HashMap<>();

    public List<TeilnehmerErfuellung> teilnehmerErfuellung = new ArrayList<>();
    public WahlErfuellungStats wahlErfuellungStats;

    public Planungsstatistik planungsstatistik;
    public List<TeilnehmerStundenplan> teilnehmerStundenplan = new ArrayList<>();
    public TeilnehmerDashboard teilnehmerDashboard;
    public PrioDashboard prioDashboard;
    public Stundenplan stundenplan;

    public String geplantAm;


    public DashboardData(VeranstaltungDto veranstaltung, boolean[][][] besucht, int[][] instanzSlot, int[][] instanzRaum,
                         Map<Long, Set<Long>> tnVerfuegbar,
                         long[] mzTeilnehmerOids, long[] mzWahlvortragOids, long[] mzSlotOids, long[] mzRaumOids) {
        this.veranstaltung = veranstaltung;
        this.besucht = besucht;
        this.instanzSlot = instanzSlot;
        this.instanzRaum = instanzRaum;
        this.tnVerfuegbar = tnVerfuegbar;

        this.mzTeilnehmerOids = mzTeilnehmerOids;
        this.mzWahlvortragOids = mzWahlvortragOids;
        this.mzSlotOids = mzSlotOids;
        this.mzRaumOids = mzRaumOids;
    }

    // -------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------


    public boolean isVerfuegbarInSlot(TeilnehmerDto tn, SlotDto slot) {
        return isVerfuegbarInSlot(tn.id, slot.id);
    }


    public boolean isVerfuegbarInSlot(long tnOid, long slotOid) {
        return tnVerfuegbar.get(tnOid).contains(slotOid);
    }
}
