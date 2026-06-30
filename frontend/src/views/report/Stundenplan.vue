<template>
  <div class="container-fluid py-4">
    <div v-if="loading" class="d-flex justify-content-center">
      <div class="spinner-border" role="status">
        <span class="visually-hidden">Loading...</span>
      </div>
    </div>
    <div v-else-if="error" class="alert alert-danger">
      {{ error }}
    </div>
    <div v-else-if="reportData">
      <header class="mb-4 d-flex justify-content-between align-items-center no-print">
        <h1>📅 Stundenplan {{ reportData.veranstaltung.name }}</h1>
        <div>
          <button @click="downloadIcs" class="btn btn-outline-secondary me-2">
            <i class="bi bi-calendar-plus"></i> Kalender laden
          </button>
          <button @click="window.print()" class="btn btn-secondary">
            <i class="bi bi-printer"></i> Drucken
          </button>
        </div>
      </header>

      <div class="row mb-4">
        <h2 class="border-bottom pb-2 mb-3">📊 Übersicht</h2>
        <div class="col-md-3">
          <div class="card bg-success text-white shadow-sm">
            <div class="card-body text-center p-3">
              <h6 class="mb-1">Erfüllung Wahlvorträge</h6>
              <h5 class="mb-0">{{ reportData.wahlErfuellungStats.totalPrefs }} Wünsche, erfüllt: {{ reportData.wahlErfuellungStats.erfuellungenGesamt }}x ≅ {{ reportData.wahlErfuellungStats.gesamtErfuellungenProzentual }}</h5>
              <div class="mt-2" style="font-size: 0.8rem; opacity: 0.9;">
                <div v-for="(prefCnt, wv_oid) in reportData.wahlErfuellungStats.wvGewaehlte" :key="wv_oid">
                  <div v-if="prefCnt > 0" class="d-flex justify-content-between">
                    <span :title="reportData.wahlvortraege[wv_oid].titel">{{ truncTo(reportData.wahlvortraege[wv_oid].titel, 12) }}: {{
                        prefCnt }}x gewählt, erf. {{ reportData.wahlErfuellungStats.wvErfuellungen[wv_oid] }}</span>
                    <span>≅ {{ reportData.wahlErfuellungStats.wvErfuellungenProzentual[wv_oid] }}</span>
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
                <div v-for="(gewaehlt, prio) in reportData.wahlErfuellungStats.prioPrefs" :key="prio">
                  <div v-if="gewaehlt > 0" class="d-flex justify-content-between">
                    <span>Prio {{ prio }}: {{ gewaehlt }}x gewählt, erf. {{ reportData.wahlErfuellungStats.prioErfuellungen[prio] }}x</span>
                    <span>≅ {{ reportData.wahlErfuellungStats.prioErfuellungenProzentual[prio] }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div class="col-md-3">
          <div class="card bg-warning text-dark text-center p-3 shadow-sm border-0" style="background-color: #e2d5f3 !important;">
            <h6 class="mb-1 text-uppercase small fw-bold">Autom. Auffüllung</h6>
            <h3 class="mb-0">{{ reportData.stats.anzahlAuffuellungen }}</h3>
            <small class="text-muted">{{ reportData.stats.anzahlAuffuellungen === 1 ? 'Platz' : 'Plätze' }} aufgefüllt</small>
          </div>
        </div>
      </div>

      <section class="mb-5">
        <h2 class="border-bottom pb-2 mb-3">📅 Vortragsplan & Raumbelegung</h2>
        <div v-for="(slot, s_oid) in reportData.slots" :key="s_oid" class="slot-container mb-5 p-3 bg-white shadow-sm rounded">
          <h4 class="text-primary">{{ slot.zeitraumTag }}</h4>
          <div class="row g-3">
            <div v-for="(raum, r_oid) in reportData.raeume" :key="r_oid" class="col-md-3">
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
                  <strong class="me-2">⚠️ Aufsicht ({{ reportData.freieTnInSlot[s_oid] ? reportData.freieTnInSlot[s_oid].length : 0 }}):</strong>
                  <div class="small">
                    <span v-if="!reportData.freieTnInSlot[s_oid] || reportData.freieTnInSlot[s_oid].length === 0">Keine Teilnehmer ohne Programm.</span>
                    <span v-else>{{ reportData.freieTnInSlot[s_oid].join(" • ") }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <footer class="text-right py-2 text-muted small print-footer">
        <span class="badge bg-secondary">Stand: {{ reportData.geplantAm }}</span>
      </footer>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, defineProps } from 'vue';
import api from '../../api/axios';

const props = defineProps({
  vid: {
    type: [String, Number],
    required: true,
  },
});

const reportData = ref(null);
const loading = ref(true);
const error = ref(null);

onMounted(async () => {
  if (!props.vid) {
    error.value = "Keine Veranstaltungs-ID übergeben.";
    loading.value = false;
    return;
  }
  try {
    const response = await api.get(`/api/reports/${props.vid}/stundenplan-data`);
    reportData.value = response.data;
  } catch (err) {
    error.value = 'Fehler beim Laden der Dashboard-Daten: ' + (err.response?.data?.message || err.message);
  } finally {
    loading.value = false;
  }
});

const getBelegung = (s_oid, r_oid) => {
  return reportData.value?.belegungDetails?.[`${s_oid}_${r_oid}`];
};

const truncTo = (text, maxLen = 25) => {
  if (!text) return '';
  return text.length > maxLen ? text.substring(0, maxLen - 1) + '…' : text;
};

const downloadIcs = async () => {
  try {
    const res = await api.get(`/api/kalender/admin/${props.vid}`, { responseType: 'blob' });
    const url = window.URL.createObjectURL(new Blob([res.data], { type: 'text/calendar' }));
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', `veranstaltung_${props.vid}.ics`);
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
