<template>
  <div class="container-fluid py-4">
    <div v-if="!reportData" class="alert alert-info">
      Keine Daten für den Stundenplan verfügbar.
    </div>
    <div v-else>
      <header class="mb-4 d-flex justify-content-between align-items-center no-print">
        <h1>📅 {{ reportData.veranstaltung.name }} - Stundenplan</h1>
        <div>
          <button @click="downloadIcs" class="btn btn-outline-secondary me-2">
            <i class="bi bi-calendar-plus"></i> ICS Download
          </button>
          <button @click="window.print()" class="btn btn-secondary">
            <i class="bi bi-printer"></i> Drucken
          </button>
        </div>
      </header>
       <p class="lead mb-4 print-only">Veranstaltung: {{ reportData.veranstaltung.name }}</p>

      <div class="row mb-4">
        <h2 class="border-bottom pb-2 mb-3">📊 Auswertung</h2>
        <div class="col-md-3">
          <div class="card bg-success text-white shadow-sm">
            <div class="card-body text-center p-3">
              <h6 class="mb-1">Erfüllung Wahlvorträge</h6>
              <h5 class="mb-0">{{ wahl_erfuellung_stats.total_prefs }} Wünsche, erfüllt: {{ wahl_erfuellung_stats.erfuellungen_gesamt }}x ≅ {{ wahl_erfuellung_stats.gesamt_erfuellungen_prozentual }}</h5>
              <div class="mt-2" style="font-size: 0.8rem; opacity: 0.9;">
                <div v-for="(pref_cnt, wv_oid) in wahl_erfuellung_stats.wv_gewaehlte" :key="wv_oid">
                  <div v-if="pref_cnt > 0" class="d-flex justify-content-between">
                    <span :title="wv_dict[wv_oid].titel">{{ truncTo(wv_dict[wv_oid].titel, 12) }}: {{ pref_cnt }}x gewählt, erf. {{ wahl_erfuellung_stats.wv_erfuellungen[wv_oid] }}</span>
                    <span>≅ {{ wahl_erfuellung_stats.wv_erfuellungen_prozentual[wv_oid] }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div class="col-md-3">
          <div class="card bg-secondary text-white shadow-sm">
            <div class="card-body text-center p-3">
              <h6 class="mb-1">Prio-Erfüllung</h6>
              <div class="mt-2" style="font-size: 0.8rem; opacity: 0.9;">
                <div v-for="(gewaehlt, prio) in wahl_erfuellung_stats.prio_prefs" :key="prio">
                  <div v-if="gewaehlt > 0" class="d-flex justify-content-between">
                    <span>Prio {{ prio }}: {{ gewaehlt }}x gewählt, erf. {{ wahl_erfuellung_stats.prio_erfuellungen[prio] }}x</span>
                    <span>≅ {{ wahl_erfuellung_stats.prio_erfuellungen_prozentual[prio] }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div class="col-md-3">
          <div class="card bg-warning text-dark text-center p-3 shadow-sm border-0" style="background-color: #e2d5f3 !important;">
            <h6 class="mb-1 text-uppercase small fw-bold">Autom. Auffüllung</h6>
            <h3 class="mb-0">{{ stats.anzahl_auffuellung }}</h3>
            <small class="text-muted">{{ stats.anzahl_auffuellung === 1 ? 'Platz' : 'Plätze' }} aufgefüllt</small>
          </div>
        </div>
      </div>

      <section class="mb-5">
        <h2 class="border-bottom pb-2 mb-3">📅 Vortragsplan & Raumbelegung</h2>
        <div v-for="(slot, s_oid) in slots" :key="s_oid" class="slot-container mb-5 p-3 bg-white shadow-sm rounded">
          <h4 class="text-primary">{{ slot.zeitraumTag }}</h4>
          <div class="row g-3">
            <div v-for="(raum, r_oid) in raeume" :key="r_oid" class="col-md-3">
              <div class="card h-100 card-vortrag" :class="getBelegung(s_oid, r_oid) && getBelegung(s_oid, r_oid).isPflicht ? 'border-primary border-2' : 'border-light'">
                <div class="card-header py-1 d-flex justify-content-between small bg-light">
                  <span>Raum: <strong>{{ raum.name }}</strong></span>
                  <span class="text-muted">Kap: {{ raum.kapazitaet }}</span>
                </div>
                <div class="card-body p-2">
                  <template v-if="getBelegung(s_oid, r_oid)">
                    <h6 class="card-title mb-1 text-truncate">{{ getBelegung(s_oid, r_oid).titel }}</h6>
                    <p class="card-text small mb-1">
                      <i class="text-primary fw-bold">{{ getBelegung(s_oid, r_oid).referent }}</i><br>
                      <span class="text-muted small">{{ getBelegung(s_oid, r_oid).organisation }}</span>
                    </p>
                    <div class="d-flex justify-content-between align-items-center mt-2">
                      <span class="badge" :class="getBelegung(s_oid, r_oid).teilnehmer.length < 4 ? 'bg-danger' : 'bg-success'">
                        {{ getBelegung(s_oid, r_oid).teilnehmer.length }} TN
                      </span>
                      <span v-if="getBelegung(s_oid, r_oid).isPflicht" class="badge bg-primary">Pflicht</span>
                    </div>
                  </template>
                  <div v-else class="text-center py-3">
                    <em class="text-muted small">Nicht belegt</em>
                  </div>
                </div>
              </div>
            </div>
          </div>
          <div class="row mt-3">
            <div class="col-12">
              <div class="alert alert-warning py-2 px-3 m-0 shadow-sm">
                <div class="d-flex align-items-center">
                  <strong class="me-2">⚠️ Aufsicht ({{ freie_tn_je_slot[s_oid] ? freie_tn_je_slot[s_oid].length : 0 }}):</strong>
                  <div class="small">
                    <span v-if="!freie_tn_je_slot[s_oid] || freie_tn_je_slot[s_oid].length === 0">Keine Teilnehmer ohne Programm.</span>
                    <span v-else>{{ freie_tn_je_slot[s_oid].join(" • ") }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <footer class="text-right py-2 text-muted small print-footer">
        <span class="badge bg-secondary">Stand: {{ geplantAm }}</span>
      </footer>
    </div>
  </div>
</template>

<script setup>
import { defineProps, computed } from 'vue';
import api from '../../api/axios';
import { useEventContextStore } from '../../stores/eventContext';

const props = defineProps({
  reportData: {
    type: Object,
    required: true,
  },
});

const eventContext = useEventContextStore();

// Destructuring props for easier access in template
const {
  wahl_erfuellung_stats,
  wv_dict,
  ref_dict,
  stats,
  slots,
  raeume,
  belegung_details,
  freie_tn_je_slot,
  geplantAm
} = props.reportData;

const getBelegung = (s_oid, r_oid) => {
  return belegung_details[`${s_oid}_${r_oid}`];
};

const truncTo = (text, maxLen = 25) => {
  if (!text || text.length <= maxLen) {
    return text;
  }
  const truncated = text.substring(0, maxLen);
  const lastIndex = truncated.lastIndexOf(' ');
  return (lastIndex > 0 ? truncated.substring(0, lastIndex) : truncated).trim() + '...';
};

const downloadIcs = async () => {
  try {
    const vid = eventContext.selectedEvent.id;
    const res = await api.get(`/api/ics/admin/${vid}`, { responseType: 'blob' });
    const url = window.URL.createObjectURL(new Blob([res.data], { type: 'text/calendar' }));
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', `veranstaltung_${vid}.ics`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  } catch (e) {
    console.error('Fehler beim Download der ICS-Datei:', e);
  }
};
</script>

<style>
/* Globale Druck-Styles */
@media print {
  .no-print { display: none !important; }
  .print-footer { display: block !important; position: fixed; bottom: 0; width: 100%; text-align: center; font-size: 0.8rem; color: #6c757d; }
  body { background-color: #fff; }
  .container-fluid { width: 100% !important; padding: 0 !important; margin: 0 !important; }
  .table { font-size: 9pt; }
}
.print-footer { display: none; }

/* Spezifische Styles */
body {
  background-color: #f8f9fa;
  font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
}
.header-logo {
  width: 72px;
  height: 72px;
}
.card-vortrag {
  transition: transform 0.2s;
}
.card-vortrag:hover {
  transform: scale(1.02);
  z-index: 10;
}
</style>
