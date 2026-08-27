#!/usr/bin/env sh
# 1. Aktuelle Werte auslesen
SMTP_USER=$(cat .env | grep BREVO_SMTP_USER | cut -d= -f2)
SMTP_PASSWORD=$(cat secrets/brevo_smtp_password.txt)
echo "User: $SMTP_USER"
echo "Password-Länge: ${#SMTP_PASSWORD} Zeichen"

# 2. Testmail-Inhalt anlegen
cat > /tmp/testmail.txt <<MAIL
From: juergenkrey@yahoo.de
To: scalanaut@gmail.com
Subject: Brevo SMTP Test
Testnachricht von <b>Jürgen</b>.
MAIL

# 3. Direkt gegen Brevo senden (identische Verbindung wie Keycloak/Quarkus)
curl --url 'smtp://smtp-relay.brevo.com:587' \
--ssl-reqd \
--mail-from 'juergenkrey@yahoo.de' \
--mail-rcpt 'scalanaut@gmail.com' \
--user "$SMTP_USER:$SMTP_PASSWORD" \
--upload-file /tmp/testmail.txt \
-v
