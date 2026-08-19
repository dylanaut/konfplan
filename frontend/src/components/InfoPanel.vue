<template>
  <div class="w-72 bg-white text-gray-800 rounded-lg shadow-2xl border border-gray-200 p-4 z-50">
    <div class="flex items-center justify-between mb-3">
      <h3 class="font-bold text-sm">{{ info?.name || 'KonfPlan' }}</h3>
      <button @click="close" class="text-gray-400 hover:text-gray-600">✕</button>
    </div>

    <div v-if="loading" class="text-xs text-gray-500">Lade Informationen...</div>
    <div v-else-if="error" class="text-xs text-red-600">Fehler beim Laden der Anwendungsinformationen.</div>
    <dl v-else class="space-y-1 text-xs">
      <div class="flex justify-between gap-2">
        <dt class="text-gray-500">Version</dt>
        <dd class="font-mono">{{ info?.version || '—' }}</dd>
      </div>
      <div class="flex justify-between gap-2">
        <dt class="text-gray-500">Build-Datum</dt>
        <dd class="font-mono">{{ formattedBuildTime }}</dd>
      </div>
      <div class="flex justify-between gap-2">
        <dt class="text-gray-500">Git-Commit</dt>
        <dd class="font-mono">{{ shortCommit }}</dd>
      </div>
    </dl>

    <a href="mailto:juergenkrey@yahoo.de" class="block mt-3 text-xs text-indigo-600 hover:underline">
      juergenkrey@yahoo.de
    </a>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import api from '../api/axios';

const emit = defineEmits(['close']);

const info = ref(null);
const loading = ref(true);
const error = ref(false);

const close = () => emit('close');

const formattedBuildTime = computed(() => {
  if (!info.value?.buildTime) return '—';
  return new Date(info.value.buildTime).toLocaleString('de-DE');
});

const shortCommit = computed(() => {
  const commit = info.value?.gitCommit;
  return commit ? commit.substring(0, 7) : '—';
});

onMounted(async () => {
  try {
    const res = await api.get('/api/info');
    info.value = res.data;
  } catch (e) {
    console.error('Fehler beim Laden der Anwendungsinformationen:', e);
    error.value = true;
  } finally {
    loading.value = false;
  }
});
</script>
