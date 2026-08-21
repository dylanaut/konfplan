<template>
  <div v-if="newsText" class="laufband" role="status">
    <div class="laufband-track">
      <span class="laufband-text">{{ newsText }}</span>
      <span class="laufband-text" aria-hidden="true">{{ newsText }}</span>
    </div>
  </div>
  <div v-else class="laufband laufband-leer" aria-hidden="true"></div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue';
import api from '../api/axios';

const POLL_INTERVAL_MS = 5 * 60 * 1000;
const TRENNER = '   •   ';

const news = ref([]);
let pollingInterval = null;

const newsText = computed(() => news.value.join(TRENNER));

async function ladeNews() {
  try {
    const response = await api.get('/api/laufband');
    news.value = response.data?.news ?? [];
  } catch (error) {
    console.error('Laufband konnte nicht geladen werden:', error);
    news.value = [];
  }
}

onMounted(() => {
  ladeNews();
  pollingInterval = setInterval(ladeNews, POLL_INTERVAL_MS);
});

onUnmounted(() => {
  if (pollingInterval) {
    clearInterval(pollingInterval);
  }
});
</script>

<style scoped>
.laufband {
  width: 100%;
  max-width: 28rem;
  height: 2.25rem;
  margin: 0 auto;
  overflow: hidden;
  white-space: nowrap;
  border-radius: 0.375rem;
  background-color: #eef2ff;
}

.laufband-leer {
  background-color: #f3f4f6;
}

.laufband-track {
  display: inline-flex;
  height: 100%;
  align-items: center;
  animation: laufband-scroll 20s linear infinite;
}

.laufband-text {
  padding-right: 3rem;
  color: #4338ca;
  font-weight: 500;
  font-size: 0.875rem;
}

@keyframes laufband-scroll {
  from {
    transform: translateX(0);
  }
  to {
    transform: translateX(-50%);
  }
}
</style>
