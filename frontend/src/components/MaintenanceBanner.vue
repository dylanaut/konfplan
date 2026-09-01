<template>
  <div v-if="meldungstext" class="bg-amber-500 text-amber-950 px-4 py-2 text-sm font-bold text-center shadow-md no-print">
    {{ meldungstext }}
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue';
import api from '../api/axios';

const POLL_INTERVAL_MS = 60000;
const TICK_INTERVAL_MS = 30000;

const hinweis = ref(null);
const now = ref(new Date());
let pollTimer = null;
let tickTimer = null;

const fetchHinweis = async () => {
  try {
    const res = await api.get('/api/wartungshinweis');
    hinweis.value = res.data?.startZeitpunkt && res.data?.endeZeitpunkt ? res.data : null;
  } catch (e) {
    console.error('Fehler beim Abrufen des Wartungshinweises:', e);
  }
};

const formatUhrzeit = (isoString) => new Date(isoString).toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' });
const minutenBis = (isoString) => Math.max(0, Math.ceil((new Date(isoString) - now.value) / 60000));

const meldungstext = computed(() => {
  if (!hinweis.value) return '';
  const start = new Date(hinweis.value.startZeitpunkt);
  const ende = new Date(hinweis.value.endeZeitpunkt);

  if (now.value < start) {
    return `Achtung: Wartungsarbeiten in ca. ${minutenBis(hinweis.value.startZeitpunkt)} Minuten (${formatUhrzeit(hinweis.value.startZeitpunkt)} Uhr) - die Anwendung ist danach voraussichtlich bis ca. ${formatUhrzeit(hinweis.value.endeZeitpunkt)} Uhr nicht erreichbar.`;
  }
  if (now.value < ende) {
    return `Die Anwendung befindet sich aktuell in Wartung und ist voraussichtlich in ca. ${minutenBis(hinweis.value.endeZeitpunkt)} Minuten (${formatUhrzeit(hinweis.value.endeZeitpunkt)} Uhr) wieder verfügbar.`;
  }
  return '';
});

onMounted(() => {
  fetchHinweis();
  pollTimer = setInterval(fetchHinweis, POLL_INTERVAL_MS);
  tickTimer = setInterval(() => { now.value = new Date(); }, TICK_INTERVAL_MS);
});

onUnmounted(() => {
  clearInterval(pollTimer);
  clearInterval(tickTimer);
});
</script>
