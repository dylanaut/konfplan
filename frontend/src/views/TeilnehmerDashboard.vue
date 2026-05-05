<template>
  <div class="max-w-6xl mx-auto space-y-8 pb-20">
    <!-- App Logo -->
    <div class="flex justify-center py-4">
      <img src="/logo/konfplan-light.svg" alt="Konfplan Logo" class="h-16" />
    </div>

    <!-- VERANSTALTUNGS-AUSWAHL -->
    <section class="bg-white p-6 rounded-xl shadow-sm border border-gray-100 flex flex-col md:flex-row md:items-center justify-between gap-4">
      <div>
        <h1 class="text-xl font-bold text-gray-900">Mein Dashboard</h1>
        <p class="text-sm text-gray-500">Wählen Sie eine Veranstaltung, um Ihren Plan zu sehen oder Prioritäten zu setzen.</p>
      </div>
      <div class="flex items-center gap-3">
        <label class="text-[10px] font-bold text-gray-500 uppercase tracking-wider">Veranstaltung:</label>
        <select v-model="selectedVid" @change="handleVeranstaltungChange"
                class="bg-gray-50 border border-gray-300 text-gray-900 text-sm rounded-lg focus:ring-indigo-500 focus:border-indigo-500 block w-full p-2.5 max-w-xs pr-12">
          <!-- Changed pr-12 to pr-16 to prevent arrow overlap -->
          <option :value="null">-- Bitte wählen --</option>
          <option v-for="v in veranstaltungen" :key="v.id" :value="v.id">
            {{ v.name }} ({{ formatDate(v.beginntAm) }})
          </option>
        </select>
      </div>
    </section>

    <div v-if="!selectedVid" class="bg-indigo-50 p-12 rounded-2xl text-center border-2 border-dashed border-indigo-200 animate-fade-in">
      <CalendarIcon class="w-12 h-12 text-indigo-300 mx-auto mb-4" />
      <h2 class="text-xl font-bold text-indigo-900">Keine Veranstaltung ausgewählt</h2>
      <p class="text-indigo-600 mt-2">Bitte wählen Sie oben eine Veranstaltung aus, um fortzufahren.</p>
    </div>

    <template v-else>
      <!-- MODUS: MEIN PLAN -->
      <section v-if="zuweisungen.length > 0" class="bg-indigo-900 text-white p-8 rounded-2xl shadow-2xl animate-fade-in">
        <div class="flex items-center gap-3 mb-6">
          <CalendarCheckIcon class="w-8 h-8 text-indigo-300" />
          <h2 class="text-3xl font-black">Mein Vortragsplan</h2>
        </div>
        <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          <div v-for="z in zuweisungen" :key="z.id" class="bg-white/10 border border-white/20 p-5 rounded-xl backdrop-blur-sm">
            <div class="text-[10px] uppercase font-bold text-indigo-300 mb-1">{{ z.slotZeit }}</div>
            <h3 class="text-lg font-bold mb-2">{{ z.vortragTitel }}</h3>
            <div class="flex items-center gap-2 text-sm text-indigo-100">
              <MapPinIcon class="w-4 h-4" />
              <span>{{ z.raumName }} ({{ z.gebaeudeName }})</span>
            </div>
          </div>
        </div>
      </section>

      <!-- SEKTION: Meine Verfügbarkeit -->
      <section class="bg-white p-6 rounded-xl shadow-sm border border-gray-100 animate-fade-in">
        <div v-if="currentEvent && currentEvent.deadlineTeilnehmer" :class="['mb-4 p-3 rounded-lg text-sm flex items-center gap-2', isDeadlinePassed(currentEvent.deadlineTeilnehmer) ? 'bg-red-50 border border-red-200 text-red-800' : 'bg-orange-50 border border-orange-200 text-orange-800']">
          <template v-if="isDeadlinePassed(currentEvent.deadlineTeilnehmer)">
            <XIcon class="w-4 h-4" /> Die Deadline für die Verfügbarkeitsangabe ist am {{ formatDateTime(currentEvent.deadlineTeilnehmer) }} abgelaufen.
          </template>
          <template v-else>
            <CalendarIcon class="w-4 h-4" /> Deadline für Verfügbarkeit: {{ formatDateTime(currentEvent.deadlineTeilnehmer) }}
          </template>
        </div>

        <div class="flex items-center gap-2 mb-6 text-indigo-600">
          <CheckSquareIcon class="w-6 h-6" />
          <h2 class="text-xl font-bold">Meine Verfügbarkeit</h2>
        </div>

        <div v-if="getSlotsForEvent.length === 0" class="text-xs text-gray-500 italic">
          Noch keine Zeit-Slots für diese Veranstaltung angelegt.
        </div>
        <div v-else class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-2">
          <button
              v-for="slot in getSlotsForEvent" :key="slot.id"
              @click="toggleAvailability(slot.id)"
              :disabled="isDeadlinePassed(currentEvent?.deadlineTeilnehmer)"
              :class="['p-2 rounded-lg border text-[10px] transition-all text-center',
                       isUserAvailable(slot.id) ? 'bg-indigo-600 text-white border-indigo-600 font-bold' : 'bg-gray-50 text-gray-400 border-gray-200',
                       isDeadlinePassed(currentEvent?.deadlineTeilnehmer) ? 'opacity-80 cursor-not-allowed' : 'hover:border-indigo-400']"
          >
            {{ formatTime(slot.startTime) }} - {{ formatTime(slot.endTime) }}
          </button>
        </div>
      </section>

      <!-- WAHL-MODUS MIT KOMPAKTER DARSTELLUNG -->
      <div class="space-y-6">
        <header class="bg-white p-6 rounded-xl shadow-sm border border-gray-100 flex justify-between items-center">
          <div>
            <h2 class="text-2xl font-bold text-gray-800 uppercase tracking-tight flex items-center gap-2">
              <StarIcon class="w-6 h-6 text-orange-500" /> Wahlvorträge & Prioritäten
            </h2>
            <p class="text-gray-600 mt-1">Wählen Sie Ihre Top 10 Vorträge aus. 1 = Höchste Priorität.</p>
          </div>
          <div class="hidden md:flex gap-2">
            <div v-for="n in 10" :key="n"
                 :class="['w-8 h-8 flex items-center justify-center rounded-full border text-[10px] font-black',
                          isRankTaken(n) ? 'bg-indigo-600 text-white border-indigo-600' : 'bg-gray-100 text-gray-400 border-gray-200']">
              {{ n }}
            </div>
          </div>
        </header>

        <!-- Legende der Wahlvorträge (wie im Admin-Bereich) -->
        <div v-if="electiveTalks.length > 0" class="bg-white p-6 rounded-xl shadow-sm border border-gray-100 animate-fade-in">
          <h3 class="font-black text-indigo-900 uppercase text-xs mb-4 flex items-center gap-2">
            <InfoIcon class="w-4 h-4" /> Übersicht der verfügbaren Wahlvorträge
          </h3>
          <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-x-8 gap-y-2">
            <div v-for="(talk, index) in electiveTalks" :key="'legende-'+talk.id" class="flex gap-3 items-start group">
              <span class="font-black text-indigo-600 shrink-0 w-5 text-right text-sm">{{ index + 1 }}:</span>
              <div class="min-w-0">
                <span class="text-gray-800 text-sm font-bold block truncate group-hover:text-indigo-600 transition-colors"
                      :title="`Referent: ${talk.referent?.lastName || 'N/A'}${talk.referent?.organisation ? ' (' + talk.referent.organisation + ')' : ''}`">
                  {{ talk.titel }}
                </span>
                <p class="text-[10px] text-gray-500 italic truncate">{{ talk.referent?.firstName }} {{ talk.referent?.lastName }}</p>
              </div>
            </div>
          </div>
        </div>

        <!-- Kompakte Prioritäten-Tabelle -->
        <div v-if="electiveTalks.length > 0" class="bg-white shadow rounded-xl border border-gray-100 overflow-hidden animate-fade-in">
          <table class="min-w-full divide-y divide-gray-200 text-xs table-fixed">
            <thead class="bg-gray-50 text-[10px] uppercase font-black text-gray-500">
            <tr>
              <th class="px-6 py-3 text-left w-64 border-r border-gray-100">Info</th>
              <th v-for="(talk, index) in electiveTalks" :key="'header-'+talk.id"
                  class="px-1 py-3 text-center text-[10px] font-black text-indigo-600 w-14 min-w-[56px] border-r border-gray-100 bg-indigo-50/30"
                  :title="talk.titel">
                {{ index + 1 }}
              </th>
              <th class="w-auto bg-gray-50"></th>
            </tr>
            </thead>
            <tbody class="divide-y divide-gray-100">
            <tr class="hover:bg-gray-50/50 transition-colors">
              <td class="px-6 py-4 border-r border-gray-100 bg-gray-50/30">
                <div class="flex flex-col">
                  <span class="font-black text-indigo-900 uppercase text-[10px]">Meine Wahl</span>
                  <span class="text-gray-500 text-[9px]">Geben Sie hier Ihre Prioritäten (1-10) ein</span>
                </div>
              </td>
              <td v-for="talk in electiveTalks" :key="'cell-'+talk.id" class="px-1 py-4 text-center border-r border-gray-50">
                <input type="number" min="1" max="10"
                       :value="getCurrentPriority(talk.id) || ''"
                       @input="updatePriority(talk.id, $event.target.value)"
                       :disabled="zuweisungen.length > 0 || isDeadlinePassed(currentEvent?.deadlineTeilnehmer)"
                       class="w-12 mx-auto text-center border rounded-lg py-1.5 text-xs font-bold focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 border-gray-200 bg-white transition-all disabled:bg-gray-100 disabled:text-gray-300" />
              </td>
              <td class="bg-gray-50/10"></td>
            </tr>
            </tbody>
          </table>
        </div>

        <!-- Save Button -->
        <div v-if="zuweisungen.length === 0" class="flex justify-end mt-8">
          <button @click="saveAllPriorities"
                  :disabled="isDeadlinePassed(currentEvent?.deadlineTeilnehmer)"
                  class="bg-green-600 hover:bg-green-700 text-white px-10 py-4 rounded-2xl shadow-xl font-black uppercase tracking-widest text-xs flex items-center gap-3 transition-all hover:scale-105 active:scale-95 disabled:opacity-50 disabled:hover:scale-100">
            <SaveIcon class="w-5 h-5" /> Auswahl jetzt speichern
          </button>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue';
