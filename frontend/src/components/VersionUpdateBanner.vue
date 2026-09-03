<template>
  <div v-if="updateAvailable" class="bg-indigo-600 text-white px-4 py-2 text-sm font-bold text-center shadow-md no-print flex items-center justify-center gap-3">
    <span>Es ist eine neue Version von KonfPlan verfügbar. Bitte alle Änderungen speichern - für die Nutzung ist danach ggf. eine erneute Anmeldung erforderlich.</span>
    <button @click="reload" class="bg-white text-indigo-600 rounded px-3 py-1 text-xs font-bold hover:bg-indigo-50 shrink-0">
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
      // SNAPSHOT-Versionen wechseln bei jedem Dev-/Staging-Rebuild ihren gitCommit, ohne dass
      // sich die Versionsnummer aendert - der Hinweis waere dort bei jedem Redeploy staendig zu
      // sehen. Echte Produktions-Deployments pinnen IMAGE_TAG immer auf einen echten Release
      // (nie ein SNAPSHOT), betrifft also nur Dev/Staging.
      if (res.data?.version?.endsWith('-SNAPSHOT')) {
        clearInterval(pollTimer);
      }
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

const reload = () => {
  if (window.confirm('Bitte stellen Sie sicher, dass Sie alle Änderungen gespeichert haben - '
    + 'für die Nutzung der neuen Version ist möglicherweise eine erneute Anmeldung erforderlich. '
    + 'Jetzt neu laden?')) {
    window.location.reload();
  }
};

onMounted(() => {
  fetchInfo();
  pollTimer = setInterval(fetchInfo, POLL_INTERVAL_MS);
});

onUnmounted(() => {
  clearInterval(pollTimer);
});
</script>
