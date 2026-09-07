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

      <p v-if="reportData.zeilen.length === 0" class="text-muted">Keine Anmeldungen vorhanden.</p>

      <div v-else class="card shadow-sm p-3">
        <div class="prio-legend mb-4">
          <span class="text-muted small me-2">Priorität:</span>
          <span class="prio-legend-track">
            <span
              v-for="p in 10"
              :key="p"
              class="prio-legend-swatch"
              :style="{ backgroundColor: PRIO_COLORS[p] }"
            ></span>
          </span>
          <span class="text-muted small ms-2">1 (niedrig) &rarr; 10 (hoch)</span>
        </div>

        <div v-for="zeile in reportData.zeilen" :key="zeile.vortragId" class="bar-row mb-3">
          <div class="d-flex justify-content-between small mb-1">
            <span class="fw-bold">{{ zeile.titel }}</span>
            <span class="text-muted">
              {{ zeile.anzahlAnmeldungen }} Anmeldung{{ zeile.anzahlAnmeldungen === 1 ? '' : 'en' }}
              &middot; Ø Priorität {{ zeile.durchschnittPrio.toFixed(1) }}
            </span>
          </div>
          <div class="bar-track">
            <span
              v-for="segment in zeile.segmente"
              :key="segment.prioWert"
              class="bar-segment"
              :style="{ width: barWidth(segment.anzahl) + '%', backgroundColor: PRIO_COLORS[segment.prioWert] }"
              :title="`Priorität ${segment.prioWert}: ${segment.anzahl} Anmeldung${segment.anzahl === 1 ? '' : 'en'}`"
            ></span>
          </div>
        </div>
      </div>

      <div v-if="reportData.ohneAnmeldungen.length > 0" class="mt-4">
        <h2 class="h5">Wahlvorträge ohne Anmeldungen ({{ reportData.ohneAnmeldungen.length }})</h2>
        <ul class="list-group">
          <li v-for="wv in reportData.ohneAnmeldungen" :key="wv.vortragId" class="list-group-item text-muted">
            {{ wv.titel }}
          </li>
        </ul>
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

// Sequenzielle Skala Prio 1 (hellgelb) -> Prio 10 (dunkelgrün), in OKLCH interpoliert
// und mit dataviz-Skill-Validator geprüft (Helligkeit monoton, Schrittweite >= 0.06 ΔL,
// Kontrast am hellen Ende >= 2:1 gegen weiße Kartenfläche). Bewusste Abweichung von der
// "ein Farbton"-Ordinalregel, da Nutzer explizit eine Gelb-Grün-Ratingskala wollte -
// kompensiert durch feste Links-nach-rechts-Reihenfolge (Position codiert Identität mit)
// und den MouseOver-Tooltip mit der exakten Anzahl je Segment.
const PRIO_COLORS = {
  1: '#c2b100', 2: '#a19f17', 3: '#828d20', 4: '#667b24', 5: '#4d6825',
  6: '#375423', 7: '#24411e', 8: '#142f18', 9: '#071d0f', 10: '#020c06'
};

const route = useRoute();
const reportData = ref({ veranstaltung: {}, zeilen: [], ohneAnmeldungen: [] });
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

.prio-legend {
  display: flex;
  align-items: center;
}
.prio-legend-track {
  display: flex;
  gap: 2px;
}
.prio-legend-swatch {
  width: 1rem;
  height: 0.75rem;
  border-radius: 2px;
}

.bar-track {
  display: flex;
  gap: 2px;
  background-color: #e9ecef;
  border-radius: 0.375rem;
  height: 1.75rem;
  overflow: hidden;
  padding: 2px;
}
.bar-segment {
  height: 100%;
  min-width: 3px;
  border-radius: 2px;
  transition: filter 0.15s ease;
}
.bar-segment:hover {
  filter: brightness(1.15);
}
</style>
