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
    <div class="bg-indigo-900 text-white p-6 rounded-2xl shadow-xl space-y-4">
      <div class="flex flex-col md:flex-row items-end justify-between gap-6">
        <div class="space-y-3 flex-1 w-full">
          <h2 class="text-2xl font-black flex items-center gap-2">
            Planerstellung
            <HelpTooltip label="Planerstellung" text="Startet die automatische Zuteilung von Teilnehmern zu Wahlvorträgen per MiniZinc-Solver. Prüfe vorher in der Übersicht oben, ob alle Voraussetzungen (Prioritäten, Räume, Zeit-Slots) erfüllt sind."/>
          </h2>
          <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-3 bg-white/10 p-3 rounded-xl border border-white/10">
            <div>
              <label class="flex items-center gap-1 text-[9px] uppercase font-bold text-indigo-300 mb-0.5">
                Timeout (Sek.)
                <HelpTooltip label="Timeout" text="Maximale Rechenzeit in Sekunden für die reine Solver-Berechnung. Nach Ablauf wird das bis dahin beste gefundene Ergebnis übernommen."/>
              </label>
              <input v-model.number="solverConfig.timeout" type="number" class="w-full bg-indigo-800 border-none rounded text-xs text-white focus:ring-2 focus:ring-green-400 py-1 px-2"/>
            </div>
            <div>
              <label class="flex items-center gap-1 text-[9px] uppercase font-bold text-indigo-300 mb-0.5">
                max. Wiederholungen
                <HelpTooltip label="max. Wiederholungen" text="Maximale Anzahl von Wiederholungen pro Vortrag."/>
              </label>
              <input v-model.number="solverConfig.maxInstanzen" type="number" class="w-full bg-indigo-800 border-none rounded text-xs text-white focus:ring-2 focus:ring-green-400 py-1 px-2"/>
            </div>
            <div>
              <label class="flex items-center gap-1 text-[9px] uppercase font-bold text-indigo-300 mb-0.5">
                Leistung (1-5)
                <HelpTooltip label="Leistung" text="Anzahl der für die Berechnung genutzten Rechenkerne (Threads). Höhere Werte können die Berechnung beschleunigen."/>
              </label>
              <input v-model.number="solverConfig.numThreads" type="number" class="w-full bg-indigo-800 border-none rounded text-xs text-white focus:ring-2 focus:ring-green-400 py-1 px-2"/>
            </div>
            <div>
              <label class="flex items-center gap-1 text-[9px] uppercase font-bold text-indigo-300 mb-0.5">
                max. WV pro TN (0=∞)
                <HelpTooltip label="max. WV pro TN" text="Maximale Anzahl Wahlvorträge pro Teilnehmer. 0 = kein Limit."/>
              </label>
              <input v-model.number="solverConfig.maxWvsProTn" type="number" min="0" class="w-full bg-indigo-800 border-none rounded text-xs text-white focus:ring-2 focus:ring-green-400 py-1 px-2"/>
            </div>
            <div class="flex items-center justify-center pt-3">
              <label class="flex items-center space-x-2 cursor-pointer text-xs">
                <input v-model="solverConfig.auffuellen" type="checkbox" class="bg-indigo-800 border-indigo-600 rounded text-green-500 focus:ring-green-400"/>
                <span class="flex items-center gap-1">Auffüllen? <HelpTooltip label="Auffüllen?" text="Freie Plätze sollen in Wahlvorträgen mit nicht verplanten Teilnehmern aufgefüllt werden."/></span>
              </label>
            </div>
          </div>
        </div>
        <div class="text-right">
          <p v-if="teilnehmerMitPrioritaetenCount === 0" class="text-red-300 text-center text-[10px] mt-1.5 font-bold animate-pulse">
            Keine Prioritäten vorhanden.
          </p>
          <button v-if="!isPlanning" @click="emit('startOptimization', solverConfig)" :disabled="teilnehmerMitPrioritaetenCount === 0" class="bg-green-500 hover:bg-green-400 disabled:bg-gray-600 text-white px-5 py-2.5 rounded-xl font-black text-sm shadow-2xl transition-all transform hover:scale-105 flex items-center gap-2">
            <ZapIcon class="w-4 h-4"/>
            Pläne erstellen
          </button>
          <button v-else-if="planningPhase === 'BERECHNUNG'" @click="emit('cancelOptimization')" class="bg-red-500 hover:bg-red-400 text-white px-5 py-2.5 rounded-xl font-black text-sm shadow-2xl transition-all transform hover:scale-105 flex items-center gap-2">
            <LoaderIcon class="animate-spin w-4 h-4"/>
            Erstellung abbrechen ({{ remainingSeconds }}s)
          </button>
          <button v-else disabled title="Der Timeout gilt nur für die reine MiniZinc-Berechnung; diese Phase lässt sich nicht abbrechen." class="bg-gray-500 text-white px-5 py-2.5 rounded-xl font-black text-sm shadow-2xl flex items-center gap-2 cursor-not-allowed opacity-80">
            <LoaderIcon class="animate-spin w-4 h-4"/>
            {{ planningPhase === 'PERSISTIERUNG' ? 'Ergebnis wird gespeichert...' : 'Vorbereitung läuft...' }}
          </button>
        </div>
      </div>

      <!-- Export/Import (extern berechneter Plan) -->
      <div v-if="!isPlanning" class="flex flex-wrap items-center gap-2 pt-3 border-t border-white/10">
        <button @click="emit('exportDzn', solverConfig)" title="MiniZinc-Datendatei (.dzn) herunterladen"
                class="bg-white/10 hover:bg-white/20 text-white px-3 py-2 rounded-lg font-bold text-xs shadow-lg transition-all flex items-center gap-2">
          <DownloadIcon class="w-4 h-4"/>
          .dzn exportieren
        </button>
        <button @click="emit('exportBundle', solverConfig)" title="Export-Paket (.dzn + Modell + Metadaten) für die Berechnung auf einem externen Rechner herunterladen"
                class="bg-white/10 hover:bg-white/20 text-white px-3 py-2 rounded-lg font-bold text-xs shadow-lg transition-all flex items-center gap-2">
          <DownloadIcon class="w-4 h-4"/>
          Bundle exportieren
        </button>
        <button @click="ergebnisFileInput?.click()" title="Extern berechnetes MiniZinc-Ergebnis importieren"
                class="bg-white/10 hover:bg-white/20 text-white px-3 py-2 rounded-lg font-bold text-xs shadow-lg transition-all flex items-center gap-2">
          <UploadIcon class="w-4 h-4"/>
          Ergebnis importieren
        </button>
        <input type="file" ref="ergebnisFileInput" class="hidden" accept=".zip" @change="onErgebnisFileSelected"/>
      </div>
    </div>
  </section>
