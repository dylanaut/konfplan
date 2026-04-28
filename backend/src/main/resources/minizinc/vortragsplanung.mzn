include "globals.mzn";

% =============================================================================
% VEREINFACHTES MODELL: NUR WAHLVORTRÄGE
% Pflichtvorträge werden manuell außerhalb des Modells platziert
% =============================================================================

% --- STRUKTUREN ---
type Slot = record(int: oid);
type Raum = record(int: oid, int: kapazitaet);
type Teilnehmer = record(int: oid);
type Wahlvortrag = record(int: oid,
    int: referent_id,
    array[int] of int: belegbare_slots
);

% --- PARAMETER ---
int: n_slots;
array[1..n_slots] of Slot: slots;
int: n_raeume;
array[1..n_raeume] of Raum: raeume;
int: n_wahlvortraege;
array[1..n_wahlvortraege] of Wahlvortrag: wahlvortraege;
int: n_teilnehmer;
array[1..n_teilnehmer] of Teilnehmer: teilnehmer;
array[1..n_teilnehmer, 1..n_wahlvortraege] of int: prioritaeten;
array[1..n_teilnehmer, 1..n_slots] of bool: tn_verfuegbar;  % in welchen Slots ist TN frei/disponierbar?
array[1..n_raeume, 1..n_slots] of bool: raum_belegbar;  % in welchen Slots ist Raum frei/disponierbar??
int: max_instanzen;

% --- HILFS-ARRAYS ---
array[1..n_raeume] of int: r_kapazitaeten = [raeume[r].kapazitaet | r in 1..n_raeume];
array[1..n_wahlvortraege] of int: w_referenten = [wahlvortraege[w].referent_id | w in 1..n_wahlvortraege];

% --- ENTSCHEIDUNGSVARIABLEN ---
array[1..n_wahlvortraege, 1..max_instanzen] of var 0..n_slots: instanz_slot;
array[1..n_wahlvortraege, 1..max_instanzen] of var 0..n_raeume: instanz_raum;
array[1..n_teilnehmer, 1..n_wahlvortraege, 1..max_instanzen] of var bool: besucht;

% =============================================================================
% CONSTRAINTS
% =============================================================================

% 1. Slot und Raum müssen gekoppelt sein
constraint forall(w in 1..n_wahlvortraege, i in 1..max_instanzen) (
    (instanz_slot[w, i] > 0) = (instanz_raum[w, i] > 0)
);

% 2. Nur erlaubte Slots verwenden (für Vorträge)
constraint forall(w in 1..n_wahlvortraege, i in 1..max_instanzen) (
    if instanz_slot[w, i] > 0 then
        exists(s_id in wahlvortraege[w].belegbare_slots)(
            instanz_slot[w, i] = s_id
        )
    else true endif
);

% 2b. Nur verfügbare Raum-Slot-Kombinationen verwenden
constraint forall(w in 1..n_wahlvortraege, i in 1..max_instanzen) (
    if instanz_raum[w, i] > 0 /\ instanz_slot[w, i] > 0 then
        raum_belegbar[instanz_raum[w, i], instanz_slot[w, i]]
    else true endif
);

% 3. Keine doppelten Slots pro Vortrag
constraint forall(w in 1..n_wahlvortraege) (
    all_different_except_0([instanz_slot[w, i] | i in 1..max_instanzen])
);

% 4. RAUM-EXKLUSIVITÄT: Pro Slot-Raum-Kombination nur ein Vortrag
constraint forall(s in 1..n_slots, r in 1..n_raeume) (
    sum(w in 1..n_wahlvortraege, i in 1..max_instanzen) (
        bool2int(instanz_slot[w, i] = s /\ instanz_raum[w, i] = r)
    ) <= 1
);

% 5. KAPAZITÄT: Nicht mehr Teilnehmer als Plätze
constraint forall(w in 1..n_wahlvortraege, i in 1..max_instanzen) (
    if instanz_raum[w, i] > 0 then
        sum(p in 1..n_teilnehmer)(besucht[p, w, i]) <= r_kapazitaeten[instanz_raum[w, i]]
    else
        sum(p in 1..n_teilnehmer)(besucht[p, w, i]) = 0
    endif
);

% 6. REFERENTEN: Ein Referent kann nicht an zwei Orten gleichzeitig sein
constraint forall(ref_id in 1..max(w_referenten), s in 1..n_slots) (
    sum(w in 1..n_wahlvortraege where w_referenten[w] = ref_id,
        i in 1..max_instanzen) (
        bool2int(instanz_slot[w, i] = s)
    ) <= 1
);

