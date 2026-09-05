<template>
  <section class="space-y-6 animate-fade-in">
    <!-- Planungsläufe: Historie aller Planerstellungen dieser Veranstaltung -->
    <div class="bg-white p-4 rounded-xl shadow-sm border border-gray-100 no-print">
      <div class="flex justify-between items-center mb-3">
        <h3 class="text-sm font-bold">Planungsläufe</h3>
        <button @click="loadErgebnisse" :disabled="loading" class="btn-secondary flex items-center gap-2 text-xs py-1 px-3">
          <RefreshCwIcon class="w-3.5 h-3.5" :class="{'animate-spin': loading}"/>
          Aktualisieren
        </button>
      </div>

      <div v-if="error" class="bg-red-50 border border-red-200 text-red-700 text-xs rounded-xl p-3 mb-3">{{ error }}</div>

      <div v-if="!loading && ergebnisse.length === 0" class="text-center text-xs text-gray-500 py-6">
        Noch kein Planungslauf vorhanden. Bitte erstellen Sie zuerst einen Plan im Tab "Planerstellung".
      </div>

      <table v-else class="min-w-full divide-y divide-gray-200 text-xs">
        <thead class="bg-gray-50 text-[9px] uppercase font-bold text-gray-500">
        <tr>
          <th class="px-3 py-1.5 text-left font-bold">Ersteller</th>
          <th class="px-3 py-1.5 text-left font-bold">Erstellt am</th>
          <th class="px-3 py-1.5 text-left font-bold">Güte</th>
          <th class="px-3 py-1.5 text-left font-bold">Status</th>
          <th class="px-3 py-1.5 text-right font-bold">Aktionen</th>
        </tr>
        </thead>
        <tbody>
        <tr v-for="e in ergebnisse" :key="e.id" class="hover:bg-gray-50 transition">
          <td class="px-3 py-2">{{ e.ersteller || '-' }}</td>
          <td class="px-3 py-2">{{ e.erstelltAm ? formatDateTime(e.erstelltAm) : '-' }}</td>
          <td class="px-3 py-2">{{ e.guete }}</td>
          <td class="px-3 py-2">
            <span v-if="e.publiziert" class="px-1.5 py-0.5 rounded bg-green-50 text-green-700 border border-green-100">Veröffentlicht</span>
            <span v-else class="px-1.5 py-0.5 rounded bg-gray-50 text-gray-600 border border-gray-200">Entwurf</span>
          </td>
          <td class="px-3 py-2 text-right space-x-2">
            <button v-if="!e.publiziert" @click="publizieren(e)" :disabled="busyId !== null"
                    class="px-2 py-1 bg-indigo-500 text-white rounded hover:bg-indigo-600 disabled:opacity-50">
              Veröffentlichen
            </button>
            <button @click="loeschen(e)" :disabled="e.publiziert || busyId !== null"
                    :title="e.publiziert ? 'Ein veröffentlichtes Ergebnis kann nicht gelöscht werden' : ''"
                    class="px-2 py-1 bg-red-500 text-white rounded hover:bg-red-600 disabled:opacity-50 disabled:cursor-not-allowed">
              Löschen
            </button>
          </td>
        </tr>
        </tbody>
      </table>
    </div>

    <!-- Vorbereitung: unabhängig von einem bereits erstellten Plan verfügbar -->
    <div class="bg-white p-4 rounded-xl shadow-sm border border-gray-100 no-print">
      <h3 class="text-sm font-bold mb-3">Vorbereitung</h3>
      <div class="grid grid-cols-1 md:grid-cols-2 gap-4 text-xs">
        <div class="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
          <div class="flex items-center space-x-2">
            <img src="/logo/konfplan-light_footer.svg" alt="Icon" class="w-5 h-5"/>
            <span class="font-semibold">Abstimmungsfragebögen (Teilnehmer)</span>
          </div>
          <div class="space-x-2">
            <button @click="navigateToReport('AbstimmungsfragebogenAlle')" class="px-2 py-1 bg-indigo-500 text-white rounded hover:bg-indigo-600">Anzeigen</button>
          </div>
        </div>
      </div>
    </div>

    <!-- Veralteter Plan: ein bereits zugeteilter Wahlvortrag wurde zurückgezogen -->
    <div v-if="qualitaet.veraltet" class="bg-amber-50 border border-amber-200 rounded-xl p-4 no-print flex items-start gap-2">
      <AlertTriangleIcon class="w-5 h-5 shrink-0 mt-0.5 text-amber-600"/>
      <p class="text-xs text-amber-800">
        Der Plan könnte veraltete Daten enthalten (ein bereits zugeteilter Wahlvortrag wurde
        zurückgezogen). Bitte erstellen Sie den Plan im Tab "Planerstellung" neu, damit die
        betroffenen Teilnehmer neu verteilt werden.
      </p>
    </div>

    <!-- Belegungsplan -->
    <div v-if="eventContext.selectedEvent && belegungsPlan && belegungsPlan.length > 0">
      <Stundenplan :vid="eventContext.selectedEvent.id" />
    </div>
    <div v-else class="text-center text-gray-500 py-12 bg-white rounded-xl shadow-sm border border-gray-100">
      <p class="font-bold">Kein Planungsergebnis vorhanden.</p>
      <p class="text-xs mt-1">Bitte erstellen Sie zuerst einen Plan im Tab "Planerstellung".</p>
    </div>

    <!-- Planqualität -->
    <div v-if="belegungsPlan && belegungsPlan.length > 0" class="bg-white p-4 rounded-xl shadow-sm border border-gray-100 no-print">
      <h3 class="text-sm font-bold mb-3">Planqualität</h3>
      <div class="grid grid-cols-2 md:grid-cols-4 gap-4 text-xs">
        <div class="p-3 bg-gray-50 rounded-lg">
          <p class="text-gray-500">Güte</p>
          <p class="font-semibold text-sm">{{ qualitaet.guete }}</p>
        </div>
        <div class="p-3 bg-gray-50 rounded-lg">
          <p class="text-gray-500">Zuweisungen</p>
          <p class="font-semibold text-sm">{{ qualitaet.zuweisungen }}</p>
        </div>
        <div class="p-3 bg-gray-50 rounded-lg">
          <p class="text-gray-500">Raumwechsel</p>
          <p class="font-semibold text-sm">{{ qualitaet.raumwechsel }}</p>
        </div>
        <div class="p-3 bg-gray-50 rounded-lg">
          <p class="text-gray-500">Status</p>
          <p class="font-semibold text-sm">{{ qualitaet.status }}</p>
        </div>
      </div>
    </div>

    <!-- Artefakte -->
    <div v-if="belegungsPlan && belegungsPlan.length > 0" class="bg-white p-4 rounded-xl shadow-sm border border-gray-100 no-print">
      <h3 class="text-sm font-bold mb-3">Berichte</h3>
      <div class="grid grid-cols-1 md:grid-cols-2 gap-4 text-xs">
        <div class="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
          <div class="flex items-center space-x-2">
            <img src="/logo/konfplan-light_footer.svg" alt="Icon" class="w-5 h-5"/>
            <span class="font-semibold">Prioritäten Auswertung</span>
          </div>
          <div class="space-x-2">
            <button @click="navigateToReport('Prioritaeten')" class="px-2 py-1 bg-indigo-500 text-white rounded hover:bg-indigo-600">Anzeigen</button>
          </div>
        </div>
        <div class="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
          <div class="flex items-center space-x-2">
            <img src="/logo/konfplan-light_footer.svg" alt="Icon" class="w-5 h-5"/>
            <span class="font-semibold">Teilnehmer-Zuordnungen</span>
          </div>
          <div class="space-x-2">
            <button @click="navigateToReport('TeilnehmerZuordnungen')" class="px-2 py-1 bg-indigo-500 text-white rounded hover:bg-indigo-600">Anzeigen</button>
          </div>
        </div>
        <div class="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
          <div class="flex items-center space-x-2">
            <img src="/logo/konfplan-light_footer.svg" alt="Icon" class="w-5 h-5"/>
            <span class="font-semibold">Raumschilder</span>
          </div>
          <div class="space-x-2">
            <button @click="navigateToReport('Raumschilder')" class="px-2 py-1 bg-indigo-500 text-white rounded hover:bg-indigo-600">Anzeigen</button>
          </div>
        </div>
        <div class="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
          <div class="flex items-center space-x-2">
            <img src="/logo/konfplan-light_footer.svg" alt="Icon" class="w-5 h-5"/>
            <span class="font-semibold">Anwesenheiten</span>
          </div>
          <div class="space-x-2">
            <button @click="navigateToReport('Anwesenheiten')" class="px-2 py-1 bg-indigo-500 text-white rounded hover:bg-indigo-600">Anzeigen</button>
          </div>
        </div>
        <div class="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
          <div class="flex items-center space-x-2">
            <img src="/logo/konfplan-light_footer.svg" alt="Icon" class="w-5 h-5"/>
            <span class="font-semibold">Laufzettel für Teilnehmer</span>
          </div>
          <div class="space-x-2">
            <button @click="navigateToReport('LaufzettelAlle')" class="px-2 py-1 bg-indigo-500 text-white rounded hover:bg-indigo-600">Anzeigen</button>
          </div>
        </div>
        <div class="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
          <div class="flex items-center space-x-2">
            <img src="/logo/konfplan-light_footer.svg" alt="Icon" class="w-5 h-5"/>
            <span class="font-semibold">Laufzettel für Referenten</span>
          </div>
          <div class="space-x-2">
            <button @click="navigateToReport('LaufzettelAlleReferenten')" class="px-2 py-1 bg-indigo-500 text-white rounded hover:bg-indigo-600">Anzeigen</button>
          </div>
        </div>
        <div class="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
          <div class="flex items-center space-x-2">
            <img src="/logo/konfplan-light_footer.svg" alt="Icon" class="w-5 h-5"/>
            <span class="font-semibold">Freie Slots (Referenten)</span>
          </div>
          <div class="space-x-2">
            <button @click="navigateToReport('FreieSlotsReferenten')" class="px-2 py-1 bg-indigo-500 text-white rounded hover:bg-indigo-600">Anzeigen</button>
          </div>
        </div>
        <div class="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
          <div class="flex items-center space-x-2">
            <img src="/logo/konfplan-light_footer.svg" alt="Icon" class="w-5 h-5"/>
            <span class="font-semibold">Freie Slots (Teilnehmer)</span>
          </div>
          <div class="space-x-2">
            <button @click="navigateToReport('FreieSlotsTeilnehmer')" class="px-2 py-1 bg-indigo-500 text-white rounded hover:bg-indigo-600">Anzeigen</button>
          </div>
        </div>
      </div>
    </div>

  </section>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { AlertTriangle as AlertTriangleIcon, RefreshCw as RefreshCwIcon } from '@lucide/vue';
