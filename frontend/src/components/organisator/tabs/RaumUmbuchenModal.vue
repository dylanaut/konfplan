<template>
  <div v-if="isVisible" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4">
    <div class="w-full max-w-3xl rounded-xl bg-white p-6 shadow-2xl overflow-y-auto max-h-[90vh]">
      <div class="flex items-center justify-between mb-4">
        <h2 class="text-xl font-bold text-gray-900">Raum umbuchen</h2>
        <button class="text-gray-500 hover:text-gray-700" @click="schliessen">✕</button>
      </div>

      <p class="text-xs text-gray-500 mb-4">
        Verlegt eine Wahlvortrag-Instanz in einen anderen Raum im selben Zeitslot - z.B. wenn sich
        nachträglich ein passenderer Raum ergibt. Es werden nur Räume angeboten, die in diesem
        Zeitslot frei sind und deren Kapazität für die aktuell zugewiesenen Teilnehmer ausreicht.
      </p>

      <div v-if="error" class="bg-red-50 border border-red-200 text-red-700 text-xs rounded-xl p-3 mb-4">{{ error }}</div>
      <div v-if="erfolg" class="bg-green-50 border border-green-200 text-green-800 text-xs rounded-xl p-3 mb-4">{{ erfolg }}</div>

      <!-- Schritt 1: Wahlvortrag-Instanz wählen -->
      <template v-if="!ausgewaehlteInstanz">
        <div v-if="loadingInstanzen" class="text-center text-xs text-gray-500 py-6">Lade Wahlvortrag-Instanzen...</div>
        <div v-else-if="instanzen.length === 0" class="text-center text-xs text-gray-500 py-6">
          Keine Wahlvortrag-Instanzen in diesem Planungsergebnis gefunden.
        </div>
        <table v-else class="min-w-full divide-y divide-gray-200 text-xs">
          <thead class="bg-gray-50 text-[9px] uppercase font-bold text-gray-500">
          <tr>
            <th @click="toggleSort('slotZeit')" class="sortable-header">
              Zeit
              <component :is="getSortIcon('slotZeit')" class="w-3 h-3 inline ml-0.5"/>
            </th>
            <th @click="toggleSort('vortragTitel')" class="sortable-header">
              Vortrag
              <component :is="getSortIcon('vortragTitel')" class="w-3 h-3 inline ml-0.5"/>
            </th>
            <th @click="toggleSort('referentName')" class="sortable-header">
              Referent
              <component :is="getSortIcon('referentName')" class="w-3 h-3 inline ml-0.5"/>
            </th>
            <th @click="toggleSort('raumName')" class="sortable-header">
              Raum
              <component :is="getSortIcon('raumName')" class="w-3 h-3 inline ml-0.5"/>
            </th>
            <th class="px-3 py-1.5 text-right font-bold">Aktion</th>
          </tr>
          </thead>
          <tbody class="divide-y divide-gray-100">
          <tr v-for="i in sortedInstanzen" :key="`${i.wahlvortragId}-${i.instanzIndex}`"
              :class="i.ausgefallen ? 'opacity-50' : 'hover:bg-gray-50'">
            <td class="px-3 py-2">{{ i.slotZeit }}</td>
            <td class="px-3 py-2" :class="{ 'line-through': i.ausgefallen }">{{ i.vortragTitel }}</td>
            <td class="px-3 py-2">{{ i.referentName }}</td>
            <td class="px-3 py-2">{{ raumLabel({ name: i.raumName, gebaeudeKuerzel: i.raumGebaeudeKuerzel }) }}</td>
            <td class="px-3 py-2 text-right">
              <span v-if="i.ausgefallen" class="px-1.5 py-0.5 rounded bg-gray-100 text-gray-600 border border-gray-200">Ausgefallen</span>
              <button v-else @click="instanzAuswaehlen(i)"
                      class="px-2 py-1 bg-white text-gray-700 rounded border border-gray-200 hover:bg-gray-50">
                Raum wählen
              </button>
            </td>
          </tr>
          </tbody>
        </table>
      </template>

      <!-- Schritt 2: freien Zielraum wählen -->
      <template v-else>
        <div class="flex items-center justify-between mb-3 bg-gray-50 rounded-lg p-2 text-xs">
          <span>Aktuell: <b>{{ ausgewaehlteInstanz.vortragTitel }}</b> ({{ ausgewaehlteInstanz.slotZeit }}, {{ raumLabel({ name: ausgewaehlteInstanz.raumName, gebaeudeKuerzel: ausgewaehlteInstanz.raumGebaeudeKuerzel }) }})</span>
          <button @click="zurueckZurAuswahl" class="text-indigo-600 hover:underline">Anderen Vortrag wählen</button>
        </div>

        <div v-if="loadingOptionen" class="text-center text-xs text-gray-500 py-6">Lade freie Räume...</div>
        <div v-else-if="optionen.length === 0" class="text-center text-xs text-gray-500 py-6">
          Kein freier Raum mit mindestens derselben Kapazität im selben Zeitslot gefunden.
        </div>
        <table v-else class="min-w-full divide-y divide-gray-200 text-xs">
          <thead class="bg-gray-50 text-[9px] uppercase font-bold text-gray-500">
          <tr>
            <th class="px-3 py-1.5 text-left font-bold">Raum</th>
            <th class="px-3 py-1.5 text-center font-bold">Kapazität</th>
            <th class="px-3 py-1.5 text-right font-bold">Aktion</th>
          </tr>
          </thead>
          <tbody class="divide-y divide-gray-100">
          <tr v-for="o in optionen" :key="o.raumId" class="hover:bg-gray-50">
            <td class="px-3 py-2">{{ o.raumGebaeudeKuerzel ? `${o.raumGebaeudeKuerzel} ${o.raumName}` : o.raumName }}</td>
            <td class="px-3 py-2 text-center">{{ o.kapazitaet }}</td>
            <td class="px-3 py-2 text-right">
              <button @click="umbuchen(o)" :disabled="busy"
                      class="px-2 py-1 bg-indigo-500 text-white rounded hover:bg-indigo-600 disabled:opacity-50">
                Verlegen
              </button>
            </td>
          </tr>
          </tbody>
        </table>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue';
