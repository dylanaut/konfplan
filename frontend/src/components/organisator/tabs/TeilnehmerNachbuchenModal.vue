<template>
  <div v-if="isVisible" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4">
    <div class="w-full max-w-3xl rounded-xl bg-white p-6 shadow-2xl overflow-y-auto max-h-[90vh]">
      <div class="flex items-center justify-between mb-4">
        <h2 class="text-xl font-bold text-gray-900">
          Teilnehmer nachbuchen: {{ teilnehmer?.firstName }} {{ teilnehmer?.lastName }}
        </h2>
        <button class="text-gray-500 hover:text-gray-700" @click="schliessen">✕</button>
      </div>

      <p class="text-xs text-gray-500 mb-4">
        Der Teilnehmer wurde wieder als verfügbar markiert. Unten passende Wahlvorträge je neu
        verfügbarem Zeitslot - absteigend sortiert nach unerfüllter Priorität und Neigungs-Übereinstimmung.
        Der Teilnehmer wird bei tatsächlicher Nachbuchung per Nachricht informiert.
      </p>

      <div v-if="error" class="bg-red-50 border border-red-200 text-red-700 text-xs rounded-xl p-3 mb-4">{{ error }}</div>
      <div v-if="erfolg" class="bg-green-50 border border-green-200 text-green-800 text-xs rounded-xl p-3 mb-4">{{ erfolg }}</div>

      <div v-if="offeneVorschlaege.length === 0" class="text-center text-xs text-gray-500 py-6">
        Keine offenen Zeitslots mehr.
      </div>

      <div v-for="vorschlag in offeneVorschlaege" :key="vorschlag.slotId" class="mb-5">
        <h3 class="text-xs font-bold text-gray-700 mb-2">Zeitslot {{ vorschlag.slotZeit }}</h3>
        <table class="min-w-full divide-y divide-gray-200 text-xs">
          <thead class="bg-gray-50 text-[9px] uppercase font-bold text-gray-500">
          <tr>
            <th class="px-3 py-1.5 text-left font-bold">Vortrag</th>
            <th class="px-3 py-1.5 text-left font-bold">Referent</th>
            <th class="px-3 py-1.5 text-left font-bold">Raum</th>
            <th class="px-3 py-1.5 text-center font-bold">Frei</th>
            <th class="px-3 py-1.5 text-center font-bold" title="Vom Teilnehmer selbst vergebene Priorität für diesen Wahlvortrag">Priorität</th>
            <th class="px-3 py-1.5 text-center font-bold" title="Anzahl gemeinsamer Neigungen mit dem Teilnehmer - Entscheidungshilfe zur Beratung">Passung</th>
            <th class="px-3 py-1.5 text-right font-bold">Aktion</th>
          </tr>
          </thead>
          <tbody class="divide-y divide-gray-100">
          <tr v-for="o in vorschlag.optionen" :key="`${o.wahlvortragId}-${o.instanzIndex}`" class="hover:bg-gray-50">
            <td class="px-3 py-2">{{ o.vortragTitel }}</td>
            <td class="px-3 py-2">{{ o.referentName }}</td>
            <td class="px-3 py-2">{{ o.raumName }}</td>
            <td class="px-3 py-2 text-center">{{ o.kapazitaet - o.belegteAnzahl }} / {{ o.kapazitaet }}</td>
            <td class="px-3 py-2 text-center">
              <span v-if="o.prioWert > 0" class="px-1.5 py-0.5 rounded bg-indigo-100 text-indigo-700">{{ o.prioWert }}</span>
              <span v-else class="text-gray-400">-</span>
            </td>
            <td class="px-3 py-2 text-center">
              <span v-if="o.neigungsUeberschneidung > 0" class="px-1.5 py-0.5 rounded bg-green-100 text-green-700">{{ o.neigungsUeberschneidung }}</span>
              <span v-else class="text-gray-400">-</span>
            </td>
            <td class="px-3 py-2 text-right">
              <button @click="nachbuchen(vorschlag, o)" :disabled="busy"
                      class="px-2 py-1 bg-indigo-500 text-white rounded hover:bg-indigo-600 disabled:opacity-50">
                Nachbuchen
              </button>
            </td>
          </tr>
          </tbody>
        </table>
      </div>

      <div class="flex justify-end mt-4">
        <button @click="schliessen" class="px-3 py-1.5 bg-white text-gray-700 rounded-lg border border-gray-200 hover:bg-gray-50">
          Schließen
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue';
import api from '../../../api/axios';

const props = defineProps({
  isVisible: { type: Boolean, required: true },
  vid: { type: [Number, String], default: null },
  teilnehmer: { type: Object, default: null },
  vorschlaege: { type: Array, default: () => [] },
});

const emit = defineEmits(['close']);

const offeneVorschlaege = ref([]);
const busy = ref(false);
const error = ref('');
const erfolg = ref('');

// Lokale Kopie statt direkt auf der Prop zu arbeiten - nach jeder erfolgreichen Nachbuchung wird
// der betroffene Zeitslot hier entfernt, ohne die vom Elternteil übergebenen Vorschläge zu mutieren.
watch(() => props.isVisible, (visible) => {
  if (visible) {
    offeneVorschlaege.value = props.vorschlaege.map(v => ({ ...v, optionen: [...v.optionen] }));
    error.value = '';
    erfolg.value = '';
  }
}, { immediate: true });

const nachbuchen = async (vorschlag, option) => {
  if (!confirm(`${props.teilnehmer.firstName} ${props.teilnehmer.lastName} in '${option.vortragTitel}' `
    + `(${vorschlag.slotZeit}) nachbuchen? Der Teilnehmer wird per Nachricht informiert.`)) {
    return;
  }
  busy.value = true;
  error.value = '';
  try {
    await api.post(`/api/veranstaltungen/${props.vid}/teilnehmer/${props.teilnehmer.id}/nachbuchen`, {
      wahlvortragId: option.wahlvortragId,
      instanzIndex: option.instanzIndex,
    });
    erfolg.value = `Erfolgreich in '${option.vortragTitel}' nachgebucht.`;
    offeneVorschlaege.value = offeneVorschlaege.value.filter(v => v.slotId !== vorschlag.slotId);
  } catch (e) {
    error.value = 'Nachbuchung fehlgeschlagen: ' + (e.response?.data?.error || e.message);
  } finally {
    busy.value = false;
  }
};

const schliessen = () => {
  emit('close');
};
</script>
