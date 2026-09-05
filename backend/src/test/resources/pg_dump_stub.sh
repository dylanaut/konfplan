#!/bin/sh
# Stub fuer pg_dump in Tests (siehe PgDumpStubTestProfile) - schreibt statt eines echten Dumps
# einen deterministischen Marker inkl. aller Aufrufargumente (letztes Argument = Datenbankname)
# nach stdout, damit DatabaseBackupResourceTest die ZIP-Struktur ohne echtes Postgres pruefen kann.
echo "STUB-DUMP $*"
