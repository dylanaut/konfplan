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
        <h1 class="h3">Freie Slots für Referenten</h1>
        <button @click="window.print()" class="btn btn-secondary">
          <i class="bi bi-printer"></i> Drucken
        </button>
      </div>
      <p class="lead mb-4 print-only">Veranstaltung: {{ reportData.veranstaltung.name }}</p>

      <table class="table table-striped table-bordered">
        <thead class="table-dark">
          <tr>
            <th scope="col">Referent</th>
            <th scope="col">Freie Slots</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="referent in sortedReferenten" :key="referent.id" class="page-break-inside-avoid">
            <td>{{ referent.firstName }} {{ referent.lastName }}</td>
            <td>
              <ul v-if="reportData.freieSlots[referent.id] && reportData.freieSlots[referent.id].length > 0" class="list-unstyled mb-0">
                <li v-for="slot in sortedSlots(reportData.freieSlots[referent.id])" :key="slot.id">
                  {{ formatSlot(slot) }}
                </li>
              </ul>
              <span v-else class="text-muted">Keine freien Slots</span>
            </td>
          </tr>
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
import api from '../api/axios';

const route = useRoute();
const reportData = ref({ veranstaltung: {}, freieSlots: {}, referenten: [] });
const loading = ref(true);
const error = ref(null);

const sortedReferenten = computed(() => {
  if (!reportData.value.referenten) return [];
  return [...reportData.value.referenten].sort((a, b) => {
    return a.lastName.localeCompare(b.lastName) || a.firstName.localeCompare(b.firstName);
  });
});

const sortedSlots = (slots) => {
  return [...slots].sort((a, b) => new Date(a.startTime) - new Date(b.startTime));
};

onMounted(async () => {
  const veranstaltungId = route.params.vid;
  if (!veranstaltungId) {
    error.value = "Keine Veranstaltungs-ID in der URL gefunden.";
    loading.value = false;
    return;
  }
  try {
    const response = await api.get(`/api/reports/${veranstaltungId}/freie-slots-referenten-data`);
    reportData.value = response.data;
  } catch (err) {
    error.value = 'Fehler beim Laden der Daten: ' + (err.response?.data?.message || err.message);
  } finally {
    loading.value = false;
  }
});

const formatSlot = (slot) => {
  const options = { weekday: 'long', hour: '2-digit', minute: '2-digit' };
  const start = new Date(slot.startTime).toLocaleTimeString('de-DE', options);
  const end = new Date(slot.endTime).toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' });
  return `${start} - ${end}`;
};
</script>

<style>
/* Globale Druck-Styles */
@media print {
  /* Versteckt Elemente, die nicht gedruckt werden sollen */
  .no-print {
    display: none !important;
  }

  /* Stellt sicher, dass der Druck-Footer nur beim Drucken sichtbar ist */
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

  /* Verhindert, dass Tabellenzeilen über Seitenumbrüche getrennt werden */
  .page-break-inside-avoid {
    page-break-inside: avoid;
  }

  /* Allgemeine Druck-Optimierungen */
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

/* Standardmäßig sind Druck-spezifische Elemente versteckt */
.print-footer, .print-only {
  display: none;
}
</style>
