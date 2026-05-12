<!DOCTYPE html>
<html lang="de">
<head>
    <meta charset="UTF-8">
    <title>Freie Slots der Teilnehmer</title>
    <style>
        body { font-family: sans-serif; }
        h1, h2 { color: #333; }
        .teilnehmer { margin-bottom: 20px; }
    </style>
</head>
<body>
    <h1>Freie Slots der Teilnehmer für ${veranstaltung.name}</h1>

    <#if teilnehmer?has_content>
        <#list teilnehmer as tn>
            <div class="teilnehmer">
                <h2>${tn.firstName} ${tn.lastName} (${tn.gruppe})</h2>
                <#if freieSlots[tn.id?string]?has_content>
                    <ul>
                        <#list freieSlots[tn.id?string] as slot>
                            <li>${slot.startTime?string("HH:mm")} - ${slot.endTime?string("HH:mm")}</li>
                        </#list>
                    </ul>
                <#else>
                    <p>Keine freien Slots.</p>
                </#if>
            </div>
        </#list>
    <#else>
        <p>Keine Teilnehmer für diese Veranstaltung gefunden.</p>
    </#if>
</body>
</html>