import { useEventContextStore } from '../../../stores/eventContext';
import Stundenplan from '../../../views/report/Stundenplan.vue';
import api from '../../../api/axios';

const props = defineProps({
  belegungsPlan: {type: Array, required: true},
  qualitaet: {type: Object, required: true},
});

const emit = defineEmits(['published']);

const router = useRouter();
const eventContext = useEventContextStore();

const navigateToReport = (routeName) => {
  const vid = eventContext.selectedEvent?.id;
  if (vid) {
    const route = router.resolve({ name: routeName, params: { vid } });
    window.open(route.href, '_blank');
  }
};

const ergebnisse = ref([]);
const loading = ref(false);
const error = ref('');
const busyId = ref(null);

const formatDateTime = (isoString) => new Date(isoString).toLocaleString('de-DE');

const loadErgebnisse = async () => {
  const vid = eventContext.selectedEvent?.id;
  if (!vid) return;
  loading.value = true;
  error.value = '';
  try {
    const res = await api.get(`/api/veranstaltungen/${vid}/planungsergebnisse`);
    ergebnisse.value = res.data;
  } catch (e) {
    error.value = 'Fehler beim Laden der Planungsergebnisse: ' + (e.response?.data?.error || e.message);
  } finally {
    loading.value = false;
  }
};