import { ArrowUpDown, ChevronUp, ChevronDown } from '@lucide/vue';
import api from '../../../api/axios';
import { raumLabel } from '../../../utils/raumLabel';

const props = defineProps({
  isVisible: { type: Boolean, required: true },
  vid: { type: [Number, String], default: null },
  ergebnisId: { type: [Number, String], default: null }
});

const emit = defineEmits(['close', 'umgebucht']);

const instanzen = ref([]);
const loadingInstanzen = ref(false);
const ausgewaehlteInstanz = ref(null);
const optionen = ref([]);
const loadingOptionen = ref(false);
const busy = ref(false);
const error = ref('');
const erfolg = ref('');

const sortKey = ref(null);
const sortOrder = ref('asc');

const toggleSort = (key) => {
  if (sortKey.value === key) {
    sortOrder.value = sortOrder.value === 'asc' ? 'desc' : 'asc';
  } else {
    sortKey.value = key;
    sortOrder.value = 'asc';
  }
};

const getSortIcon = (key) => {
  if (sortKey.value !== key) return ArrowUpDown;
  return sortOrder.value === 'asc' ? ChevronUp : ChevronDown;
};

const sortedInstanzen = computed(() => {
  if (!sortKey.value) {
    return instanzen.value;
  }
  const result = [...instanzen.value];
  result.sort((a, b) => {
    const cmp = String(a[sortKey.value] ?? '').localeCompare(String(b[sortKey.value] ?? ''));
    return sortOrder.value === 'asc' ? cmp : -cmp;
  });
  return result;
});

const reset = () => {
  instanzen.value = [];
  ausgewaehlteInstanz.value = null;
  optionen.value = [];
  error.value = '';
  erfolg.value = '';
};

const schliessen = () => {
  reset();
  emit('close');
};

const zurueckZurAuswahl = () => {
  ausgewaehlteInstanz.value = null;
  optionen.value = [];
  erfolg.value = '';
};

const ladeInstanzen = async () => {
  if (!props.vid || !props.ergebnisId) return;
  loadingInstanzen.value = true;
  error.value = '';
  try {
    const res = await api.get(`/api/veranstaltungen/${props.vid}/planungsergebnisse/${props.ergebnisId}/instanzen`);
    instanzen.value = res.data;
  } catch (e) {
    error.value = 'Fehler beim Laden der Wahlvortrag-Instanzen: ' + (e.response?.data?.error || e.message);
  } finally {
    loadingInstanzen.value = false;
  }
};

const instanzAuswaehlen = async (instanz) => {
  ausgewaehlteInstanz.value = instanz;
  error.value = '';
  erfolg.value = '';
  loadingOptionen.value = true;
  try {
    const res = await api.get(
      `/api/veranstaltungen/${props.vid}/planungsergebnisse/${props.ergebnisId}/vortraege/${instanz.wahlvortragId}/instanzen/${instanz.instanzIndex}/raum-optionen`
    );
    optionen.value = res.data;
  } catch (e) {
    error.value = 'Fehler beim Laden der freien Räume: ' + (e.response?.data?.error || e.message);
  } finally {
    loadingOptionen.value = false;
  }
};

const umbuchen = async (option) => {
  if (!confirm(`Wahlvortrag '${ausgewaehlteInstanz.value.vortragTitel}' vom Raum '${ausgewaehlteInstanz.value.raumName}' `
    + `in den Raum '${option.raumName}' verlegen?`)) {
    return;
  }
  busy.value = true;
  error.value = '';
  try {
    const res = await api.post(`/api/veranstaltungen/${props.vid}/planungsergebnisse/${props.ergebnisId}/raum-umbuchen`, {
      wahlvortragId: ausgewaehlteInstanz.value.wahlvortragId,
      instanzIndex: ausgewaehlteInstanz.value.instanzIndex,
      neuerRaumId: option.raumId
    });
    erfolg.value = `'${res.data.vortragTitel}' erfolgreich in den Raum '${res.data.neuerRaumName}' verlegt.`;
    ausgewaehlteInstanz.value = null;
    optionen.value = [];
    await ladeInstanzen();
    emit('umgebucht');
  } catch (e) {
    error.value = 'Raumumbuchung fehlgeschlagen: ' + (e.response?.data?.error || e.message);
  } finally {
    busy.value = false;
  }
};

watch(() => [props.isVisible, props.ergebnisId], ([visible]) => {
  if (visible) {
    reset();
    ladeInstanzen();
  }
});
</script>

<style scoped>
@reference "tailwindcss";

.sortable-header { @apply px-3 py-1.5 text-left font-bold cursor-pointer hover:text-indigo-600 transition select-none; }
</style>
