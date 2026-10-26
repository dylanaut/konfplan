<template>
  <div v-if="meldungstext" class="px-4 py-2 text-sm font-bold text-center shadow-md no-print"
       :class="sperre ? 'bg-red-600 text-white' : 'bg-amber-500 text-amber-950'" data-testid="lizenz-banner">
    {{ meldungstext }}
    <router-link :to="{ path: '/organisator', query: { lizenz: '1' } }" class="underline ml-2">Lizenz verwalten</router-link>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue';
import api from '../api/axios';

const POLL_INTERVAL_MS = 10 * 60 * 1000;
const WARNUNG_TAGE = 30;
const TAG_MS = 24 * 60 * 60 * 1000;

const status = ref(null);
let pollTimer = null;

const fetchStatus = async () => {
  try {
    status.value = (await api.get('/api/lizenz')).data;
  } catch (e) {
    console.error('Fehler beim Abrufen des Lizenzstatus:', e);
  }
};

const sperre = computed(() => !!status.value?.durchsetzung && status.value.zustand !== 'LIZENZIERT');

const meldungstext = computed(() => {
  const s = status.value;
  if (!s) return '';
  if (sperre.value) {
    return s.zustand === 'UNLIZENZIERT'
      ? 'Für diese Instanz ist keine Lizenz installiert - Planerstellung, Importe und das Anlegen neuer Veranstaltungen und Nutzer sind gesperrt.'
      : 'Die Lizenz dieser Instanz ist nicht gültig - Planerstellung, Importe und das Anlegen neuer Veranstaltungen und Nutzer sind gesperrt.';
  }
  if (s.zustand === 'LIZENZIERT' && s.gueltigBis) {
    const tage = Math.ceil((new Date(s.gueltigBis) - Date.now()) / TAG_MS);
    if (tage <= WARNUNG_TAGE) {
      return `Die Lizenz dieser Instanz läuft in ${Math.max(tage, 0)} Tagen ab.`;
    }
  }
  return '';
});

onMounted(() => {
  fetchStatus();
  pollTimer = setInterval(fetchStatus, POLL_INTERVAL_MS);
});

onUnmounted(() => clearInterval(pollTimer));
</script>
