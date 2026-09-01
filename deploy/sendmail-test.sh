#!/usr/bin/env sh
# 1. Aktuelle Werte auslesen
SMTP_USER=$(cat .env | grep BREVO_SMTP_USER | cut -d= -f2)
SMTP_PASSWORD=$(cat secrets/brevo_smtp_password.txt)
echo "User: $SMTP_USER"
echo "Password-Länge: ${#SMTP_PASSWORD} Zeichen"

# 2. Testmail-Inhalt anlegen
# WICHTIG: Leerzeile zwischen Headern und Body ist laut RFC 5322 Pflicht - ohne sie wird die
# Body-Zeile als (ungueltige) Header-Fortsetzung interpretiert, was viele Empfangsserver
# (z.B. Gmail) veranlasst, die Nachricht kommentarlos zu verwerfen statt sie zuzustellen.
cat > /tmp/testmail.txt <<MAIL
From: juergenkrey@yahoo.de
To: scalanaut@gmail.com
Subject: Brevo SMTP Test
Date: $(date -R)
MIME-Version: 1.0
Content-Type: text/plain; charset=UTF-8

Testnachricht von Juergen.
MAIL

# 3. Direkt gegen Brevo senden (identische Verbindung wie Keycloak/Quarkus)
curl --url 'smtp://smtp-relay.brevo.com:587' \
--ssl-reqd \
--mail-from 'juergenkrey@yahoo.de' \
--mail-rcpt 'scalanaut@gmail.com' \
--user "$SMTP_USER:$SMTP_PASSWORD" \
--upload-file /tmp/testmail.txt \
-v
