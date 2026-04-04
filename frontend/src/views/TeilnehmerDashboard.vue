<template>
  <div class="max-w-6xl mx-auto space-y-6">
    <!-- Header (unverändert) -->

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
          <p class="text-sm text-gray-500 mb-4 flex items-center">
            <UserIcon class="w-4 h-4 mr-1" /> {{ vortrag.referent?.firstName }} {{ vortrag.referent?.lastName }}
          </p>

          <p class="text-gray-600 text-sm line-clamp-3 mb-4">
            {{ vortrag.inhalt }}
          </p>
        </div>

        <!-- Footer (unverändert) -->
      </div>
    </div>

    <!-- Save Button (unverändert) -->
  </div>
</template>

<script setup>
/* ... (Imports und Logik wie bisher, nutzt bereits vortragId und prioWert) ... */
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
    myPriorities.value = prioRes.data.map(p => ({ vortragId: p.vortrag.id, prioWert: p.prioWert }));
  } catch (err) { console.error(err); }
});

const getCurrentPriority = (vortragId) => myPriorities.value.find(p => p.vortragId === vortragId)?.prioWert;
const isRankTaken = (rank) => myPriorities.value.some(p => p.prioWert == rank);

const updatePriority = (vortragId, value) => {
  myPriorities.value = myPriorities.value.filter(p => p.vortragId !== vortragId);
  if (value !== "") {
    myPriorities.value = myPriorities.value.filter(p => p.prioWert != value);
    myPriorities.value.push({ vortragId, prioWert: parseInt(value) });
  }
};

const saveAllPriorities = async () => {
  try {
    const payload = myPriorities.value.map(p => ({ vortragId: p.vortragId, prioWert: p.prioWert }));
    await api.post('/api/participant/priorities', payload);
    alert("Erfolgreich gespeichert!");
  } catch (e) { alert("Fehler!"); }
};
</script>
