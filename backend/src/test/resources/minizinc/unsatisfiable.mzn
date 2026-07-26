% Modell 1: Garantiert unlösbar (Unsatisfiable)
% Dieses Modell dient zum Testen, wie die Anwendung auf ein vom Solver als
% "unlösbar" deklariertes Problem reagiert.

% Eine Entscheidungsvariable
var 1..10: x;

% Zwei sich widersprechende Constraints, die nicht gleichzeitig erfüllt werden können.
constraint x > 8;
constraint x < 4;

% Ziel: Finde irgendeine Lösung (was nicht möglich ist).
solve satisfy;
