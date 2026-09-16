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
        <h1 class="h3">Raumbelegungen</h1>
        <button @click="handlePrint" class="btn btn-secondary">
          <i class="bi bi-printer"></i> Drucken
        </button>
      </div>

      <div class="row">
        <div v-for="(raum, index) in sortedRaeume" :key="raum.id" class="col-12 page-break-after">
          <VeranstaltungHeader :veranstaltung="reportData.veranstaltung" class="print-only" />
          <div class="card h-100">
            <div class="card-header text-center">
              <h2>{{ raumLabel(raum) }} <small class="text-muted">(Kapazität: {{ raum.kapazitaet }})</small></h2>
            </div>
            <div class="card-body">
              <table class="table table-striped">
                <thead>
                  <tr>
                    <th>Zeit</th>
                    <th>Vortrag</th>
                    <th>Referent</th>
                    <th>Teilnehmer</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="slot in sortedSlots" :key="slot.id" class="page-break-inside-avoid">
                    <td>{{ formatSlot(slot) }}</td>
                    <template v-if="reportData.raumplan[raum.id] && reportData.raumplan[raum.id][slot.id]">
                      <td>{{ reportData.raumplan[raum.id][slot.id].vortragTitel }}</td>
                      <td>
                        {{ reportData.raumplan[raum.id][slot.id].referentName }}
                        <template v-if="reportData.raumplan[raum.id][slot.id].referentOrganisation"><br>{{ reportData.raumplan[raum.id][slot.id].referentOrganisation }}</template>
                      </td>
                      <td>{{ (reportData.raumplan[raum.id][slot.id].teilnehmer || []).length }}</td>
                    </template>
                    <template v-else>
                      <td colspan="3" class="text-muted">Frei</td>
                    </template>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
          <ReportFooter :veranstaltung-name="reportData.veranstaltung.name" report-titel="Raumbelegungen" :seite="index + 1" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue';
import { useRoute } from 'vue-router';
import api from '../../api/axios';
import VeranstaltungHeader from '../../components/VeranstaltungHeader.vue';
import ReportFooter from '../../components/ReportFooter.vue';
import { raumLabel } from '../../utils/raumLabel';

const route = useRoute();
const reportData = ref({ veranstaltung: {}, raumplan: {}, raeume: [], slots: [] });
const loading = ref(true);
const error = ref(null);

const handlePrint = () => window.print();

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
    const ergebnisId = route.query.ergebnisId;
    const url = `/api/reports/${veranstaltungId}/raumschilder-data` + (ergebnisId ? `?ergebnisId=${ergebnisId}` : '');
    const response = await api.get(url);
    reportData.value = response.data;
    document.title = `${response.data.veranstaltung.name} - Raumbelegungen`;
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
  .print-only {
    display: block !important;
  }
  .page-break-after {
    page-break-after: always;
  }
  /* Wiederholt die Tabellen-Kopfzeile (Zeit/Vortrag/Referent/Teilnehmer) auf jeder neuen
     Druckseite, wenn die Belegung eines Raums über eine Seite hinaus geht - dafür darf keine
     einzelne Zeile über einen Seitenumbruch gerissen werden, sonst unterbleibt die native
     Thead-Wiederholung des Browsers. */
  .page-break-inside-avoid {
    page-break-inside: avoid;
  }
  thead {
    display: table-header-group;
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

.print-only {
  display: none;
}
</style>
