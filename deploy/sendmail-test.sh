#!/usr/bin/env sh
# 1. Aktuelle Werte auslesen
SMTP_USER=$(cat .env | grep BREVO_SMTP_USER | cut -d= -f2)
SMTP_PASSWORD=$(cat secrets/brevo_smtp_password.txt)
echo "User: $SMTP_USER"
echo "Password-Länge: ${#SMTP_PASSWORD} Zeichen"
#
RECIP=${1:-juergenkrey@yahoo.de}
LASTNAME=${2:-Krey}
GENDER=${3:-m}
SALUT='r Herr'
if [ "$GENDER" != 'm' ]; then
  SALUT=' Frau'
fi

read -p "Email an $RECIP mit Anrede 'Sehr geehrte${SALUT} ${LASTNAME}' versenden? (j/n): " antwort

case "$antwort" in
    [jJ][aA]|[jJ])
        echo "Emailversand vorbereitet..."
        ;;
    [nN][eE][iI][nN]|[nN])
        echo "Abgebrochen..."
        exit 0
        ;;
    *)
        echo "Ungültige Eingabe. Versand abgebrochen."
        exit 1
        ;;
esac

# 2. Testmail-Inhalt anlegen
# WICHTIG: Leerzeile zwischen Headern und Body ist laut RFC 5322 Pflicht - ohne sie wird die
# Body-Zeile als (ungueltige) Header-Fortsetzung interpretiert, was viele Empfangsserver
# (z.B. Gmail) veranlasst, die Nachricht kommentarlos zu verwerfen statt sie zuzustellen.
cat > /tmp/testmail.txt <<MAIL
From: kontakt@konfplan.de
To: $RECIP
Subject: KonfPlan Email Test
Date: $(date -R)
MIME-Version: 1.0
Content-Type: text/plain; charset=UTF-8

Sehr geehrte$SALUT $LASTNAME,

dies ein Test zur Zustellung einer KonfPlan-Email an ihre E-Mailadresse.


Mit freundlichen Grüßen,
  Jürgen Krey und Kathrin Jessen
MAIL

# 3. Direkt gegen Brevo senden (identische Verbindung wie Keycloak/Quarkus)
curl --url 'smtp://smtp-relay.brevo.com:587' \
--ssl-reqd \
--mail-from 'kontakt@konfplan.de' \
--mail-rcpt "$RECIP" \
--user "$SMTP_USER:$SMTP_PASSWORD" \
--upload-file /tmp/testmail.txt \
-v
