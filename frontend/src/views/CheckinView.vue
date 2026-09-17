<template>
  <div class="max-w-md mx-auto mt-16 px-4">
    <div class="bg-white p-8 rounded-xl shadow-sm border border-gray-100 text-center">
      <img src="/logo/konfplan-light.svg" alt="Konfplan Logo" class="h-12 mx-auto mb-6" />

      <div v-if="loading" class="text-gray-500">
        <Loader2Icon class="w-10 h-10 mx-auto mb-4 animate-spin text-indigo-500" />
        <p>Anwesenheit wird registriert…</p>
      </div>

      <div v-else-if="error" class="text-gray-700">
        <XCircleIcon class="w-10 h-10 mx-auto mb-4 text-red-500" />
        <p class="font-medium">Check-in nicht möglich</p>
        <p class="text-sm text-gray-500 mt-2">{{ error }}</p>
      </div>

      <div v-else-if="result && result.aktiverTermin" class="text-gray-700">
        <CheckCircleIcon class="w-10 h-10 mx-auto mb-4 text-green-500" />
        <p class="font-medium">Anwesenheit registriert</p>
        <p class="text-sm text-gray-600 mt-2">{{ result.vortragTitel }}</p>
        <p class="text-xs text-gray-500 mt-1">{{ result.raumName }} · {{ result.slotZeit }}</p>
      </div>

      <div v-else-if="result" class="text-gray-700">
        <InfoIcon class="w-10 h-10 mx-auto mb-4 text-amber-500" />
        <p class="font-medium">Aktuell kein Termin</p>
        <p class="text-sm text-gray-500 mt-2">In {{ result.raumName }} findet gerade keine Veranstaltung statt.</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import api from '../api/axios';
import { extractErrorMessage } from '../utils/errorMessage';
import {
  Loader2 as Loader2Icon,
  CheckCircle as CheckCircleIcon,
  XCircle as XCircleIcon,
  Info as InfoIcon,
} from '@lucide/vue';

const route = useRoute();
const loading = ref(true);
const error = ref(null);
const result = ref(null);

onMounted(async () => {
  const { vid, raumId } = route.params;
  try {
    const response = await api.post(`/api/teilnehmer/veranstaltungen/${vid}/anwesenheit`, null, {
      params: { raumId },
    });
    result.value = response.data;
  } catch (e) {
    error.value = extractErrorMessage(e, 'Anwesenheit konnte nicht registriert werden.');
  } finally {
    loading.value = false;
  }
});
</script>
