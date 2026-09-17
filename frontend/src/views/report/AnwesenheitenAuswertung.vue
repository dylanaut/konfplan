<template>
  <div class="container mt-5">
    <div v-if="loading" class="d-flex justify-content-center">
      <div class="spinner-border" role="status">
        <span class="visually-hidden">Loading...</span>
      </div>
    </div>

    <div v-else-if="error" class="alert alert-danger">
      {{ error }}
    </div>

    <div v-else>
      <VeranstaltungHeader :veranstaltung="reportData.veranstaltung" class="no-print" />
      <div class="d-flex justify-content-between align-items-center mb-4 no-print">
        <h1 class="h3">Anwesenheiten-Auswertung</h1>
        <button @click="handlePrint" class="btn btn-secondary">
          <i class="bi bi-printer"></i> Drucken
        </button>
      </div>

      <div v-for="(eintrag, index) in auswertung" :key="`${eintrag.slotId}-${eintrag.raumId}`" class="page-break-after mb-4">
        <VeranstaltungHeader :veranstaltung="reportData.veranstaltung" class="print-only" />
        <h4 class="mb-0">{{ eintrag.vortragTitel }}</h4>
        <p class="text-muted mb-3">{{ eintrag.slotZeit }} &middot; {{ eintrag.raumName }}</p>

        <div class="row">
          <div class="col-md-4">
            <h6 class="text-success">War da ({{ eintrag.warDa.length }})</h6>
            <ul v-if="eintrag.warDa.length > 0" class="list-unstyled">
              <li v-for="(name, idx) in eintrag.warDa" :key="idx">{{ name }}</li>
            </ul>
            <p v-else class="text-muted small">Niemand.</p>
          </div>
          <div class="col-md-4">
            <h6 class="text-danger">Fehlte ({{ eintrag.fehlte.length }})</h6>
            <ul v-if="eintrag.fehlte.length > 0" class="list-unstyled">
              <li v-for="(name, idx) in eintrag.fehlte" :key="idx">{{ name }}</li>
            </ul>
            <p v-else class="text-muted small">Niemand.</p>
          </div>
          <div class="col-md-4">
            <h6 class="text-warning">Unangemeldet ({{ eintrag.unangemeldet.length }})</h6>
            <ul v-if="eintrag.unangemeldet.length > 0" class="list-unstyled">
              <li v-for="(name, idx) in eintrag.unangemeldet" :key="idx">{{ name }}</li>
            </ul>
            <p v-else class="text-muted small">Niemand.</p>
          </div>
        </div>
        <ReportFooter :veranstaltung-name="reportData.veranstaltung.name" report-titel="Anwesenheiten-Auswertung" :seite="index + 1" />
      </div>

      <p v-if="auswertung.length === 0" class="text-muted">Keine Vorträge vorhanden.</p>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue';
import { useRoute } from 'vue-router';
import api from '../../api/axios';
import VeranstaltungHeader from '../../components/VeranstaltungHeader.vue';
import ReportFooter from '../../components/ReportFooter.vue';

const route = useRoute();
const reportData = ref({ veranstaltung: {}, auswertung: [] });
const loading = ref(true);
const error = ref(null);

const handlePrint = () => window.print();

const auswertung = computed(() => reportData.value.auswertung || []);

onMounted(async () => {
  const veranstaltungId = route.params.vid;
  if (!veranstaltungId) {
    error.value = "Keine Veranstaltungs-ID in der URL gefunden.";
    loading.value = false;
    return;
  }
  try {
    const ergebnisId = route.query.ergebnisId;
    const url = `/api/reports/${veranstaltungId}/anwesenheiten-auswertung-data` + (ergebnisId ? `?ergebnisId=${ergebnisId}` : '');
    const response = await api.get(url);
    reportData.value = response.data;
    document.title = `${response.data.veranstaltung.name} - Anwesenheiten-Auswertung`;
  } catch (err) {
    error.value = 'Fehler beim Laden der Daten: ' + (err.response?.data?.message || err.message);
  } finally {
    loading.value = false;
  }
});
</script>

<style>
/* Globale Druck-Styles */
@media print {
  .no-print {
    display: none !important;
  }
  .print-only {
    display: block !important;
  }
  .page-break-after {
    page-break-after: always;
  }
  body {
    background-color: #fff;
    -webkit-print-color-adjust: exact;
    print-color-adjust: exact;
  }
  .container {
    width: 100% !important;
    padding: 0 !important;
    margin: 0 !important;
  }
}

.print-only {
  display: none;
}
</style>
