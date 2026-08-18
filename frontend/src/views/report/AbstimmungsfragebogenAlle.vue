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
        <h1 class="h3">Abstimmungsfragebögen für alle Teilnehmer</h1>
        <button @click="handlePrint" class="btn btn-secondary">
          <i class="bi bi-printer"></i> Drucken
        </button>
      </div>

      <div v-for="t in reportData.teilnehmer" :key="t.id" class="page-break-after fragebogen-seite">
        <VeranstaltungHeader :veranstaltung="reportData.veranstaltung" />
        <h4 class="mb-4">
          {{ t.firstName }} {{ t.lastName }}
          <small v-if="t.gruppen?.length" class="text-muted">({{ t.gruppen.join(', ') }})</small>
        </h4>

        <h5>Wahlvorträge</h5>
        <div v-for="eintrag in reportData.legende" :key="eintrag.nummer" class="mb-2">
          <div>
            <strong>{{ eintrag.nummer }}.</strong>
            {{ eintrag.referentName }} &ndash; {{ eintrag.titel }}
            <span v-if="eintrag.referentOrganisation" class="text-muted">({{ eintrag.referentOrganisation }})</span>
          </div>
          <div v-if="eintrag.inhalt" class="ps-4 small text-muted">{{ eintrag.inhalt }}</div>
        </div>

        <h5 class="mt-4">Ihre Prioritäten</h5>
        <table class="table table-bordered table-sm prio-tabelle">
          <thead>
            <tr>
              <th v-for="eintrag in reportData.legende" :key="eintrag.nummer" scope="col">{{ eintrag.nummer }}</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td v-for="eintrag in reportData.legende" :key="eintrag.nummer"></td>
            </tr>
          </tbody>
        </table>
        <p class="small text-muted">
          leer oder 0 =&gt; kein Interesse, ... , 10 =&gt; höchstes Interesse
        </p>
      </div>
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

const route = useRoute();
const reportData = ref({ veranstaltung: {}, legende: [], teilnehmer: [] });
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
    const response = await api.get(`/api/reports/${veranstaltungId}/abstimmungsfragebogen-data`);
    reportData.value = response.data;
    document.title = `${response.data.veranstaltung.name} - Abstimmungsfragebögen`;
  } catch (err) {
    error.value = 'Fehler beim Laden der Daten: ' + (err.response?.data?.error || err.response?.data || err.message);
  } finally {
    loading.value = false;
  }
});
</script>

<style>
.prio-tabelle th, .prio-tabelle td {
  min-width: 2.2rem;
  text-align: center;
}
.prio-tabelle td {
  height: 2.2rem;
}

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
  .prio-tabelle {
    font-size: 8pt;
  }
}

.print-footer {
  display: none;
}
</style>
