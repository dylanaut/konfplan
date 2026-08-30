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
      <VeranstaltungHeader :veranstaltung="veranstaltung" />
      <div class="d-flex justify-content-between align-items-center mb-4 no-print">
        <h1 class="h3">Wahlvorträge - Übersicht</h1>
        <button @click="handlePrint" class="btn btn-secondary">
          <i class="bi bi-printer"></i> Drucken
        </button>
      </div>

      <p v-if="wahlvortraege.length === 0" class="text-muted">Keine Wahlvorträge vorhanden.</p>

      <div v-for="(vortrag, index) in wahlvortraege" :key="vortrag.id" class="vortrag-eintrag mb-4 pb-3 border-bottom">
        <h4 class="mb-1">{{ index + 1 }}. {{ vortrag.titel }}</h4>
        <p class="text-muted mb-2">
          {{ vortrag.referentName }}
          <span v-if="vortrag.referentOrganisation">({{ vortrag.referentOrganisation }})</span>
          <span v-if="vortrag.abschlussName"> &ndash; {{ vortrag.abschlussName }}</span>
        </p>
        <p v-if="vortrag.neigungen?.length" class="mb-2">
          <span v-for="neigungName in vortrag.neigungen" :key="neigungName" class="badge bg-secondary me-1">
            {{ neigungBezeichnung(neigungName) }}
          </span>
        </p>
        <p v-if="vortrag.inhalt" class="mb-0">{{ vortrag.inhalt }}</p>
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
import VeranstaltungHeader from '../../components/VeranstaltungHeader.vue';
import { useNeigungStore } from '../../stores/neigung';
import { extractErrorMessage } from '../../utils/errorMessage';

const route = useRoute();
const neigungStore = useNeigungStore();
const veranstaltung = ref({});
const vortraege = ref([]);
const loading = ref(true);
const error = ref(null);

const handlePrint = () => window.print();

// Gleiche Sortierung wie die "Legende der Wahlvorträge" in TeilnehmerTab.vue/TeilnehmerDashboard.vue
// (alphabetisch nach Titel) - nur so stimmen die dort angezeigten Nummern mit denen hier ueberein.
const wahlvortraege = computed(() =>
  vortraege.value.filter((v) => !v.istPflicht).sort((a, b) => a.titel.localeCompare(b.titel))
);

const neigungBezeichnung = (name) => {
  return neigungStore.neigungen.find((n) => n.name === name)?.bezeichnung ?? name;
};

onMounted(async () => {
  const veranstaltungId = route.params.vid;
  if (!veranstaltungId) {
    error.value = 'Keine Veranstaltungs-ID in der URL gefunden.';
    loading.value = false;
    return;
  }
  try {
    await neigungStore.fetchNeigungen();
    const [veranstaltungRes, vortraegeRes] = await Promise.all([
      api.get(`/api/veranstaltungen/${veranstaltungId}`),
      api.get(`/api/veranstaltungen/${veranstaltungId}/vortraege`),
    ]);
    veranstaltung.value = veranstaltungRes.data;
    vortraege.value = vortraegeRes.data;
    document.title = `${veranstaltungRes.data.name} - Wahlvorträge`;
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
  .vortrag-eintrag {
    break-inside: avoid;
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
</style>
