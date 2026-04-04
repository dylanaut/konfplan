<template>
  <div class="max-w-6xl mx-auto space-y-6">
    <!-- Header & Info -->
    <header class="bg-white p-6 rounded-xl shadow-sm border border-gray-100">
      <h2 class="text-2xl font-bold text-gray-800">Verfügbare Vorträge</h2>
      <p class="text-gray-600 mt-1">
        Wählen Sie Ihre Top 10 Vorträge aus. 1 = Höchste Priorität, 10 = Niedrigste.
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

    <!-- Talk Grid -->
    <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
      <div v-for="talk in talks" :key="talk.id"
           class="bg-white rounded-xl shadow-sm border border-gray-100 flex flex-col overflow-hidden hover:shadow-md transition-shadow">

        <div class="p-5 flex-1">
          <div class="flex justify-between items-start mb-2">
            <span class="text-xs font-semibold uppercase tracking-wider text-indigo-500">{{ talk.targetAudience }}</span>
            <!-- Anzeige der aktuell gewählten Prio für diesen Talk -->
            <span v-if="getCurrentPriority(talk.id)" class="bg-indigo-100 text-indigo-700 px-2 py-1 rounded text-xs font-bold">
              Prio {{ getCurrentPriority(talk.id) }}
            </span>
          </div>

          <h3 class="text-lg font-bold text-gray-900 leading-tight mb-2">{{ talk.title }}</h3>
          <p class="text-sm text-gray-500 mb-4 flex items-center">
            <UserIcon class="w-4 h-4 mr-1" /> {{ talk.speaker.firstName }} {{ talk.speaker.lastName }}
          </p>

          <p class="text-gray-600 text-sm line-clamp-3 mb-4">
            {{ talk.abstractText }}
          </p>
        </div>

        <!-- Footer: Priorität wählen -->
        <div class="bg-gray-50 p-4 border-t border-gray-100">
          <label class="block text-xs font-medium text-gray-500 mb-2">Priorität zuweisen:</label>
          <select
              :value="getCurrentPriority(talk.id) || ''"
              @change="updatePriority(talk.id, $event.target.value)"
              class="w-full bg-white border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-indigo-500 outline-none"
          >
            <option value="">Keine Wahl</option>
            <option v-for="n in 10" :key="n" :value="n" :disabled="isRankTaken(n) && getCurrentPriority(talk.id) !== n">
              Rang {{ n }} {{ isRankTaken(n) && getCurrentPriority(talk.id) !== n ? '(Belegt)' : '' }}
            </option>
          </select>
        </div>
      </div>
    </div>

    <!-- Floating Save Button (Mobile optimized) -->
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
import { User as UserIcon, Save as SaveIcon } from 'lucide-vue-next';

const talks = ref([]);
const myPriorities = ref([]); // Format: [{ talkId: 1, prioWert: 1 }, ...]

// Daten laden
onMounted(async () => {
  const [talksRes, prioRes] = await Promise.all([
    api.get('/api/talks'), // Wir müssten noch einen öffentlichen Talk-Endpoint im Backend haben
    api.get('/api/participant/priorities')
  ]);
  talks.value = talksRes.data;
  myPriorities.value = prioRes.data.map(p => ({ talkId: p.talk.id, prioWert: p.prioWert }));
});

const getCurrentPriority = (talkId) => {
  return myPriorities.value.find(p => p.talkId === talkId)?.prioWert;
};

const isRankTaken = (rank) => {
  return myPriorities.value.some(p => p.prioWert == rank);
};

const updatePriority = (talkId, value) => {
  // Entferne alte Prio für diesen Talk
  myPriorities.value = myPriorities.value.filter(p => p.talkId !== talkId);

  if (value !== "") {
    // Falls der Rang von einem anderen Talk belegt war, dort entfernen (Swap-Logik optional)
    myPriorities.value = myPriorities.value.filter(p => p.prioWert != value);
    myPriorities.value.push({ talkId, prioWert: parseInt(value) });
  }
};

const saveAllPriorities = async () => {
  try {
    await api.post('/api/participant/priorities', myPriorities.value);
    alert("Erfolgreich gespeichert!");
  } catch (e) {
    alert("Fehler beim Speichern: " + e.response?.data || e.message);
  }
};
</script>

<style scoped>
/* Hilft bei langen Abstracts auf Mobile */
.line-clamp-3 {
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>