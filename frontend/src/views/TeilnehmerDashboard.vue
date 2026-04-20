<template>
  <div class="max-w-6xl mx-auto space-y-8 pb-20">

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
                  :disabled="zuweisungen.length > 0"
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
        <button @click="saveAllPriorities" class="bg-green-600 hover:bg-green-700 text-white px-8 py-3 rounded-full shadow-lg font-bold flex items-center gap-2">
          <SaveIcon class="w-5 h-5" /> Auswahl speichern
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import api from '../api/axios';
import { useEventContextStore } from '../stores/eventContext';
import {
  User as UserIcon, Save as SaveIcon,
  CalendarCheck as CalendarCheckIcon, MapPin as MapPinIcon
} from 'lucide-vue-next';

const eventContext = useEventContextStore();
const vortraege = ref([]);
const prios = ref([]);
const zuweisungen = ref([]);

onMounted(async () => {
  try {
    const zuweisungenRes = await api.get('/api/teilnehmer/zuweisungen');
    zuweisungen.value = zuweisungenRes.data;

    const veranstaltungRes = await api.get('/api/teilnehmer/event');
    eventContext.setEvent(veranstaltungRes.data);

    const [vortragRes, prioRes] = await Promise.all([
      api.get('/api/admin/vortraege'),
      api.get('/api/teilnehmer/prios')
    ]);
    vortraege.value = vortragRes.data;
    prios.value = prioRes.data.map(p => ({ vortragId: p.vortrag.id, prioWert: p.prioWert }));
  } catch (err) {
    console.error("Fehler beim Laden:", err);
  }
});

const getCurrentPriority = (vortragId) => prios.value.find(p => p.vortragId === vortragId)?.prioWert;
const isRankTaken = (rank) => prios.value.some(p => p.prioWert == rank);

const updatePriority = (vortragId, value) => {
  if (zuweisungen.value.length > 0) return;
  prios.value = prios.value.filter(p => p.vortragId !== vortragId);
  if (value !== "") {
    prios.value = prios.value.filter(p => p.prioWert != value);
    prios.value.push({ vortragId, prioWert: parseInt(value) });
  }
};

const saveAllPriorities = async () => {
  try {
    const payload = prios.value.map(p => ({ vortragId: p.vortragId, prioWert: p.prioWert }));
    await api.post('/api/teilnehmer/priorities', payload);
    alert("Erfolgreich gespeichert!");
  } catch (e) {
    alert("Fehler beim Speichern.");
  }
};
</script>

<style scoped>
.animate-fade-in { animation: fadeIn 0.3s ease-in-out; }
@keyframes fadeIn { from { opacity: 0; transform: translateY(5px); } to { opacity: 1; transform: translateY(0); } }
</style>
