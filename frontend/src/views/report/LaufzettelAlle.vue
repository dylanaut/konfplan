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
        <h1 class="h3">Laufzettel für alle Teilnehmer</h1>
        <button @click="window.print()" class="btn btn-secondary">
          <i class="bi bi-printer"></i> Drucken
        </button>
      </div>
      <p class="lead mb-4 print-only">Veranstaltung: {{ reportData.veranstaltung.name }}</p>

      <div v-for="(plan, teilnehmerId) in sortedPlaene" :key="teilnehmerId" class="page-break-after">
        <h4>{{ reportData.plaene[teilnehmerId][0].teilnehmer.firstName }} {{ reportData.plaene[teilnehmerId][0].teilnehmer.lastName }}</h4>
        <table class="table table-striped table-bordered table-sm">
          <thead class="table-dark">
            <tr>
              <th scope="col">Zeit</th>
              <th scope="col">Vortrag</th>
              <th scope="col">Raum</th>
              <th scope="col">Referent</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="eintrag in plan" :key="eintrag.slot.id">
              <td>{{ formatSlot(eintrag.slot) }}</td>
              <td>{{ eintrag.vortrag.titel }}</td>
              <td>{{ eintrag.raum.name }}</td>
              <td>{{ eintrag.vortrag.referent.firstName }} {{ eintrag.vortrag.referent.lastName }}</td>
            </tr>
          </tbody>
        </table>
      </div>
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
const reportData = ref({ veranstaltung: {}, plaene: {} });
const loading = ref(true);
const error = ref(null);

const sortedPlaene = computed(() => {
  if (!reportData.value.plaene) return {};
  const sorted = {};
  for (const teilnehmerId in reportData.value.plaene) {
    sorted[teilnehmerId] = [...reportData.value.plaene[teilnehmerId]].sort((a, b) => new Date(a.slot.startTime) - new Date(b.slot.startTime));
  }
  return sorted;
});

onMounted(async () => {
  const veranstaltungId = route.params.vid;
  if (!veranstaltungId) {
    error.value = "Keine Veranstaltungs-ID in der URL gefunden.";
    loading.value = false;
    return;
  }
  try {
    const response = await api.get(`/api/reports/${veranstaltungId}/laufzettel-alle-data`);
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
  .page-break-after {
    page-break-after: always;
  }
  body {
    background-color: #fff;
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
