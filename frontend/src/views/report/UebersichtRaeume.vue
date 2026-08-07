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
      <div class="d-flex justify-content-between align-items-center mb-4 no-print">
        <h1 class="h3">Raumübersicht</h1>
        <button @click="handlePrint" class="btn btn-secondary">
          <i class="bi bi-printer"></i> Drucken
        </button>
      </div>
      <p class="lead mb-4 print-only">Veranstaltung: {{ reportData.veranstaltung.name }}</p>

      <table class="table table-striped table-bordered">
        <thead class="table-dark">
          <tr>
            <th scope="col">Raum</th>
            <th scope="col">Slot</th>
            <th scope="col">Vortrag</th>
            <th scope="col">Referent</th>
          </tr>
        </thead>
        <tbody>
          <template v-for="raum in sortedPlan" :key="raum.raumId">
            <tr v-for="(belegung, index) in raum.belegungen" :key="belegung.slotId" class="page-break-inside-avoid">
              <td v-if="index === 0" :rowspan="raum.belegungen.length">{{ raum.raumName }}</td>
              <td>{{ formatSlot(belegung.slot) }}</td>
              <td>{{ belegung.vortragTitel }}</td>
              <td>{{ belegung.referentName }}</td>
            </tr>
          </template>
        </tbody>
      </table>
    </div>

    <!-- Druck-spezifischer Footer -->
    <footer class="print-footer">
      Gedruckt am {{ new Date().toLocaleDateString('de-DE') }} - KonfPlan
    </footer>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue';
import { useRoute } from 'vue-router';
import api from '../../api/axios';

const route = useRoute();
const reportData = ref({ veranstaltung: {}, plan: [] });
const loading = ref(true);
const error = ref(null);

const handlePrint = () => window.print();

const sortedPlan = computed(() => {
  if (!reportData.value.plan) return [];
  return [...reportData.value.plan].sort((a, b) => a.raumName.localeCompare(b.raumName));
});

onMounted(async () => {
  const veranstaltungId = route.params.vid;
  if (!veranstaltungId) {
    error.value = "Keine Veranstaltungs-ID in der URL gefunden.";
    loading.value = false;
    return;
  }
  try {
    const response = await api.get(`/api/reports/${veranstaltungId}/raeume-data`);
    reportData.value = response.data;
  } catch (err) {
    error.value = 'Fehler beim Laden der Daten: ' + (err.response?.data?.message || err.message);
  } finally {
    loading.value = false;
  }
});

const formatSlot = (slot) => {
  const options = { hour: '2-digit', minute: '2-digit' };
  const start = new Date(slot.startTime).toLocaleTimeString('de-DE', options);
  const end = new Date(slot.endTime).toLocaleTimeString('de-DE', options);
  return `${start} - ${end}`;
};
</script>

<style>
/* Globale Druck-Styles */
@media print {
  .no-print {
    display: none !important;
  }
  .print-footer {
    position: fixed;
    bottom: 0;
    width: 100%;
    text-align: center;
    font-size: 0.8rem;
    color: #6c757d;
    display: block !important;
  }
  .print-only {
    display: block !important;
  }
  .page-break-inside-avoid {
    page-break-inside: avoid;
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
  .table {
    font-size: 10pt;
  }
}

.print-footer, .print-only {
  display: none;
}
</style>
