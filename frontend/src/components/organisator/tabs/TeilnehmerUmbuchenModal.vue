<template>
  <div v-if="isVisible" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4">
    <div class="w-full max-w-3xl rounded-xl bg-white p-6 shadow-2xl overflow-y-auto max-h-[90vh]">
      <div class="flex items-center justify-between mb-4">
        <h2 class="text-xl font-bold text-gray-900">Teilnehmer manuell umbuchen</h2>
        <button class="text-gray-500 hover:text-gray-700" @click="schliessen">✕</button>
      </div>

      <p class="text-xs text-gray-500 mb-4">
        Bucht einen einzelnen Teilnehmer von einem Wahlvortrag auf einen anderen im selben Zeitslot um -
        z.B. wenn nachträglich ein unpassender Wahlvortrag priorisiert wurde und die Teilnehmer-Deadline
        bereits abgelaufen ist. Unabhängig von der Deadline, da organisatorgetrieben.
      </p>

      <div v-if="error" class="bg-red-50 border border-red-200 text-red-700 text-xs rounded-xl p-3 mb-4">{{ error }}</div>
      <div v-if="erfolg" class="bg-green-50 border border-green-200 text-green-800 text-xs rounded-xl p-3 mb-4">{{ erfolg }}</div>

      <!-- Schritt 1: Teilnehmer suchen -->
      <div v-if="!ausgewaehlterTeilnehmer" class="mb-2">
        <label class="form-label small fw-bold text-gray-500 text-xs">Teilnehmer suchen:</label>
        <input v-model="suchtext" type="text" placeholder="Name eingeben..."
               class="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm mb-2"/>
        <div class="max-h-64 overflow-y-auto divide-y divide-gray-100 border border-gray-100 rounded-lg">
          <button v-for="t in gefilterteTeilnehmer" :key="t.id" @click="teilnehmerAuswaehlen(t)"
                  class="w-full text-left px-3 py-2 text-sm hover:bg-gray-50">
            {{ t.firstName }} {{ t.lastName }}
          </button>
          <div v-if="gefilterteTeilnehmer.length === 0" class="px-3 py-4 text-center text-xs text-gray-400">
            Keine Teilnehmer gefunden.
          </div>
        </div>
      </div>

      <!-- Schritt 2: aktuelle Zuweisung wählen -->
      <template v-else>
        <div class="flex items-center justify-between mb-3 bg-gray-50 rounded-lg p-2">
          <span class="text-sm font-semibold">{{ ausgewaehlterTeilnehmer.firstName }} {{ ausgewaehlterTeilnehmer.lastName }}</span>
          <button @click="zurueckZurSuche" class="text-xs text-indigo-600 hover:underline">Anderen Teilnehmer wählen</button>
        </div>

        <div v-if="loadingZuweisungen" class="text-center text-xs text-gray-500 py-6">Lade aktuelle Zuweisungen...</div>
        <div v-else-if="zuweisungen.length === 0" class="text-center text-xs text-gray-500 py-6">
          Dieser Teilnehmer hat in diesem Planungsergebnis keine Wahlvortrag-Zuweisung.
        </div>
        <template v-else-if="!aktuelleZuweisung">
          <p class="text-xs text-gray-500 mb-2">Welche Zuweisung soll geändert werden?</p>
          <table class="min-w-full divide-y divide-gray-200 text-xs mb-2">
            <thead class="bg-gray-50 text-[9px] uppercase font-bold text-gray-500">
            <tr>
              <th class="px-3 py-1.5 text-left font-bold">Zeit</th>
              <th class="px-3 py-1.5 text-left font-bold">Vortrag</th>
              <th class="px-3 py-1.5 text-left font-bold">Raum</th>
              <th class="px-3 py-1.5 text-right font-bold">Aktion</th>
            </tr>
            </thead>
            <tbody class="divide-y divide-gray-100">
            <tr v-for="z in zuweisungen" :key="`${z.wahlvortragId}-${z.instanzIndex}`" class="hover:bg-gray-50">
              <td class="px-3 py-2">{{ z.slotZeit }}</td>
              <td class="px-3 py-2">{{ z.vortragTitel }}</td>
              <td class="px-3 py-2">{{ z.raumName }}</td>
              <td class="px-3 py-2 text-right">
                <button @click="zuweisungAendern(z)" class="px-2 py-1 bg-white text-gray-700 rounded border border-gray-200 hover:bg-gray-50">
                  Ändern
                </button>
              </td>
            </tr>
            </tbody>
          </table>
        </template>

        <!-- Schritt 3: Optionen zur Beratung anzeigen, Ziel wählen -->
        <template v-else>
          <div class="flex items-center justify-between mb-3 bg-amber-50 border border-amber-200 rounded-lg p-2 text-xs">
            <span>Aktuell: <b>{{ aktuelleZuweisung.vortragTitel }}</b> ({{ aktuelleZuweisung.slotZeit }}, {{ aktuelleZuweisung.raumName }})</span>
            <button @click="aktuelleZuweisung = null" class="text-indigo-600 hover:underline">Zurück</button>
          </div>

          <div v-if="loadingOptionen" class="text-center text-xs text-gray-500 py-6">Lade Alternativen...</div>
          <div v-else-if="optionen.length === 0" class="text-center text-xs text-gray-500 py-6">
            Keine Alternative im selben Zeitslot mit freiem Platz gefunden.
          </div>
          <table v-else class="min-w-full divide-y divide-gray-200 text-xs">
            <thead class="bg-gray-50 text-[9px] uppercase font-bold text-gray-500">
            <tr>
              <th class="px-3 py-1.5 text-left font-bold">Vortrag</th>
              <th class="px-3 py-1.5 text-left font-bold">Referent</th>
              <th class="px-3 py-1.5 text-left font-bold">Raum</th>
              <th class="px-3 py-1.5 text-center font-bold">Frei</th>
              <th class="px-3 py-1.5 text-center font-bold" title="Anzahl gemeinsamer Neigungen mit dem Teilnehmer - Entscheidungshilfe zur Beratung">Passung</th>
              <th class="px-3 py-1.5 text-right font-bold">Aktion</th>
            </tr>
            </thead>
            <tbody class="divide-y divide-gray-100">
            <tr v-for="o in optionen" :key="`${o.wahlvortragId}-${o.instanzIndex}`" class="hover:bg-gray-50">
              <td class="px-3 py-2">{{ o.vortragTitel }}</td>
              <td class="px-3 py-2">{{ o.referentName }}</td>
              <td class="px-3 py-2">{{ o.raumName }}</td>
              <td class="px-3 py-2 text-center">{{ o.kapazitaet - o.belegteAnzahl }} / {{ o.kapazitaet }}</td>
              <td class="px-3 py-2 text-center">
                <span v-if="o.neigungsUeberschneidung > 0" class="px-1.5 py-0.5 rounded bg-green-100 text-green-700">{{ o.neigungsUeberschneidung }}</span>
                <span v-else class="text-gray-400">-</span>
              </td>
              <td class="px-3 py-2 text-right">
                <button @click="umbuchen(o)" :disabled="busy"
                        class="px-2 py-1 bg-indigo-500 text-white rounded hover:bg-indigo-600 disabled:opacity-50">
                  Umbuchen
                </button>
              </td>
            </tr>
            </tbody>
          </table>
        </template>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue';
