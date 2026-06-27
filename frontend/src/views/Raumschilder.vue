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
        <h1 class="h3">Raumschilder</h1>
        <button @click="window.print()" class="btn btn-secondary">
          <i class="bi bi-printer"></i> Drucken
        </button>
      </div>
      <p class="lead mb-4 print-only">Veranstaltung: {{ reportData.veranstaltung.name }}</p>

      <div class="row">
        <div v-for="raum in sortedRaeume" :key="raum.id" class="col-12 page-break-after">
          <div class="card h-100">
            <div class="card-header text-center">
              <h2>Raum: {{ raum.name }}</h2>
            </div>
            <div class="card-body">
              <table class="table table-striped">
                <thead>
                  <tr>
                    <th>Zeit</th>
                    <th>Vortrag</th>
                    <th>Referent</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="slot in sortedSlots" :key="slot.id">
                    <td>{{ formatSlot(slot) }}</td>
                    <template v-if="reportData.raumplan[raum.id] && reportData.raumplan[raum.id][slot.id]">
                      <td>{{ reportData.raumplan[raum.id][slot.id].vortrag.titel }}</td>
                      <td>{{ reportData.raumplan[raum.id][slot.id].vortrag.referent.firstName }} {{ reportData.raumplan[raum.id][slot.id].vortrag.referent.lastName }}</td>
                    </template>
                    <template v-else>
                      <td colspan="2" class="text-muted">Frei</td>
                    </template>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
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
import api from '../api/axios';

const route = useRoute();
const reportData = ref({ veranstaltung: {}, raumplan: {}, raeume: [], slots: [] });
const loading = ref(true);
const error = ref(null);

const sortedRaeume = computed(() => {
  if (!reportData.value.raeume) return [];
  return [...reportData.value.raeume].sort((a, b) => a.name.localeCompare(b.name));
});

const sortedSlots = computed(() => {
  if (!reportData.value.slots) return [];
  return [...reportData.value.slots].sort((a, b) => new Date(a.startTime) - new Date(b.startTime));
});

onMounted(async () => {
  const veranstaltungId = route.params.vid;
  if (!veranstaltungId) {
    error.value = "Keine Veranstaltungs-ID in der URL gefunden.";
    loading.value = false;
    return;
  }
  try {
    const response = await api.get(`/api/reports/${veranstaltungId}/raumschilder-data`);
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
