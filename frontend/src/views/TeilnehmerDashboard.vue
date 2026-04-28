<template>
  <div class="max-w-6xl mx-auto space-y-8 pb-20">

    <!-- VERANSTALTUNGS-AUSWAHL -->
    <section class="bg-white p-6 rounded-xl shadow-sm border border-gray-100 flex flex-col md:flex-row md:items-center justify-between gap-4">
      <div>
        <h1 class="text-xl font-bold text-gray-900">Mein Dashboard</h1>
        <p class="text-sm text-gray-500">Wählen Sie eine Veranstaltung, um Ihren Plan zu sehen oder Prioritäten zu setzen.</p>
      </div>
      <div class="flex items-center gap-3">
        <label class="text-[10px] font-bold text-gray-500 uppercase tracking-wider">Veranstaltung:</label>
        <select v-model="selectedVid" @change="handleVeranstaltungChange"
                class="bg-gray-50 border border-gray-300 text-gray-900 text-sm rounded-lg focus:ring-indigo-500 focus:border-indigo-500 block w-full p-2.5 max-w-xs">
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

      <!-- NEUE SEKTION: Meine Verfügbarkeit -->
      <section class="bg-white p-6 rounded-xl shadow-sm border border-gray-100 animate-fade-in">
        <div class="flex items-center gap-2 mb-6 text-indigo-600">
          <CheckSquareIcon class="w-6 h-6" />
          <h2 class="text-xl font-bold">Meine Verfügbarkeit</h2>
        </div>

        <div v-if="currentEvent && currentEvent.deadlineTeilnehmer" :class="['mb-4 p-3 rounded-lg text-sm flex items-center gap-2', isDeadlinePassed(currentEvent.deadlineTeilnehmer) ? 'bg-red-50 border border-red-200 text-red-800' : 'bg-orange-50 border border-orange-200 text-orange-800']">
          <template v-if="isDeadlinePassed(currentEvent.deadlineTeilnehmer)">
            <XIcon class="w-4 h-4" /> Die Deadline für die Verfügbarkeitsangabe ist am {{ formatDateTime(currentEvent.deadlineTeilnehmer) }} abgelaufen.
          </template>
          <template v-else>
            <CalendarIcon class="w-4 h-4" /> Deadline für Verfügbarkeit: {{ formatDateTime(currentEvent.deadlineTeilnehmer) }}
          </template>
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

      <!-- HEADER & WAHL-MODUS -->
      <div class="space-y-6">
        <header class="bg-white p-6 rounded-xl shadow-sm border border-gray-100">
          <h2 class="text-2xl font-bold text-gray-800">Verfügbare Vorträge</h2>
          <p class="text-gray-600 mt-1">Wählen Sie Ihre Top 10 Vorträge aus. 1 = Höchste Priorität.</p>
          <div class="mt-4 flex flex-wrap gap-2">
            <div v-for="n in 10" :key="n"
                 :class="['w-8 h-8 flex items-center justify-center rounded-full border text-xs font-bold',
                          isRankTaken(n) ? 'bg-indigo-600 text-white border-indigo-600' : 'bg-gray-100 text-gray-400 border-gray-200']">
              {{ n }}
            </div>
          </div>
        </header>

        <!-- Vortrags-Grid -->
        <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          <div v-for="vortrag in vortraege" :key="vortrag.id"
               :class="['bg-white rounded-xl shadow-sm border flex flex-col overflow-hidden hover:shadow-md transition-shadow relative',
                        vortrag.istPflicht ? 'border-red-300 ring-1 ring-red-100' : 'border-gray-100']">

            <div v-if="vortrag.istPflicht" class="absolute top-0 right-0 bg-red-600 text-white text-[10px] font-black px-3 py-1 rounded-bl-lg uppercase tracking-widest shadow-sm z-10">
              Pflicht
            </div>

            <div class="p-5 flex-1">
              <div class="flex justify-between items-start mb-2">
                <span class="text-xs font-semibold uppercase tracking-wider text-indigo-500">{{ vortrag.zielgruppe }}</span>
                <span v-if="getCurrentPriority(vortrag.id)" class="bg-indigo-100 text-indigo-700 px-2 py-1 rounded text-xs font-bold">
                  Prio {{ getCurrentPriority(vortrag.id) }}
                </span>
              </div>

              <h3 class="text-lg font-bold text-gray-900 leading-tight mb-2 pr-12">{{ vortrag.titel }}</h3>

              <div class="flex flex-col mb-4">
                <p class="text-sm font-bold text-gray-700 flex items-center cursor-help"
                   :title="vortrag.referent?.biography || 'Keine Biografie hinterlegt'">
                  <UserIcon class="w-4 h-4 mr-1 text-indigo-500" />
                  {{ vortrag.referent?.firstName }} {{ vortrag.referent?.lastName }}
                </p>
                <p v-if="vortrag.referent?.organisation"
                   class="text-xs text-gray-500 ml-5 italic cursor-help"
                   :title="vortrag.referent?.slogan || 'Kein Slogan hinterlegt'">
                  {{ vortrag.referent.organisation }}
                </p>
              </div>

              <p class="text-gray-600 text-sm mb-4">
                {{ vortrag.inhalt }}
              </p>
            </div>

            <!-- Footer -->
            <div class="bg-gray-50 p-4 border-t border-gray-100 mt-auto">
              <div v-if="vortrag.istPflicht" class="text-xs text-red-600 font-medium italic text-center py-2">
                Pflichtveranstaltung
              </div>
              <template v-else>
                <select
                    :disabled="zuweisungen.length > 0 || isDeadlinePassed(currentEvent?.deadlineTeilnehmer)"
                    :value="getCurrentPriority(vortrag.id) || ''"
                    @change="updatePriority(vortrag.id, $event.target.value)"
                    class="w-full bg-white border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-indigo-500 outline-none disabled:bg-gray-100 disabled:text-gray-400"
                >
                  <option value="">Keine Wahl</option>
                  <option v-for="n in 10" :key="n" :value="n" :disabled="isRankTaken(n) && getCurrentPriority(vortrag.id) !== n">
                    Rang {{ n }}
                  </option>
                </select>
              </template>
            </div>
          </div>
        </div>

        <!-- Save Button -->
        <div v-if="zuweisungen.length === 0" class="fixed bottom-6 right-6 lg:static lg:mt-8 lg:flex lg:justify-end">
          <button @click="saveAllPriorities" :disabled="isDeadlinePassed(currentEvent?.deadlineTeilnehmer)" class="bg-green-600 hover:bg-green-700 text-white px-8 py-3 rounded-full shadow-lg font-bold flex items-center gap-2">
            <SaveIcon class="w-5 h-5" /> Auswahl speichern
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
  Calendar as CalendarIcon, CheckSquare as CheckSquareIcon, X as XIcon
} from 'lucide-vue-next';