import api from '../api/axios';
import { useEventContextStore } from '../stores/eventContext';
import {
  User as UserIcon, Save as SaveIcon,
  CalendarCheck as CalendarCheckIcon, MapPin as MapPinIcon,
  Calendar as CalendarIcon, CheckSquare as CheckSquareIcon, X as XIcon,
  Star as StarIcon, Info as InfoIcon
} from 'lucide-vue-next';

const eventContext = useEventContextStore();
const veranstaltungen = ref([]);
const selectedVid = ref(null);
const vortraege = ref([]);
const prios = ref([]);
const zuweisungen = ref([]);
const allSlots = ref([]);
const teilnehmerAvailabilities = ref([]);

const currentEvent = computed(() => {
  return veranstaltungen.value.find(v => v.id === selectedVid.value);
});

// Filter elective talks from all talks
const electiveTalks = computed(() => {
  return vortraege.value.filter(v => !v.istPflicht);
});

onMounted(async () => {
  try {
    const vRes = await api.get('/api/teilnehmer/veranstaltungen');
    veranstaltungen.value = vRes.data;

    if (veranstaltungen.value.length === 1) {
      selectedVid.value = veranstaltungen.value[0].id;
      await handleVeranstaltungChange();
    }
  } catch (err) {
    console.error("Fehler beim Laden der Veranstaltungen:", err);
  }
});

