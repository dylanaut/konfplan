<template>
  <div class="max-w-7xl mx-auto space-y-6 pb-20">

    <!-- Page Header & Veranstaltungsauswahl -->
    <div class="flex flex-col md:flex-row md:items-center justify-between gap-4 bg-white p-6 rounded-xl shadow-sm border border-gray-100">
      <div class="flex-1">
        <h1 class="text-2xl font-bold text-gray-900">Admin-Bereich</h1>
        <div class="mt-4 flex items-center gap-3">
          <label class="text-sm font-bold text-gray-500 uppercase tracking-wider">Aktive Veranstaltung:</label>
          <select v-model="selectedVid" @change="loadData" class="input-field max-w-md border-indigo-200 focus:ring-indigo-500">
            <option :value="null">Bitte wählen...</option>
            <option v-for="v in veranstaltungen" :key="v.id" :value="v.id">
              {{ v.name }} ({{ formatDate(v.beginntAm) }})
            </option>
          </select>
        </div>
      </div>
      <button v-if="selectedVid" @click="downloadExport" class="flex items-center justify-center gap-2 bg-gray-800 text-white px-6 py-2 rounded-lg hover:bg-gray-700 transition shadow-md">
        <DownloadIcon class="w-5 h-5"/> Prioritäten Export (CSV)
      </button>
    </div>

    <!-- Tab-Navigation -->
    <div v-if="selectedVid" class="border-b border-gray-200">
      <nav class="-mb-px flex space-x-8 overflow-x-auto">
        <button v-for="tab in ['benutzer', 'vorträge', 'slots', 'räume', 'planung', 'stats']" :key="tab"
                @click="activeTab = tab"
                :class="[activeTab === tab ? 'border-indigo-500 text-indigo-600' : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300', 'whitespace-nowrap py-4 px-1 border-b-2 font-medium text-sm capitalize']">
          {{ tab === 'planung' ? 'Vortragsplanung' : tab }}
        </button>
      </nav>
    </div>

    <!-- Platzhalter -->
    <div v-if="!selectedVid" class="bg-indigo-50 p-10 rounded-2xl text-center border-2 border-dashed border-indigo-200">
      <div class="text-indigo-400 mb-4 flex justify-center"><CalendarIcon class="w-12 h-12"/></div>
      <h2 class="text-xl font-bold text-indigo-900">Keine Veranstaltung ausgewählt</h2>
      <p class="text-indigo-600 mt-2">Bitte wählen Sie oben eine Veranstaltung aus, um die Details zu verwalten.</p>
    </div>

    <!-- TABS -->
    <template v-if="selectedVid">

      <!-- ... (Andere Tabs wie bisher) ... -->

      <!-- TAB: VORTRAGSPLANUNG -->
      <section v-if="activeTab === 'planung'" class="space-y-6 animate-fade-in">
        <div class="grid grid-cols-1 md:grid-cols-3 gap-6">

          <!-- Konfiguration & Aktion -->
          <div class="md:col-span-3 bg-indigo-900 text-white p-8 rounded-2xl shadow-xl flex flex-col md:flex-row items-start md:items-center justify-between gap-8">
            <div class="space-y-4 flex-1">
              <div>
                <h2 class="text-3xl font-black">Planung & Optimierung</h2>
                <p class="text-indigo-200">Konfigurieren Sie den Solver und starten Sie die Berechnung.</p>
              </div>

              <!-- Solver Konfiguration -->
              <div class="grid grid-cols-1 sm:grid-cols-2 gap-4 max-w-xl bg-white/10 p-4 rounded-xl border border-white/10">
                <div>
                  <label class="block text-[10px] uppercase font-bold text-indigo-300 mb-1">MiniZinc Solver</label>
                  <select v-model="solverConfig.solver" class="w-full bg-indigo-800 border-none rounded text-sm text-white focus:ring-2 focus:ring-green-400">
                    <option value="OR-tools">Google OR-Tools</option>
                    <option value="Gecode">Gecode</option>
                    <option value="COIN-BC">COIN-BC</option>
                    <option value="Chuffed">Chuffed</option>
                  </select>
                </div>
                <div>
                  <label class="block text-[10px] uppercase font-bold text-indigo-300 mb-1">Timeout (Sekunden)</label>
                  <input v-model.number="solverConfig.timeout" type="number" min="10" max="600" class="w-full bg-indigo-800 border-none rounded text-sm text-white focus:ring-2 focus:ring-green-400" />
                </div>
              </div>

              <div class="flex gap-4">
                <div class="bg-white/10 px-4 py-2 rounded-lg border border-white/20">
                  <div class="text-xs uppercase font-bold text-indigo-300">Teilnehmer</div>
                  <div class="text-2xl font-black">{{ participantsCount }}</div>
                </div>
                <!-- ... -->
              </div>
            </div>

            <button @click="startOptimization" :disabled="isOptimizing"
                    class="bg-green-500 hover:bg-green-400 disabled:bg-gray-600 text-white px-10 py-5 rounded-2xl font-black text-xl shadow-2xl transition-all transform hover:scale-105 flex items-center gap-3">
              <span v-if="isOptimizing" class="animate-spin"><LoaderIcon /></span>
              <ZapIcon v-else />
              {{ isOptimizing ? 'Optimierung läuft...' : 'Jetzt Optimieren' }}
            </button>
          </div>

          <!-- ... (Listen für Referenten, Vorträge, Slots wie bisher) ... -->
        </div>
      </section>

    </template>

    <!-- Modals & Global File Input ... -->
  </div>
</template>

<script setup>
import { computed, onMounted, ref, reactive } from 'vue';
import api from '../api/axios';
import {
  Download as DownloadIcon, Trash2 as Trash2Icon, Upload as UploadIcon, Calendar as CalendarIcon,
  Zap as ZapIcon, Loader as LoaderIcon, Users as UsersIcon, Clock as ClockIcon, FileText as FileTextIcon
} from 'lucide-vue-next';

// ... (Andere State Variablen)

const solverConfig = reactive({
  solver: 'OR-tools',
  timeout: 120
});

const isOptimizing = ref(false);

const startOptimization = async () => {
  if (!confirm(`Die Optimierung mit ${solverConfig.solver} wird gestartet. Fortfahren?`)) return;

  isOptimizing.value = true;
  try {
    const res = await api.post(`/api/veranstaltungen/${selectedVid.value}/optimierung/start`, solverConfig);
    alert("Optimierung erfolgreich abgeschlossen!");
    loadData();
  } catch (err) {
    alert("Fehler bei der Optimierung: " + (err.response?.data || err.message));
  } finally {
    isOptimizing.value = false;
  }
};

// ... (Restliche Methoden)
</script>