const publizieren = async (ergebnis) => {
  if (!confirm('Dieses Planungsergebnis veröffentlichen? Ein zuvor veröffentlichtes Ergebnis wird dadurch automatisch zurückgezogen und gilt danach als nicht veröffentlicht.')) {
    return;
  }
  const vid = eventContext.selectedEvent?.id;
  busyId.value = ergebnis.id;
  error.value = '';
  try {
    await api.put(`/api/veranstaltungen/${vid}/planungsergebnisse/${ergebnis.id}/publizieren`);
    await loadErgebnisse();
    emit('published');
  } catch (e) {
    error.value = 'Veröffentlichen fehlgeschlagen: ' + (e.response?.data?.error || e.message);
  } finally {
    busyId.value = null;
  }
};

const loeschen = async (ergebnis) => {
  if (!confirm('Dieses Planungsergebnis endgültig löschen?')) {
    return;
  }
  const vid = eventContext.selectedEvent?.id;
  busyId.value = ergebnis.id;
  error.value = '';
  try {
    await api.delete(`/api/veranstaltungen/${vid}/planungsergebnisse/${ergebnis.id}`);
    await loadErgebnisse();
  } catch (e) {
    error.value = 'Löschen fehlgeschlagen: ' + (e.response?.data?.error || e.message);
  } finally {
    busyId.value = null;
  }
};

onMounted(loadErgebnisse);
</script>

<style scoped>
@reference "tailwindcss";

.btn-secondary { @apply bg-white text-gray-700 px-3 py-1.5 rounded-lg hover:bg-gray-50 font-bold border border-gray-200 transition shadow-sm cursor-pointer disabled:opacity-50; }

.animate-fade-in {
  animation: fadeIn 0.5s ease-in-out;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
