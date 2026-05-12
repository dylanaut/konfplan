<!DOCTYPE html>
<html lang="de">
<head>
    <meta charset="UTF-8">
    <title>Freie Slots der Referenten</title>
    <style>
        body { font-family: sans-serif; }
        h1, h2 { color: #333; }
        .referent { margin-bottom: 20px; }
    </style>
</head>
<body>
    <h1>Freie Slots der Referenten für ${veranstaltung.name}</h1>

    <#if referenten?has_content>
        <#list referenten as referent>
            <div class="referent">
                <h2>${referent.firstName} ${referent.lastName}</h2>
                <#if freieSlots[referent.id?string]?has_content>
                    <ul>
                        <#list freieSlots[referent.id?string] as slot>
                            <li>${slot.startTime?string("HH:mm")} - ${slot.endTime?string("HH:mm")}</li>
                        </#list>
                    </ul>
                <#else>
                    <p>Keine freien Slots.</p>
                </#if>
            </div>
        </#list>
    <#else>
        <p>Keine Referenten für diese Veranstaltung gefunden.</p>
    </#if>
</body>
</html>
