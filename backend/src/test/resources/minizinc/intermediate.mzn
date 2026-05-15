% Modell 2: Liefert Zwischenergebnis (Intermediate Solution)
% Dieses Modell löst ein extrem großes Rucksackproblem. Es ist so konzipiert,
% dass es praktisch unmöglich ist, die *optimale* Lösung in einer
% realistischen Zeitspanne zu finden.
% Bei einem kurzen Timeout wird der Solver daher garantiert nur eine
% gültige, aber nicht-optimale Zwischenlösung ausgeben. Dies testet,
% ob die Anwendung korrekt mit Zwischenergebnissen umgeht.

int: n = 300; % Anzahl der Gegenstände

% Feste Daten für Reproduzierbarkeit
array[1..n] of int: values  = [18, 15, 10, 25, 12, 22, 19, 14, 28, 30, 17, 13, 24, 29, 16, 21, 20, 27, 23, 11, 26, 11, 20, 16, 29, 13, 24, 18, 27, 15, 21, 19, 23, 12, 30, 14, 25, 17, 22, 28, 19, 26, 13, 28, 17, 21, 15, 29, 11, 24, 16, 20, 27, 14, 22, 18, 25, 10, 23, 30, 19, 26, 13, 28, 17, 21, 15, 29, 11, 24, 16, 20, 27, 14, 22, 18, 25, 10, 23, 30, 18, 15, 10, 25, 12, 22, 19, 14, 28, 30, 17, 13, 24, 29, 16, 21, 20, 27, 23, 11, 26, 11, 20, 16, 29, 13, 24, 18, 27, 15, 21, 19, 23, 12, 30, 14, 25, 17, 22, 28, 19, 26, 13, 28, 17, 21, 15, 29, 11, 24, 16, 20, 27, 14, 22, 18, 25, 10, 23, 30, 15, 25, 14, 21, 18, 29, 13, 22, 16, 27, 18, 15, 10, 25, 12, 22, 19, 14, 28, 30, 17, 13, 24, 29, 16, 21, 20, 27, 23, 11, 26, 11, 20, 16, 29, 13, 24, 18, 27, 15, 21, 19, 23, 12, 30, 14, 25, 17, 22, 28, 19, 26, 13, 28, 17, 21, 15, 29, 11, 24, 16, 20, 27, 14, 22, 18, 25, 10, 23, 30, 19, 26, 13, 28, 17, 21, 15, 29, 11, 24, 16, 20, 27, 14, 22, 18, 25, 10, 23, 30, 18, 15, 10, 25, 12, 22, 19, 14, 28, 30, 17, 13, 24, 29, 16, 21, 20, 27, 23, 11, 26, 11, 20, 16, 29, 13, 24, 18, 27, 15, 21, 19, 23, 12, 30, 14, 25, 17, 22, 28, 19, 26, 13, 28, 17, 21, 15, 29, 11, 24, 16, 20, 27, 14, 22, 18, 25, 10, 23, 30, 15, 25, 14, 21, 18, 29, 13, 22, 16, 27];
array[1..n] of int: weights = [5, 4, 3, 7, 4, 6, 5, 4, 8, 9, 5, 4, 7, 8, 5, 6, 6, 8, 7, 3, 7, 3, 6, 5, 8, 4, 7, 5, 8, 4, 6, 5, 7, 3, 9, 4, 7, 5, 6, 8, 6, 8, 4, 9, 5, 7, 4, 8, 3, 7, 5, 6, 8, 4, 7, 5, 8, 3, 7, 9, 6, 8, 4, 9, 5, 7, 4, 8, 3, 7, 5, 6, 8, 4, 7, 5, 8, 3, 7, 9, 5, 4, 3, 7, 4, 6, 5, 4, 8, 9, 5, 4, 7, 8, 5, 6, 6, 8, 7, 3, 7, 3, 6, 5, 8, 4, 7, 5, 8, 4, 6, 5, 7, 3, 9, 4, 7, 5, 6, 8, 6, 8, 4, 9, 5, 7, 4, 8, 3, 7, 5, 6, 8, 4, 7, 5, 8, 3, 7, 9, 4, 7, 4, 6, 5, 8, 3, 7, 5, 8, 5, 4, 3, 7, 4, 6, 5, 4, 8, 9, 5, 4, 7, 8, 5, 6, 6, 8, 7, 3, 7, 3, 6, 5, 8, 4, 7, 5, 8, 4, 6, 5, 7, 3, 9, 4, 7, 5, 6, 8, 6, 8, 4, 9, 5, 7, 4, 8, 3, 7, 5, 6, 8, 4, 7, 5, 8, 3, 7, 9, 6, 8, 4, 9, 5, 7, 4, 8, 3, 7, 5, 6, 8, 4, 7, 5, 8, 3, 7, 9, 5, 4, 3, 7, 4, 6, 5, 4, 8, 9, 5, 4, 7, 8, 5, 6, 6, 8, 7, 3, 7, 3, 6, 5, 8, 4, 7, 5, 8, 4, 6, 5, 7, 3, 9, 4, 7, 5, 6, 8, 6, 8, 4, 9, 5, 7, 4, 8, 3, 7, 5, 6, 8, 4, 7, 5, 8, 3, 7, 9, 4, 7, 4, 6, 5, 8, 3, 7, 5, 8];
int: capacity = 600; % Kapazität des Rucksacks

% Entscheidungsvariablen: 1, wenn Gegenstand i genommen wird, sonst 0.
array[1..n] of var 0..1: take;

% Constraint: Das Gesamtgewicht darf die Kapazität nicht überschreiten.
constraint sum(i in 1..n) (weights[i] * take[i]) <= capacity;

% Ziel: Maximiere den Gesamtwert.
solve maximize sum(i in 1..n) (values[i] * take[i]);

output [
  "{\"total_value\": \(sum(i in 1..n) (values[i] * take[i])), \"items_taken\": \(take)}"
];
