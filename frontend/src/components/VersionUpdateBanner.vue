<template>
  <div v-if="updateAvailable" class="bg-indigo-600 text-white px-4 py-2 text-sm font-bold text-center shadow-md no-print flex items-center justify-center gap-3">
    <span>Es ist eine neue Version von KonfPlan verfügbar.</span>
    <button @click="reload" class="bg-white text-indigo-600 rounded px-3 py-1 text-xs font-bold hover:bg-indigo-50">
      Jetzt neu laden
    </button>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue';
import api from '../api/axios';

const POLL_INTERVAL_MS = 60000;

const baseline = ref(null);
const updateAvailable = ref(false);
let pollTimer = null;

const kennung = (info) => `${info?.version}@${info?.gitCommit}`;

const fetchInfo = async () => {
  try {
    const res = await api.get('/api/info');
    if (null === baseline.value) {
      baseline.value = kennung(res.data);
      return;
    }
    if (kennung(res.data) !== baseline.value) {
      updateAvailable.value = true;
      clearInterval(pollTimer);
    }
  } catch (e) {
    console.error('Fehler beim Abrufen der Versionsinformation:', e);
  }
};

const reload = () => window.location.reload();

onMounted(() => {
  fetchInfo();
  pollTimer = setInterval(fetchInfo, POLL_INTERVAL_MS);
});

onUnmounted(() => {
  clearInterval(pollTimer);
});
</script>
