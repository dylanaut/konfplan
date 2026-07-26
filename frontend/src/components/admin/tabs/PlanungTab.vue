<template>
  <section class="space-y-4 animate-fade-in">
    <!-- Kennzahlen-Übersicht -->
    <div class="bg-white p-6 rounded-xl shadow-sm border border-gray-100">
      <h3 class="text-base font-bold text-gray-800 mb-4">Planungsrahmen</h3>
      <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6 text-xs">
        <!-- Block 1: Allgemein -->
        <div class="space-y-2 bg-gray-50 p-3 rounded-lg border border-gray-200">
          <p class="font-bold text-gray-600">Allgemein</p>
          <p><strong>Organisatoren:</strong> {{ organisatoren.join(', ') || 'N/A' }}</p>
          <p><strong>Zeitraum:</strong> {{ zeitraumAnzeige }}</p>
          <p><strong>Zeit-Slots:</strong> {{ eventSlotsCount }}</p>
        </div>

        <!-- Block 2: Gebäude & Räume -->
        <div class="space-y-2 bg-gray-50 p-3 rounded-lg border border-gray-200">
          <p class="font-bold text-gray-600">Gebäude & Räume</p>
          <div v-if="gebaeudeDetails.length > 0">
            <div v-for="g in gebaeudeDetails" :key="g.name">
              <p><strong>{{ g.name }}:</strong> {{ g.raumCount }} Räume (Σ {{ g.kapazitaetGesamt }} Plätze)</p>
            </div>
          </div>
          <p v-else class="text-gray-500 italic">Keine Gebäude zugewiesen.</p>
        </div>

        <!-- Block 3: Personen -->
        <div class="space-y-2 bg-gray-50 p-3 rounded-lg border border-gray-200">
          <p class="font-bold text-gray-600">Personen</p>
          <p><strong>Referenten:</strong> {{ referentenDetails.length }}</p>
          <p><strong>Teilnehmer:</strong> {{ teilnehmerCount }}</p>
          <p><strong>TN mit Prioritäten:</strong> {{ teilnehmerMitPrioritaetenCount }}</p>
        </div>

        <!-- Block 4: Vorträge -->
        <div class="space-y-2 bg-gray-50 p-3 rounded-lg border border-gray-200">
          <p class="font-bold text-gray-600">Vorträge</p>
          <p><strong>Wahlvorträge:</strong> {{ wahlvortraegeCount }}</p>
          <p><strong>Pflichtvorträge:</strong> {{ pflichtvortraegeCount }}</p>
        </div>
      </div>
    </div>

    <!-- Planerstellung -->
    <div class="bg-indigo-900 text-white p-6 rounded-2xl shadow-xl flex flex-col md:flex-row items-end justify-between gap-6">
      <div class="space-y-3 flex-1 w-full">
        <h2 class="text-2xl font-black">Planerstellung</h2>
        <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-6 gap-3 bg-white/10 p-3 rounded-xl border border-white/10">
          <div>
            <label class="block text-[9px] uppercase font-bold text-indigo-300 mb-0.5">MiniZinc Solver</label>
            <select v-model="solverConfig.solver" class="w-full bg-indigo-800 border-none rounded text-xs text-white focus:ring-2 focus:ring-green-400 py-1">
              <option value="cp-sat">Google OR-Tools</option>
              <option value="Gecode">Gecode</option>
              <option value="coinbc">COIN-BC</option>
            </select>
          </div>
          <div>
            <label class="block text-[9px] uppercase font-bold text-indigo-300 mb-0.5">Timeout (Sek.)</label>
            <input v-model.number="solverConfig.timeout" type="number" class="w-full bg-indigo-800 border-none rounded text-xs text-white focus:ring-2 focus:ring-green-400 py-1 px-2"/>
          </div>
          <div>
            <label class="block text-[9px] uppercase font-bold text-indigo-300 mb-0.5">max. Vortragswiederholungen</label>
            <input v-model.number="solverConfig.maxInstanzen" type="number" class="w-full bg-indigo-800 border-none rounded text-xs text-white focus:ring-2 focus:ring-green-400 py-1 px-2"/>
          </div>
          <div>
            <label class="block text-[9px] uppercase font-bold text-indigo-300 mb-0.5">Leistung (1-5)</label>
            <input v-model.number="solverConfig.numThreads" type="number" class="w-full bg-indigo-800 border-none rounded text-xs text-white focus:ring-2 focus:ring-green-400 py-1 px-2"/>
          </div>
          <div title="Maximale Anzahl Wahlvorträge pro Teilnehmer. 0 = kein Limit">
            <label class="block text-[9px] uppercase font-bold text-indigo-300 mb-0.5">max. WV pro TN (0=∞)</label>
            <input v-model.number="solverConfig.maxWvsProTn" type="number" min="0" class="w-full bg-indigo-800 border-none rounded text-xs text-white focus:ring-2 focus:ring-green-400 py-1 px-2"/>
          </div>
          <div class="flex items-center justify-center pt-3" title="Freie Plätze sollen in Wahlvorträgen mit nicht-verplanten Teilnehmern aufgefüllt werden">
            <label class="flex items-center space-x-2 cursor-pointer text-xs">
              <input v-model="solverConfig.auffuellen" type="checkbox" class="bg-indigo-800 border-indigo-600 rounded text-green-500 focus:ring-green-400"/>
              <span>Auffüllen?</span>
            </label>
          </div>
        </div>
      </div>
      <div class="text-right">
        <p v-if="teilnehmerMitPrioritaetenCount === 0" class="text-red-300 text-center text-[10px] mt-1.5 font-bold animate-pulse">
          Keine Prioritäten vorhanden.
        </p>
        <button v-if="!isPlanning" @click="emit('startOptimization', solverConfig)" :disabled="teilnehmerMitPrioritaetenCount === 0" class="bg-green-500 hover:bg-green-400 disabled:bg-gray-600 text-white px-8 py-4 rounded-xl font-black text-lg shadow-2xl transition-all transform hover:scale-105 flex items-center gap-3">
          <ZapIcon class="w-5 h-5"/>
          Pläne erstellen
        </button>
        <button v-else @click="emit('cancelOptimization')" class="bg-red-500 hover:bg-red-400 text-white px-8 py-4 rounded-xl font-black text-lg shadow-2xl transition-all transform hover:scale-105 flex items-center gap-3">
          <LoaderIcon class="animate-spin w-5 h-5"/>
          Erstellung abbrechen
        </button>
      </div>
    </div>
  </section>
