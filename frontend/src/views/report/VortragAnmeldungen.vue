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
        <h1 class="h3">Anmeldungen für "{{ reportData.vortragTitel }}" ({{ reportData.anmeldungen.length }})</h1>
        <button @click="handlePrint" class="btn btn-secondary">
          <i class="bi bi-printer"></i> Drucken
        </button>
      </div>

      <p v-if="reportData.anmeldungen.length === 0" class="text-muted">Keine Anmeldungen vorhanden.</p>

      <table v-else class="table table-striped table-bordered">
        <thead class="table-dark">
          <tr>
            <th scope="col">Anmeldename</th>
            <th scope="col">Priorität</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="anmeldung in reportData.anmeldungen" :key="anmeldung.loginName">
            <td>{{ anmeldung.loginName }}</td>
            <td>{{ anmeldung.prioWert }}</td>
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
import { ref, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import api from '../../api/axios';
import VeranstaltungHeader from '../../components/VeranstaltungHeader.vue';
import { extractErrorMessage } from '../../utils/errorMessage';

const route = useRoute();
const reportData = ref({ veranstaltung: {}, vortragTitel: '', anmeldungen: [] });
const loading = ref(true);
const error = ref(null);

const handlePrint = () => window.print();

onMounted(async () => {
  const veranstaltungId = route.params.vid;
  const vortragId = route.params.vortragId;
  if (!veranstaltungId || !vortragId) {
    error.value = 'Veranstaltungs- oder Vortrags-ID in der URL nicht gefunden.';
    loading.value = false;
    return;
  }
  try {
    const response = await api.get(`/api/reports/${veranstaltungId}/vortrag/${vortragId}/anmeldungen-data`);
    reportData.value = response.data;
    document.title = `Anmeldungen - ${reportData.value.vortragTitel}`;
  } catch (err) {
    error.value = 'Fehler beim Laden der Daten: ' + extractErrorMessage(err);
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
  .print-footer {
    position: fixed;
    bottom: 0;
    width: 100%;
    text-align: center;
    font-size: 0.8rem;
    color: #6c757d;
    display: block !important;
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

.print-footer {
  display: none;
}
</style>
