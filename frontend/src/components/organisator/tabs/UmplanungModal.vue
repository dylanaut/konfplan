<template>
  <div v-if="isVisible" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4">
    <div class="w-full max-w-3xl rounded-xl bg-white p-6 shadow-2xl overflow-y-auto max-h-[90vh]">
      <div class="flex items-center justify-between mb-4">
        <h2 class="text-xl font-bold text-gray-900">Kurzfristige Umplanung</h2>
        <button class="text-gray-500 hover:text-gray-700" @click="$emit('close')">✕</button>
      </div>

      <p class="text-xs text-gray-500 mb-4">
        Markieren Sie eine ausgefallene Wahlvortrag-Instanz - die bereits zugewiesenen Teilnehmer werden
        automatisch passend zu ihren Neigungen auf andere Instanzen im selben Zeitslot verteilt, sofern dort
        noch Raumkapazität frei ist.
      </p>

      <div v-if="ergebnis" class="mb-4 rounded-lg border p-3 text-xs"
           :class="ergebnis.nichtPlatziert.length > 0 ? 'bg-amber-50 border-amber-200 text-amber-800' : 'bg-green-50 border-green-200 text-green-800'">
        <p v-if="ergebnis.umverteilt.length > 0" class="font-semibold mb-1">Umverteilt:</p>
        <ul v-if="ergebnis.umverteilt.length > 0" class="list-disc list-inside mb-2">
          <li v-for="(u, idx) in ergebnis.umverteilt" :key="idx">{{ u.teilnehmerName }} → {{ u.neuerVortragTitel }}</li>
        </ul>
        <p v-if="ergebnis.nichtPlatziert.length > 0" class="font-semibold mb-1">Nicht platziert (kein freier Platz im selben Zeitslot):</p>
        <ul v-if="ergebnis.nichtPlatziert.length > 0" class="list-disc list-inside">
          <li v-for="(name, idx) in ergebnis.nichtPlatziert" :key="idx">{{ name }}</li>
        </ul>
        <p v-if="ergebnis.umverteilt.length === 0 && ergebnis.nichtPlatziert.length === 0">
          Keine Teilnehmer waren dieser Instanz zugewiesen.
        </p>
      </div>

      <div v-if="error" class="bg-red-50 border border-red-200 text-red-700 text-xs rounded-xl p-3 mb-4">{{ error }}</div>

      <div v-if="loading" class="text-center text-xs text-gray-500 py-6">Lade Wahlvortrag-Instanzen...</div>
      <div v-else-if="instanzen.length === 0" class="text-center text-xs text-gray-500 py-6">
        Keine Wahlvortrag-Instanzen in diesem Planungsergebnis gefunden.
      </div>
      <table v-else class="min-w-full divide-y divide-gray-200 text-xs">
        <thead class="bg-gray-50 text-[9px] uppercase font-bold text-gray-500">
        <tr>
          <th class="px-3 py-1.5 text-left font-bold">Zeit</th>
          <th class="px-3 py-1.5 text-left font-bold">Vortrag</th>
          <th class="px-3 py-1.5 text-left font-bold">Referent</th>
          <th class="px-3 py-1.5 text-left font-bold">Raum</th>
          <th class="px-3 py-1.5 text-center font-bold">Belegung</th>
          <th class="px-3 py-1.5 text-right font-bold">Aktion</th>
        </tr>
        </thead>
        <tbody class="divide-y divide-gray-100">
        <tr v-for="i in instanzen" :key="`${i.wahlvortragId}-${i.instanzIndex}`"
            :class="i.ausgefallen ? 'opacity-50' : 'hover:bg-gray-50'">
          <td class="px-3 py-2">{{ i.slotZeit }}</td>
          <td class="px-3 py-2" :class="{ 'line-through': i.ausgefallen }">{{ i.vortragTitel }}</td>
          <td class="px-3 py-2">{{ i.referentName }}</td>
          <td class="px-3 py-2">{{ i.raumName }}</td>
          <td class="px-3 py-2 text-center">{{ i.belegteAnzahl }} / {{ i.kapazitaet }}</td>
          <td class="px-3 py-2 text-right">
            <span v-if="i.ausgefallen" class="px-1.5 py-0.5 rounded bg-gray-100 text-gray-600 border border-gray-200">Ausgefallen</span>
            <button v-else @click="umplanen(i)" :disabled="busy"
                    class="px-2 py-1 bg-red-500 text-white rounded hover:bg-red-600 disabled:opacity-50">
              Als ausgefallen markieren &amp; umverteilen
            </button>
          </td>
        </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue';
import api from '../../../api/axios';

const props = defineProps({
  isVisible: { type: Boolean, required: true },
  vid: { type: [Number, String], default: null },
  ergebnisId: { type: [Number, String], default: null }
});

const emit = defineEmits(['close', 'umgeplant']);

const instanzen = ref([]);
const loading = ref(false);
const error = ref('');
const busy = ref(false);
const ergebnis = ref(null);

const ladeInstanzen = async () => {
  if (!props.vid || !props.ergebnisId) return;
  loading.value = true;
  error.value = '';
  try {
    const res = await api.get(`/api/veranstaltungen/${props.vid}/planungsergebnisse/${props.ergebnisId}/instanzen`);
    instanzen.value = res.data;
  } catch (e) {
    error.value = 'Fehler beim Laden der Wahlvortrag-Instanzen: ' + (e.response?.data?.error || e.message);
  } finally {
    loading.value = false;
  }
};

const umplanen = async (instanz) => {
  if (!confirm(`Wahlvortrag '${instanz.vortragTitel}' (${instanz.slotZeit}, ${instanz.raumName}) als ausgefallen markieren und die ${instanz.belegteAnzahl} zugewiesenen Teilnehmer umverteilen?`)) {
    return;
  }
  busy.value = true;
  error.value = '';
  ergebnis.value = null;
  try {
    const res = await api.post(`/api/veranstaltungen/${props.vid}/planungsergebnisse/${props.ergebnisId}/umplanen`, {
      wahlvortragId: instanz.wahlvortragId,
      instanzIndex: instanz.instanzIndex
    });
    ergebnis.value = res.data;
    await ladeInstanzen();
    emit('umgeplant');
  } catch (e) {
    error.value = 'Umplanung fehlgeschlagen: ' + (e.response?.data?.error || e.message);
  } finally {
    busy.value = false;
  }
};

watch(() => [props.isVisible, props.ergebnisId], ([visible]) => {
  if (visible) {
    ergebnis.value = null;
    ladeInstanzen();
  }
});
</script>
