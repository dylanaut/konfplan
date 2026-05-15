% Modell 3: Keine Lösung im Zeitlimit (No Solution in Time)
% Dieses Modell versucht, ein "Golomb-Lineal" zu finden, ein rechenintensives Problem.
% Selbst bei der Suche nach nur *irgendeiner* Lösung (`solve satisfy`) wird der Solver
% bei einem sehr kurzen Timeout wahrscheinlich keine Lösung finden. Dies simuliert den Fall,
% dass die Suche abgebrochen wird, bevor ein erstes Ergebnis vorliegt.

int: m = 23; % Anzahl der Markierungen (m=9 ist bereits sehr schwer)

array[1..m] of var 0..100: marks;

% Constraint 1: Erste Markierung ist bei 0.
constraint marks[1] = 0;

% Constraint 2: Markierungen sind in aufsteigender Reihenfolge (bricht Symmetrie).
constraint forall(i in 1..m-1) (marks[i] < marks[i+1]);

% Constraint 3: Alle Abstände zwischen den Markierungen müssen eindeutig sein.
include "alldifferent.mzn";
constraint alldifferent([marks[j] - marks[i] | i in 1..m, j in i+1..m]);

% Ziel: Finde irgendeine gültige Anordnung von Markierungen.
solve satisfy;
