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
        <h1 class="h3">Anmeldungen je Wahlvortrag ({{ reportData.zeilen.length }})</h1>
        <button @click="handlePrint" class="btn btn-secondary">
          <i class="bi bi-printer"></i> Drucken
        </button>
      </div>

      <p v-if="reportData.zeilen.length === 0" class="text-muted">Keine Wahlvorträge vorhanden.</p>

      <div v-else class="card shadow-sm p-3">
        <div v-for="zeile in reportData.zeilen" :key="zeile.vortragId" class="bar-row mb-3">
          <div class="d-flex justify-content-between small mb-1">
            <span class="fw-bold">{{ zeile.titel }}</span>
            <span class="text-muted">
              {{ zeile.anzahlAnmeldungen }} Anmeldung{{ zeile.anzahlAnmeldungen === 1 ? '' : 'en' }}
              <template v-if="zeile.anzahlAnmeldungen > 0">
                &middot; Ø Priorität {{ zeile.durchschnittPrio.toFixed(1) }}
              </template>
            </span>
          </div>
          <div class="bar-track">
            <div class="bar-fill" :style="{ width: barWidth(zeile.anzahlAnmeldungen) + '%' }">
              <span v-if="zeile.anzahlAnmeldungen > 0" class="bar-value">{{ zeile.anzahlAnmeldungen }}</span>
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
import { ref, computed, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import api from '../../api/axios';
import VeranstaltungHeader from '../../components/VeranstaltungHeader.vue';
import { extractErrorMessage } from '../../utils/errorMessage';

const route = useRoute();
const reportData = ref({ veranstaltung: {}, zeilen: [] });
const loading = ref(true);
const error = ref(null);

const handlePrint = () => window.print();

const maxAnzahl = computed(() =>
  Math.max(1, ...reportData.value.zeilen.map((z) => z.anzahlAnmeldungen))
);
const barWidth = (anzahl) => (anzahl / maxAnzahl.value) * 100;

onMounted(async () => {
  const veranstaltungId = route.params.vid;
  if (!veranstaltungId) {
    error.value = 'Veranstaltungs-ID in der URL nicht gefunden.';
    loading.value = false;
    return;
  }
  try {
    const response = await api.get(`/api/reports/${veranstaltungId}/wahlvortraege-anmeldungen-uebersicht-data`);
    reportData.value = response.data;
    document.title = `${reportData.value.veranstaltung.name} - Anmeldungen je Wahlvortrag`;
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
}

.print-footer {
  display: none;
}

.bar-track {
  background-color: #e9ecef;
  border-radius: 0.375rem;
  height: 1.75rem;
  overflow: hidden;
}
.bar-fill {
  background-color: #4338ca;
  height: 100%;
  min-width: 2px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding-right: 0.5rem;
  transition: width 0.3s ease;
}
.bar-value {
  color: #fff;
  font-size: 0.75rem;
  font-weight: 700;
}
</style>