</template>

<script setup>
import { reactive, computed } from 'vue';
import { Loader as LoaderIcon, Zap as ZapIcon, XCircle as CancelIcon } from '@lucide/vue';

const props = defineProps({
  isPlanning: Boolean,
  veranstaltung: Object,
  organisatoren: Array,
  eventSlotsCount: Number,
  gebaeudeDetails: Array,
  referentenDetails: Array,
  teilnehmerCount: Number,
  wahlvortraegeCount: Number,
  pflichtvortraegeCount: Number,
  teilnehmerMitPrioritaetenCount: Number,
});

const emit = defineEmits(['startOptimization', 'cancelOptimization']);

const solverConfig = reactive({
  solver: 'cp-sat',
  timeout: 120,
  maxInstanzen: 2,
  numThreads: 4,
  auffuellen: true,
  maxWvsProTn: 0,
});

const formatDate = (d) => d ? new Date(d).toLocaleDateString('de-DE') : '';

const zeitraumAnzeige = computed(() => {
  if (!props.veranstaltung) return '';
  const start = props.veranstaltung.beginntAm;
  const end = props.veranstaltung.endetAm;
  if (!start) return '';

  const startDate = new Date(start).toDateString();
  const endDate = end ? new Date(end).toDateString() : startDate;

  if (startDate === endDate) {
    return formatDate(start);
  }
  return `${formatDate(start)} - ${formatDate(end)}`;
});
</script>

<style scoped>
.animate-fade-in { animation: fadeIn 0.3s ease-in-out; }
@keyframes fadeIn { from { opacity: 0; transform: translateY(5px); } to { opacity: 1; transform: translateY(0); } }
</style>
