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
      <VeranstaltungHeader :veranstaltung="reportData.veranstaltung" />
      <div class="d-flex justify-content-between align-items-center mb-4 no-print">
        <h1 class="h3">Statistik</h1>
        <button @click="handlePrint" class="btn btn-secondary">
          <i class="bi bi-printer"></i> Drucken
        </button>
      </div>

      <div class="row">
        <div class="col-6 col-md-3 mb-3">
          <div class="p-3 bg-light rounded">
            <p class="text-muted mb-1">Güte</p>
            <p class="fw-bold fs-5 mb-0">{{ reportData.qualitaet.guete }}</p>
          </div>
        </div>
        <div class="col-6 col-md-3 mb-3">
          <div class="p-3 bg-light rounded">
            <p class="text-muted mb-1">Zuweisungen</p>
            <p class="fw-bold fs-5 mb-0">{{ reportData.qualitaet.zuweisungen }}</p>
          </div>
        </div>
        <div class="col-6 col-md-3 mb-3">
          <div class="p-3 bg-light rounded">
            <p class="text-muted mb-1">Raumwechsel</p>
            <p class="fw-bold fs-5 mb-0">{{ reportData.qualitaet.raumwechsel }}</p>
          </div>
        </div>
        <div class="col-6 col-md-3 mb-3">
          <div class="p-3 bg-light rounded">
            <p class="text-muted mb-1">Status</p>
            <p class="fw-bold fs-5 mb-0">{{ reportData.qualitaet.status }}</p>
          </div>
        </div>
      </div>
    </div>

    <ReportFooter :veranstaltung-name="reportData.veranstaltung.name" report-titel="Statistik" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import api from '../../api/axios';
import VeranstaltungHeader from '../../components/VeranstaltungHeader.vue';
import ReportFooter from '../../components/ReportFooter.vue';

const route = useRoute();
const reportData = ref({ veranstaltung: {}, qualitaet: {} });
const loading = ref(true);
const error = ref(null);

const handlePrint = () => window.print();

onMounted(async () => {
  const veranstaltungId = route.params.vid;
  if (!veranstaltungId) {
    error.value = "Keine Veranstaltungs-ID in der URL gefunden.";
    loading.value = false;
    return;
  }
  try {
    const ergebnisId = route.query.ergebnisId;
    const url = `/api/reports/${veranstaltungId}/statistik-data` + (ergebnisId ? `?ergebnisId=${ergebnisId}` : '');
    const response = await api.get(url);
    reportData.value = response.data;
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
  body {
    background-color: #fff;
    -webkit-print-color-adjust: exact;
    print-color-adjust: exact;
  }
}
</style>