import api from '../../../api/axios';

const props = defineProps({
  isVisible: { type: Boolean, required: true },
  vid: { type: [Number, String], default: null },
  ergebnisId: { type: [Number, String], default: null },
  teilnehmer: { type: Array, default: () => [] },
});

const emit = defineEmits(['close']);

const suchtext = ref('');
const ausgewaehlterTeilnehmer = ref(null);
const zuweisungen = ref([]);
const loadingZuweisungen = ref(false);
const aktuelleZuweisung = ref(null);
const optionen = ref([]);
const loadingOptionen = ref(false);
const busy = ref(false);
const error = ref('');
const erfolg = ref('');

const gefilterteTeilnehmer = computed(() => {
  const suche = suchtext.value.trim().toLowerCase();
  if (!suche) return props.teilnehmer;
  return props.teilnehmer.filter(t =>
    `${t.firstName} ${t.lastName}`.toLowerCase().includes(suche));
});

const reset = () => {
  suchtext.value = '';
  ausgewaehlterTeilnehmer.value = null;
  zuweisungen.value = [];
  aktuelleZuweisung.value = null;
  optionen.value = [];
  error.value = '';
  erfolg.value = '';
};

const schliessen = () => {
  reset();
  emit('close');
};

const zurueckZurSuche = () => {
  ausgewaehlterTeilnehmer.value = null;
  zuweisungen.value = [];
  aktuelleZuweisung.value = null;
  optionen.value = [];
  erfolg.value = '';
};