const eventContext = useEventContextStore();
const veranstaltungen = ref([]);
const selectedVid = ref(null);
const vortraege = ref([]);
const prios = ref([]);
const zuweisungen = ref([]);
const allSlots = ref([]); // To store all slots for the selected event
const teilnehmerAvailabilities = ref([]); // To store participant's availabilities for the selected event

const currentEvent = computed(() => {
  return veranstaltungen.value.find(v => v.id === selectedVid.value);
});

onMounted(async () => {
  try {
    const vRes = await api.get('/api/teilnehmer/veranstaltungen');
    veranstaltungen.value = vRes.data;

    // Wenn nur eine Veranstaltung vorhanden ist, diese direkt auswählen
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
      api.get(`/api/veranstaltungen/${selectedVid.value}/slots`), // Fetch slots for the selected event
      api.get(`/api/teilnehmer/veranstaltungen/${selectedVid.value}/verfuegbarkeiten`) // Fetch participant's availabilities
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
  prios.value = prios.value.filter(p => p.vortragId !== vortragId);
  if (value !== "") {
    prios.value = prios.value.filter(p => p.prioWert != value);
    prios.value.push({ vortragId, prioWert: parseInt(value) });
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
    await handleVeranstaltungChange(); // Refresh data after save
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

    // Lokal aktualisieren
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
const formatDateTime = (dt) => dt ? new Date(dt).toLocaleDateString('de-DE', {weekday: 'short', day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit'}) : '';
const formatTime = (t) => t ? new Date(t).toLocaleTimeString('de-DE', {hour: '2-digit', minute: '2-digit'}) : '';
</script>

<style scoped>
.animate-fade-in { animation: fadeIn 0.3s ease-in-out; }
@keyframes fadeIn { from { opacity: 0; transform: translateY(5px); } to { opacity: 1; transform: translateY(0); } }
</style>
