<template>
  <div class="max-w-6xl mx-auto space-y-6">
    <!-- Header & Info -->
    <header class="bg-white p-6 rounded-xl shadow-sm border border-gray-100">
      <h2 class="text-2xl font-bold text-gray-800">Verfügbare Vorträge</h2>
      <p class="text-gray-600 mt-1">
        Wählen Sie Ihre Top 10 Vorträge aus. 1 = Höchste Priorität, 10 = Niedrigste.
      </p>
      <p class="text-red-600 text-sm font-bold mt-2 flex items-center gap-1">
        <AlertCircleIcon class="w-4 h-4" />
        Hinweis: Rot markierte Vorträge sind Pflichtveranstaltungen für alle Teilnehmer.
      </p>

      <!-- Status-Leiste: Vergebene Prioritäten -->
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

        <!-- PFLICHT Badge -->
        <div v-if="vortrag.istPflicht" class="absolute top-0 right-0 bg-red-600 text-white text-[10px] font-black px-3 py-1 rounded-bl-lg uppercase tracking-widest shadow-sm z-10">
          Pflicht
        </div>

        <div class="p-5 flex-1">
          <div class="flex justify-between items-start mb-2">
            <span class="text-xs font-semibold uppercase tracking-wider text-indigo-500">{{ vortrag.targetAudience }}</span>
            <span v-if="getCurrentPriority(vortrag.id)" class="bg-indigo-100 text-indigo-700 px-2 py-1 rounded text-xs font-bold">
              Prio {{ getCurrentPriority(vortrag.id) }}
            </span>
          </div>

          <h3 class="text-lg font-bold text-gray-900 leading-tight mb-2 pr-12">{{ vortrag.title }}</h3>
          <p class="text-sm text-gray-500 mb-4 flex items-center">
            <UserIcon class="w-4 h-4 mr-1" /> {{ vortrag.referent?.firstName }} {{ vortrag.referent?.lastName }}
          </p>

          <p class="text-gray-600 text-sm line-clamp-3 mb-4">
            {{ vortrag.abstractText }}
          </p>
        </div>

        <!-- Footer: Priorität wählen -->
        <div class="bg-gray-50 p-4 border-t border-gray-100">
          <div v-if="vortrag.istPflicht" class="text-xs text-red-600 font-medium italic text-center py-2">
            Pflichtveranstaltung: Keine Wahl erforderlich
          </div>
          <template v-else>
            <label class="block text-xs font-medium text-gray-500 mb-2">Priorität zuweisen:</label>
            <select
                :value="getCurrentPriority(vortrag.id) || ''"
                @change="updatePriority(vortrag.id, $event.target.value)"
                class="w-full bg-white border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-indigo-500 outline-none"
            >
              <option value="">Keine Wahl</option>
              <option v-for="n in 10" :key="n" :value="n" :disabled="isRankTaken(n) && getCurrentPriority(vortrag.id) !== n">
                Rang {{ n }} {{ isRankTaken(n) && getCurrentPriority(vortrag.id) !== n ? '(Belegt)' : '' }}
              </option>
            </select>
          </template>
        </div>
      </div>
    </div>

    <!-- Floating Save Button -->
    <div class="fixed bottom-6 right-6 lg:static lg:mt-8 lg:flex lg:justify-end">
      <button
          @click="saveAllPriorities"
          class="bg-green-600 hover:bg-green-700 text-white px-8 py-3 rounded-full shadow-lg font-bold flex items-center gap-2 transition-transform active:scale-95"
      >
        <SaveIcon class="w-5 h-5" />
        Auswahl speichern
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import api from '../api/axios';
import { User as UserIcon, Save as SaveIcon, AlertCircle as AlertCircleIcon } from 'lucide-vue-next';

const vortraege = ref([]);
const myPriorities = ref([]);

onMounted(async () => {
  try {
    const [vortragRes, prioRes] = await Promise.all([
      api.get('/api/admin/vortraege'),
      api.get('/api/participant/priorities')
    ]);
    vortraege.value = vortragRes.data;
    myPriorities.value = prioRes.data.map(p => ({ vortragId: p.vortrag.id, priorityValue: p.priorityValue }));
  } catch (err) {
    console.error("Fehler beim Laden:", err);
  }
});

const getCurrentPriority = (vortragId) => {
  return myPriorities.value.find(p => p.vortragId === vortragId)?.priorityValue;
};

const isRankTaken = (rank) => {
  return myPriorities.value.some(p => p.priorityValue == rank);
};

const updatePriority = (vortragId, value) => {
  myPriorities.value = myPriorities.value.filter(p => p.vortragId !== vortragId);
  if (value !== "") {
    myPriorities.value = myPriorities.value.filter(p => p.priorityValue != value);
    myPriorities.value.push({ vortragId, priorityValue: parseInt(value) });
  }
};

const saveAllPriorities = async () => {
  try {
    const payload = myPriorities.value.map(p => ({ vortragId: p.vortragId, priorityValue: p.priorityValue }));
    await api.post('/api/participant/priorities', payload);
    alert("Erfolgreich gespeichert!");
  } catch (e) {
    alert("Fehler beim Speichern: " + (e.response?.data || e.message));
  }
};
</script>

<style scoped>
.line-clamp-3 {
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