const handleVeranstaltungChange = async () => {
  if (!selectedVid.value) {
    vortraege.value = [];
    prios.value = [];
    zuweisungen.value = [];
    allSlots.value = [];
    teilnehmerAvailabilities.value = [];
    eventContext.clearEvent();
    return;
  }

  try {
    const ev = veranstaltungen.value.find(v => v.id === selectedVid.value);
    eventContext.setEvent(ev);

    const [zuweisungenRes, vortragRes, prioRes, slotsRes, availabilitiesRes] = await Promise.all([
      api.get(`/api/teilnehmer/zuweisungen?vid=${selectedVid.value}`),
      api.get(`/api/teilnehmer/vortraege?vid=${selectedVid.value}`),
      api.get(`/api/teilnehmer/prios?vid=${selectedVid.value}`),
      api.get(`/api/veranstaltungen/${selectedVid.value}/slots`),
      api.get(`/api/teilnehmer/veranstaltungen/${selectedVid.value}/verfuegbarkeiten`)
    ]);

    zuweisungen.value = zuweisungenRes.data;
    vortraege.value = vortragRes.data;
    prios.value = prioRes.data.map(p => ({ vortragId: p.vortrag.id, prioWert: p.prioWert }));
    allSlots.value = slotsRes.data;
    teilnehmerAvailabilities.value = availabilitiesRes.data.filter(v => v.isAvailable).map(v => v.slotId);

  } catch (err) {
    console.error("Fehler beim Laden der Veranstaltungsdaten:", err);
  }
};

