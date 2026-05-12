<!DOCTYPE html>
<html lang="de">
<head>
    <meta charset="UTF-8">
    <title>Übersicht Raumbelegung</title>
    <style>
        body { font-family: sans-serif; }
        h1 { color: #333; }
        table { width: 100%; border-collapse: collapse; }
        th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
        th { background-color: #f2f2f2; }
    </style>
</head>
<body>
    <h1>Übersicht Raumbelegung für ${veranstaltung.name}</h1>

    <#if plan?has_content>
        <table>
            <thead>
                <tr>
                    <th>Zeit</th>
                    <th>Raum</th>
                    <th>Vortrag</th>
                    <th>Referent</th>
                    <th>Typ</th>
                </tr>
            </thead>
            <tbody>
                <#list plan as belegung>
                    <tr>
                        <td>${belegung.slotZeit}</td>
                        <td>${belegung.raumName}</td>
                        <td>${belegung.vortragTitel}</td>
                        <td>${belegung.referentName}</td>
                        <td>${belegung.vortragTyp}</td>
                    </tr>
                </#list>
            </tbody>
        </table>
    <#else>
        <p>Kein Plan vorhanden.</p>
    </#if>
</body>
</html>
