#!/bin/sh
# Wird per ExecStartPost nach dem Start des SQL-Server-Containers aufgerufen.
# Anders als Postgres mit POSTGRES_DB legt SQL Server keine benannte Datenbank
# automatisch an - das hier holt das idempotent nach, sobald der Server bereit ist.
set -eu

: "${DB_NAME:=konfplan}"

i=0
while [ "$i" -lt 60 ]; do
    if docker exec konfplan-mssql /opt/mssql-tools18/bin/sqlcmd -C -S localhost -U sa \
        -P "$MSSQL_SA_PASSWORD" \
        -Q "IF DB_ID(N'$DB_NAME') IS NULL CREATE DATABASE [$DB_NAME];" > /dev/null 2>&1; then
        echo "Datenbank '$DB_NAME' ist bereit."
        exit 0
    fi
    i=$((i + 1))
    sleep 2
done

echo "Fehler: SQL Server wurde nach 120s nicht bereit - Datenbank '$DB_NAME' konnte nicht angelegt werden." >&2
exit 1
