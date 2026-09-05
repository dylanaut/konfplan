#!/bin/bash

ENV_FILE=".env"
VAR_NAME="IMAGE_TAG"

# 1. Argumente prüfen und Default setzen (m = Minor)
PART=${1:-m}
VAL=$2

if [[ "$PART" != "M" && "$PART" != "m" && "$PART" != "f" ]]; then
    echo "Fehler: Ungültiges Argument für den Release-Teil."
    echo "Verwendung: $0 [M|m|f] [optional: neuer_zahlwert]"
    echo "  M = Major (X.0.0)"
    echo "  m = Minor (x.Y.0) (DEFAULT)"
    echo "  f = Fix   (x.y.Z)"
    exit 1
fi

if [[ -n "$VAL" && ! "$VAL" =~ ^[0-9]+$ ]]; then
    echo "Fehler: Der zweite Parameter muss eine gültige Zahl sein."
    exit 1
fi

# 2. Prüfen, ob .env existiert
if [[ ! -f "$ENV_FILE" ]]; then
    echo "Fehler: Datei $ENV_FILE existiert nicht im aktuellen Verzeichnis."
    exit 1
fi

# 3. Aktuellen Wert auslesen (entfernt eventuelle Anführungszeichen)
CURRENT_TAG=$(grep "^${VAR_NAME}=" "$ENV_FILE" | cut -d'=' -f2 | tr -d '"' | tr -d "'")

if [[ -z "$CURRENT_TAG" ]]; then
    echo "Fehler: Variable ${VAR_NAME} wurde in $ENV_FILE nicht gefunden."
    exit 1
fi

# 4. In MAJOR, MINOR und FIX zerlegen
IFS='.' read -r MAJOR MINOR FIX <<< "$CURRENT_TAG"

if [[ -z "$MAJOR" || -z "$MINOR" || -z "$FIX" ]]; then
    echo "Fehler: Der aktuelle Tag '$CURRENT_TAG' entspricht nicht dem Format MAJOR.MINOR.FIX"
    exit 1
fi

# 5. Neue Werte berechnen
NEW_MAJOR=$MAJOR
NEW_MINOR=$MINOR
NEW_FIX=$FIX

case "$PART" in
    "M")
        NEW_MAJOR=${VAL:-$((MAJOR + 1))}
        NEW_MINOR=0
        NEW_FIX=0
        ;;
    "m")
        NEW_MINOR=${VAL:-$((MINOR + 1))}
        NEW_FIX=0
        ;;
    "f")
        NEW_FIX=${VAL:-$((FIX + 1))}
        ;;
esac

NEW_TAG="${NEW_MAJOR}.${NEW_MINOR}.${NEW_FIX}"

# 6. Bestätigung durch den Benutzer
echo "Datei        : $ENV_FILE"
echo "Variable     : $VAR_NAME"
echo "Aktueller Tag: $CURRENT_TAG"
echo "Neuer Tag    : $NEW_TAG"
echo "---------------------------------"
read -p "Änderung in $ENV_FILE schreiben? [j/N]: " CONFIRM

if [[ "$CONFIRM" =~ ^[jJ](a|A)?$ ]]; then
    # Plattformübergreifendes Ersetzen mit awk
    TMP_FILE=$(mktemp)
    awk -v var="$VAR_NAME" -v val="$NEW_TAG" '
        BEGIN { FS=OFS="=" }
        $1 == var { $2 = val }
        { print }
    ' "$ENV_FILE" > "$TMP_FILE" && mv "$TMP_FILE" "$ENV_FILE"

    echo "Erfolgreich gespeichert."
else
    echo "Abgebrochen. Die Datei wurde nicht verändert."
fi