const getCurrentPriority = (vortragId) => prios.value.find(p => p.vortragId === vortragId)?.prioWert;
const isRankTaken = (rank) => prios.value.some(p => p.prioWert == rank);

const updatePriority = (vortragId, value) => {
  if (zuweisungen.value.length > 0 || isDeadlinePassed(currentEvent.value?.deadlineTeilnehmer)) return;

  // Remove old priority for this talk
  prios.value = prios.value.filter(p => p.vortragId !== vortragId);

  if (value !== "") {
    const val = parseInt(value);
    // If another talk has this priority, clear it (enforce unique ranks 1-10)
    prios.value = prios.value.filter(p => p.prioWert !== val);
    prios.value.push({ vortragId, prioWert: val });
  }
};

const saveAllPriorities = async () => {
  if (isDeadlinePassed(currentEvent.value?.deadlineTeilnehmer)) {
    alert("Die Deadline für die Prioritätenangabe ist abgelaufen. Speichern nicht möglich.");
    return;
  }
  try {
    const payload = prios.value.map(p => ({ vortragId: p.vortragId, prioWert: p.prioWert }));
    await api.post('/api/teilnehmer/priorities', payload);
    await handleVeranstaltungChange();
    alert("Erfolgreich gespeichert!");
  } catch (e) {
    alert("Fehler beim Speichern.");
  }
};

const getSlotsForEvent = computed(() => {
  return allSlots.value.sort((a, b) => new Date(a.startTime) - new Date(b.startTime));
});

const isUserAvailable = (slotId) => {
  return teilnehmerAvailabilities.value.includes(slotId);
};

const toggleAvailability = async (slotId) => {
  if (isDeadlinePassed(currentEvent.value?.deadlineTeilnehmer)) return;

  const current = isUserAvailable(slotId);
  const newValue = !current;

  try {
    await api.post(`/api/teilnehmer/veranstaltungen/${selectedVid.value}/verfuegbarkeiten`, {
      slotId: slotId,
      isAvailable: newValue
    });

    if (newValue) {
      teilnehmerAvailabilities.value.push(slotId);
    } else {
      teilnehmerAvailabilities.value = teilnehmerAvailabilities.value.filter(id => id !== slotId);
    }
  } catch (e) {
    alert("Fehler beim Aktualisieren der Verfügbarkeit: " + (e.response?.data || e.message));
  }
};

const isDeadlinePassed = (deadline) => {
    if (!deadline) return false;
    return new Date(deadline) < new Date();
};

const formatDate = (d) => d ? new Date(d).toLocaleDateString('de-DE') : '';
const formatDateTime = (dt) => dt ? new Date(dt).toLocaleTimeString('de-DE', {weekday: 'short', day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit'}) : '';
const formatTime = (t) => t ? new Date(t).toLocaleTimeString('de-DE', {hour: '2-digit', minute: '2-digit'}) : '';
</script>

<style scoped>
.animate-fade-in { animation: fadeIn 0.3s ease-in-out; }
@keyframes fadeIn { from { opacity: 0; transform: translateY(5px); } to { opacity: 1; transform: translateY(0); } }

/* Chrome, Safari, Edge, Opera */
input::-webkit-outer-spin-button,
input::-webkit-inner-spin-button {
  -webkit-appearance: none;
  margin: 0;
}

/* Firefox */
input[type=number] {
  -moz-appearance: textfield;
}
</style>