</template>

<script setup>
import { reactive, computed, ref, watch, onUnmounted } from 'vue';
import { Loader as LoaderIcon, Zap as ZapIcon, XCircle as CancelIcon, Download as DownloadIcon, Upload as UploadIcon } from '@lucide/vue';
import HelpTooltip from '../../HelpTooltip.vue';

const props = defineProps({
  isPlanning: Boolean,
  planningPhase: String,
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

const emit = defineEmits(['startOptimization', 'cancelOptimization', 'exportDzn', 'exportBundle', 'importErgebnis']);

const ergebnisFileInput = ref(null);
const onErgebnisFileSelected = (event) => {
  const file = event.target.files[0];
  if (!file) return;
  emit('importErgebnis', file);
  event.target.value = ''; // erlaubt erneutes Auswählen derselben Datei
};

const solverConfig = reactive({
  timeout: 120,
  maxInstanzen: 2,
  numThreads: 4,
  auffuellen: true,
  maxWvsProTn: 0,
});

const remainingSeconds = ref(0);
let countdownInterval = null;

const stopCountdown = () => {
  if (countdownInterval) {
    clearInterval(countdownInterval);
    countdownInterval = null;
  }
};

// Der Timeout gilt nur für die reine MiniZinc-Berechnung (Phase BERECHNUNG), nicht für die
// DB-lastige Vorbereitung/Persistierung davor/danach - daher startet die Anzeige erst hier.
watch(() => props.planningPhase, (phase, wasPhase) => {
  if (phase === 'BERECHNUNG' && wasPhase !== 'BERECHNUNG') {
    stopCountdown();
    remainingSeconds.value = solverConfig.timeout;
    countdownInterval = setInterval(() => {
      if (remainingSeconds.value > 0) {
        remainingSeconds.value--;
      }
    }, 1000);
  } else if (phase !== 'BERECHNUNG') {
    stopCountdown();
  }
});

onUnmounted(stopCountdown);

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