const teilnehmerAuswaehlen = async (t) => {
  ausgewaehlterTeilnehmer.value = t;
  error.value = '';
  erfolg.value = '';
  loadingZuweisungen.value = true;
  try {
    const res = await api.get(`/api/veranstaltungen/${props.vid}/planungsergebnisse/${props.ergebnisId}/teilnehmer/${t.id}/zuweisungen`);
    zuweisungen.value = res.data;
  } catch (e) {
    error.value = 'Fehler beim Laden der Zuweisungen: ' + (e.response?.data?.error || e.message);
  } finally {
    loadingZuweisungen.value = false;
  }
};

const zuweisungAendern = async (z) => {
  aktuelleZuweisung.value = z;
  error.value = '';
  loadingOptionen.value = true;
  try {
    const res = await api.get(
      `/api/veranstaltungen/${props.vid}/planungsergebnisse/${props.ergebnisId}/teilnehmer/${ausgewaehlterTeilnehmer.value.id}/umbuchen-optionen`,
      { params: { wahlvortragId: z.wahlvortragId, instanzIndex: z.instanzIndex } }
    );
    optionen.value = res.data;
  } catch (e) {
    error.value = 'Fehler beim Laden der Alternativen: ' + (e.response?.data?.error || e.message);
  } finally {
    loadingOptionen.value = false;
  }
};

const umbuchen = async (option) => {
  if (!confirm(`${ausgewaehlterTeilnehmer.value.firstName} ${ausgewaehlterTeilnehmer.value.lastName} von `
    + `'${aktuelleZuweisung.value.vortragTitel}' auf '${option.vortragTitel}' umbuchen?`)) {
    return;
  }
  busy.value = true;
  error.value = '';
  try {
    await api.post(
      `/api/veranstaltungen/${props.vid}/planungsergebnisse/${props.ergebnisId}/teilnehmer/${ausgewaehlterTeilnehmer.value.id}/umbuchen`,
      {
        altWahlvortragId: aktuelleZuweisung.value.wahlvortragId,
        altInstanzIndex: aktuelleZuweisung.value.instanzIndex,
        neuWahlvortragId: option.wahlvortragId,
        neuInstanzIndex: option.instanzIndex,
      }
    );
    erfolg.value = `Erfolgreich auf '${option.vortragTitel}' umgebucht.`;
    aktuelleZuweisung.value = null;
    optionen.value = [];
    await teilnehmerAuswaehlen(ausgewaehlterTeilnehmer.value);
  } catch (e) {
    error.value = 'Umbuchung fehlgeschlagen: ' + (e.response?.data?.error || e.message);
  } finally {
    busy.value = false;
  }
};

watch(() => props.isVisible, (visible) => {
  if (!visible) {
    reset();
  }
});
</script>
