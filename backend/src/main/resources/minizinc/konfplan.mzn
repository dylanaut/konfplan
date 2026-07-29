include "globals.mzn";

% =============================================================================
% VEREINFACHTES MODELL: NUR WAHLVORTRÄGE
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
array[1..n_teilnehmer, 1..n_slots] of bool: tn_verfuegbar;
array[1..n_raeume, 1..n_slots] of bool: raum_belegbar;
int: max_instanzen;
int: max_wvs_pro_tn; % 0 = kein Limitdie

% --- HILFS-ARRAYS ---
array[1..n_raeume] of int: r_kapazitaeten = [raeume[r].kapazitaet | r in 1..n_raeume];
array[1..n_wahlvortraege] of int: w_referenten = [wahlvortraege[w].referent_id | w in 1..n_wahlvortraege];

array[1..max(w_referenten), 1..n_slots] of bool: referent_verfuegbar;

% --- ENTSCHEIDUNGSVARIABLEN ---
array[1..n_wahlvortraege, 1..max_instanzen] of var 0..n_slots: instanz_slot;
array[1..n_wahlvortraege, 1..max_instanzen] of var 0..n_raeume: instanz_raum;
array[1..n_teilnehmer, 1..n_wahlvortraege, 1..max_instanzen] of var bool: besucht;

% =============================================================================
% CONSTRAINTS
% =============================================================================

constraint forall(w in 1..n_wahlvortraege, i in 1..max_instanzen) (
    (instanz_slot[w, i] > 0) = (instanz_raum[w, i] > 0)
);

constraint forall(w in 1..n_wahlvortraege, i in 1..max_instanzen) (
    if instanz_slot[w, i] > 0 then
        exists(s_id in wahlvortraege[w].belegbare_slots)(
            instanz_slot[w, i] = s_id
        )
    else true endif
);

constraint forall(w in 1..n_wahlvortraege, i in 1..max_instanzen) (
    if instanz_raum[w, i] > 0 /\ instanz_slot[w, i] > 0 then
        raum_belegbar[instanz_raum[w, i], instanz_slot[w, i]]
    else true endif
);

constraint forall(w in 1..n_wahlvortraege, i in 1..max_instanzen) (
    if instanz_slot[w, i] > 0 then
        referent_verfuegbar[w_referenten[w], instanz_slot[w, i]]
    else true endif
);

constraint forall(w in 1..n_wahlvortraege) (
    all_different([instanz_slot[w, i] | i in 1..max_instanzen], {0})
);

constraint forall(s in 1..n_slots, r in 1..n_raeume) (
    sum(w in 1..n_wahlvortraege, i in 1..max_instanzen) (
        bool2int(instanz_slot[w, i] = s /\ instanz_raum[w, i] = r)
    ) <= 1
);

constraint forall(w in 1..n_wahlvortraege, i in 1..max_instanzen) (
    if instanz_raum[w, i] > 0 then
        sum(p in 1..n_teilnehmer)(besucht[p, w, i]) <= r_kapazitaeten[instanz_raum[w, i]]
    else
        sum(p in 1..n_teilnehmer)(besucht[p, w, i]) = 0
    endif
);

constraint forall(ref_id in 1..max(w_referenten), s in 1..n_slots) (
    sum(w in 1..n_wahlvortraege where w_referenten[w] = ref_id,
        i in 1..max_instanzen) (
        bool2int(instanz_slot[w, i] = s)
    ) <= 1
);

constraint forall(p in 1..n_teilnehmer, s in 1..n_slots) (
    sum(w in 1..n_wahlvortraege, i in 1..max_instanzen) (
        bool2int(instanz_slot[w, i] = s /\ besucht[p, w, i])
    ) <= 1
);

constraint forall(p in 1..n_teilnehmer, w in 1..n_wahlvortraege) (
    sum(i in 1..max_instanzen)(besucht[p, w, i]) <= 1
);

constraint forall(p in 1..n_teilnehmer, w in 1..n_wahlvortraege, i in 1..max_instanzen) (
    besucht[p, w, i] -> (
        instanz_slot[w, i] > 0 /\
        tn_verfuegbar[p, instanz_slot[w, i]]
    )
);

% prioritaeten[p, w]: 0 = keine Präferenz (Hard-Exclude), 1 = niedrigste, 10 = höchste Priorität.
constraint forall(p in 1..n_teilnehmer, w in 1..n_wahlvortraege, i in 1..max_instanzen) (
    (prioritaeten[p, w] = 0) -> (besucht[p, w, i] = false)
);

constraint forall(w in 1..n_wahlvortraege, i in 1..max_instanzen) (
    (instanz_slot[w, i] > 0) -> (
        sum(p in 1..n_teilnehmer)(besucht[p, w, i]) >= 1
    )
);

constraint forall(p in 1..n_teilnehmer) (
    max_wvs_pro_tn = 0 \/
    sum(w in 1..n_wahlvortraege, i in 1..max_instanzen)(bool2int(besucht[p, w, i])) <= max_wvs_pro_tn
);

bool: opt_referent_raumtreue;
int: penalty_raumwechsel = 5000;

var int: anzahl_raumwechsel =
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
                bool2int(r1 != r2)
            else 0 endif
        )
    else 0 endif;

% =============================================================================
% PLANERSTELLUNG
% =============================================================================

% Nutzen aus erfüllten Präferenzen: je höher prioritaeten[p, w] (bis 10), desto besser die Zuteilung.
var int: nutzen_prioritaeten = sum(p in 1..n_teilnehmer, w in 1..n_wahlvortraege, i in 1..max_instanzen) (
    if besucht[p, w, i] then prioritaeten[p, w] else 0 endif
);

var int: anzahl_zuweisungen = sum(p in 1..n_teilnehmer, w in 1..n_wahlvortraege, i in 1..max_instanzen) (
    bool2int(besucht[p, w, i])
);

% Gewicht 110 pro Zuweisung entspricht dem früheren Minimierungsmodell (dort: impliziter
% "Nicht-besucht"-Malus 100 je (p,w,i)-Tripel + expliziter Bonus 10 je Zuweisung), damit eine
% zusätzliche Zuweisung weiterhin stets schwerer wiegt als jede Präferenz-Differenz (max. 9).
var int: guete = nutzen_prioritaeten + (anzahl_zuweisungen * 110) - (anzahl_raumwechsel * penalty_raumwechsel);

solve maximize guete;

% =============================================================================
% OUTPUT
% =============================================================================

output ["{ \"instanz_slot\": \(instanz_slot), \"instanz_raum\": \(instanz_raum), \"besucht\": \(besucht), \"guete\": \(guete), \"zuweisungen\": \(anzahl_zuweisungen), \"raumwechsel\": \(anzahl_raumwechsel) }"];
