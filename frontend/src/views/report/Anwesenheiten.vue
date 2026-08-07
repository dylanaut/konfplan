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
        <h1 class="h3">Anwesenheiten</h1>
        <button @click="handlePrint" class="btn btn-secondary">
          <i class="bi bi-printer"></i> Drucken
        </button>
      </div>

      <div v-for="eintrag in vortraege" :key="`${eintrag.slotId}-${eintrag.raumId}`" class="page-break-after">
        <h4 class="mb-0">{{ eintrag.vortragTitel }}</h4>
        <p class="text-muted mb-3">
          {{ eintrag.referentName }} &middot; {{ eintrag.slotZeit }} &middot; {{ eintrag.raumName }}
        </p>
        <p v-if="!eintrag.teilnehmerNamen || eintrag.teilnehmerNamen.length === 0" class="text-muted">
          Keine Teilnehmer zugewiesen.
        </p>
        <table v-else class="table table-striped table-bordered table-sm">
          <thead class="table-dark">
            <tr>
              <th scope="col">Teilnehmer</th>
              <th scope="col" class="text-center" style="width: 100px;">Anwesend</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="name in eintrag.teilnehmerNamen" :key="name">
              <td>{{ name }}</td>
              <td class="text-center"><span class="checkbox-box"></span></td>
            </tr>
          </tbody>
        </table>
      </div>

      <p v-if="vortraege.length === 0" class="text-muted">Keine Vorträge vorhanden.</p>
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
import VeranstaltungHeader from '../../components/VeranstaltungHeader.vue';

const route = useRoute();
const reportData = ref({ veranstaltung: {}, plan: [] });
const loading = ref(true);
const error = ref(null);

const handlePrint = () => window.print();

const vortraege = computed(() => {
  if (!reportData.value.plan) return [];
  return reportData.value.plan
    .filter(eintrag => eintrag.vortragTyp !== 'FREI')
    .sort((a, b) => a.slotZeit.localeCompare(b.slotZeit) || a.raumName.localeCompare(b.raumName));
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
    document.title = `${response.data.veranstaltung.name} - Anwesenheiten`;
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

.checkbox-box {
  display: inline-block;
  width: 16px;
  height: 16px;
  border: 1px solid #495057;
  vertical-align: middle;
}
</style>
