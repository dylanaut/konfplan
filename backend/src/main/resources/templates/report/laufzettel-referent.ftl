<!DOCTYPE html>
<html lang="de">
<head>
    <meta charset="UTF-8">
    <title>Laufzettel für ${referent.firstName} ${referent.lastName}</title>
    <style>
        body { font-family: sans-serif; }
        h1, h2, h3 { color: #333; }
        .vortrag { border: 1px solid #ccc; padding: 15px; margin-bottom: 20px; }
        .teilnehmer-liste { list-style-type: none; padding-left: 0; }
        .teilnehmer-liste li { display: flex; align-items: center; margin-bottom: 5px; }
        .checkbox { width: 20px; height: 20px; border: 1px solid #999; margin-right: 10px; }
    </style>
</head>
<body>
    <h1>Laufzettel für ${referent.firstName} ${referent.lastName}</h1>
    <h2>Veranstaltung: ${veranstaltung.name}</h2>

    <#if plan?has_content>
        <#list plan as vortrag>
            <div class="vortrag">
                <h3>${vortrag.vortragTitel}</h3>
                <p><strong>Zeit:</strong> ${vortrag.slotZeit}</p>
                <p><strong>Raum:</strong> ${vortrag.raumName} (${vortrag.gebaeudeName})</p>

                <h4>Teilnehmerliste:</h4>
                <#if vortrag.teilnehmer?has_content>
                    <ul class="teilnehmer-liste">
                        <#list vortrag.teilnehmer as tn>
                            <li>
                                <div class="checkbox"></div>
                                ${tn.firstName} ${tn.lastName} (${tn.gruppe})
                            </li>
                        </#list>
                    </ul>
                <#else>
                    <p>Keine Teilnehmer zugewiesen.</p>
                </#if>
            </div>
        </#list>
    <#else>
        <p>Für Sie sind keine Vorträge geplant.</p>
    </#if>
</body>
</html>