% 7. KEINE ÜBERSCHNEIDUNGEN: Pro Teilnehmer maximal ein Vortrag pro Slot
constraint forall(p in 1..n_teilnehmer, s in 1..n_slots) (
    sum(w in 1..n_wahlvortraege, i in 1..max_instanzen) (
        bool2int(instanz_slot[w, i] = s /\ besucht[p, w, i])
    ) <= 1
);

% 8. PRO VORTRAG NUR EINE INSTANZ PRO Teilnehmer
constraint forall(p in 1..n_teilnehmer, w in 1..n_wahlvortraege) (
    sum(i in 1..max_instanzen)(besucht[p, w, i]) <= 1
);

% 9. NUR IN VERFÜGBAREN SLOTS
constraint forall(p in 1..n_teilnehmer, w in 1..n_wahlvortraege, i in 1..max_instanzen) (
    besucht[p, w, i] -> (
        instanz_slot[w, i] > 0 /\
        tn_verfuegbar[p, instanz_slot[w, i]]
    )
);

% 10. KEINE PRIORITÄT 0: Teilnehmeren besuchen nur Vorträge mit Interesse
constraint forall(p in 1..n_teilnehmer, w in 1..n_wahlvortraege, i in 1..max_instanzen) (
    (prioritaeten[p, w] = 0) -> (besucht[p, w, i] = false)
);

% 11. MINDESTENS EIN Teilnehmer: Vortrag findet nur statt, wenn jemand kommt
constraint forall(w in 1..n_wahlvortraege, i in 1..max_instanzen) (
    (instanz_slot[w, i] > 0) -> (
        sum(p in 1..n_teilnehmer)(besucht[p, w, i]) >= 1
    )
);

% --- ERWEITERUNG: REFERENTEN-RAUMTREUE (SOFT CONSTRAINT) ---

% Gewichtung der Strafe: Wie wichtig ist es, im selben Raum zu bleiben?
bool: opt_referent_raumtreue;  % per .dzn definieren
int: penalty_raumwechsel = 5;

% Wir summieren die Strafen für alle Paare von Vortragsinstanzen,
% die den gleichen Referenten haben und in aufeinanderfolgenden Slots liegen.
var int: kosten_raumwechsel =
    if opt_referent_raumtreue then
        sum(w1, w2 in 1..n_wahlvortraege, i1, i2 in 1..max_instanzen
            where (w1 < w2 \/ (w1 = w2 /\ i1 < i2))
            /\ w_referenten[w1] = w_referenten[w2]
        ) (
            let {
                var int: s1 = instanz_slot[w1, i1],
                var int: s2 = instanz_slot[w2, i2],
                var int: r1 = instanz_raum[w1, i1],
                var int: r2 = instanz_raum[w2, i2]
            } in
            if (s1 > 0 /\ s2 > 0 /\ abs(s1 - s2) = 1) then
                bool2int(r1 != r2) * penalty_raumwechsel
            else 0 endif
        )
    else 0 endif; % Falls Flag false, sind die Kosten immer 0

% =============================================================================
% OPTIMIERUNG: Prioritäten erfüllen (1 = beste Wahl)
% =============================================================================

% Kosten: Summe aller Prioritäten (niedrig = gut)
var int: kosten_prioritaeten = sum(p in 1..n_teilnehmer, w in 1..n_wahlvortraege, i in 1..max_instanzen) (
    if besucht[p, w, i] then prioritaeten[p, w] else 0 endif
);

% Bonus: Jedem Teilnehmer möglichst viele Wahlvorträge zuweisen
var int: anzahl_zuweisungen = sum(p in 1..n_teilnehmer, w in 1..n_wahlvortraege, i in 1..max_instanzen) (
    bool2int(besucht[p, w, i])
);

% Ziel: Niedrige Prioritäten, viele Zuweisungen UND wenig Raumwechsel
solve minimize kosten_prioritaeten - (anzahl_zuweisungen * 10) + kosten_raumwechsel;


% =============================================================================
% OUTPUT
% =============================================================================

output ["{ \"instanz_slot\": \(instanz_slot), \"instanz_raum\": \(instanz_raum), \"besucht\": \(besucht), \"kosten\": \(kosten_prioritaeten), \"zuweisungen\": \(anzahl_zuweisungen) }"];