% Modell 3: Keine Lösung im Zeitlimit (No Solution in Time)
% Gesucht wird ein optimales Golomb-Lineal der Ordnung 16 (Länge exakt 177).
% Das Problem ist ERFÜLLBAR, aber das Finden einer Lösung ist so rechenintensiv,
% dass cp-sat es in einem kurzen Zeitlimit (1s) garantiert weder lösen noch als
% unlösbar widerlegen kann -> der Solver liefert reproduzierbar "UNKNOWN".
%
% Dies testet deterministisch den Fall, dass die Suche abgebrochen wird, bevor ein
% Ergebnis vorliegt. Im Gegensatz zu einem (beweisbar) unlösbaren Modell entsteht
% hier KEIN "=====UNSATISFIABLE=====", sodass das Verhalten nicht davon abhängt,
% ob der Solver die Unlösbarkeit zufällig rechtzeitig beweist.

int: m = 16; % Anzahl der Markierungen

array[1..m] of var 0..177: marks;

% Erste Markierung bei 0, aufsteigend (Symmetriebrechung).
constraint marks[1] = 0;
constraint forall(i in 1..m-1) (marks[i] < marks[i+1]);

% Alle paarweisen Abstände eindeutig.
include "alldifferent.mzn";
constraint alldifferent([marks[j] - marks[i] | i in 1..m, j in i+1..m]);

% Auf die (bekannte) optimale Länge fixieren -> erfüllbar, aber extrem schwer zu finden.
constraint marks[m] = 177;

solve satisfy;
