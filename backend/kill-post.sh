#!/bin/bash

# Überprüft, ob ein Port als Argument übergeben wurde.
if [ -z "$1" ]; then
  echo "Fehler: Bitte geben Sie eine Portnummer an."
  echo "Verwendung: $0 <port_nummer>"
  exit 1
fi

PORT=$1

echo "Suche nach Prozess auf Port $PORT..."

# Verwendet lsof, um die Prozess-ID (PID) zu finden.
# -t gibt nur die PID aus, was die Weiterverarbeitung erleichtert.
# head -n 1 stellt sicher, dass wir nur eine PID nehmen, falls mehrere gefunden werden.
PID=$(lsof -t -i :$PORT | head -n 1)

# Überprüft, ob eine PID gefunden wurde.
if [ -z "$PID" ]; then
  echo "Kein Prozess auf Port $PORT gefunden."
  exit 0
fi

# Holt sich zusätzliche Informationen zum Prozess für den Benutzer.
PROCESS_INFO=$(ps -p $PID -o command=)

echo ""
echo "Prozess gefunden!"
echo "--------------------"
echo "PID:      $PID"
echo "Befehl:   $PROCESS_INFO"
echo "--------------------"
echo ""

# Bittet um Bestätigung.
# -n 1 liest nur ein Zeichen, der Benutzer muss nicht Enter drücken.
read -p "Soll dieser Prozess beendet werden? (y/n) " -n 1 -r
echo "" # Fügt eine neue Zeile für saubere Ausgabe hinzu.

# Überprüft die Antwort des Benutzers.
if [[ $REPLY =~ ^[Yy]$ ]]; then
  echo "Beende Prozess $PID..."
  # Verwendet kill -9 für ein sofortiges, erzwungenes Beenden.
  kill -9 $PID
  echo "Prozess wurde beendet."
else
  echo "Vorgang abgebrochen."
fi