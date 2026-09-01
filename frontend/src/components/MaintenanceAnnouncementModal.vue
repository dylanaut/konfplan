<template>
  <div v-if="isVisible" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4">
    <div class="w-full max-w-md rounded-xl bg-white p-6 shadow-2xl animate-fade-in">
      <div class="flex justify-between items-center mb-4">
        <h2 class="text-xl font-bold text-gray-900">Wartung ankündigen</h2>
        <button @click="$emit('close')" class="text-gray-400 hover:text-gray-600">
          <XIcon class="w-6 h-6" />
        </button>
      </div>

      <div class="bg-indigo-50 p-3 rounded-lg border border-indigo-100 mb-4">
        <p class="text-[10px] text-indigo-700 leading-relaxed">
          <InfoIcon class="w-3 h-3 inline mr-1" />
          Allen eingeloggten Nutzern wird ab sofort ein Hinweisbanner angezeigt (Aktualisierung
          spätestens nach 60 Sekunden), bis die Anwendung wieder verfügbar ist.
        </p>
      </div>

      <div class="space-y-4">
        <div>
          <label class="block text-xs font-bold text-gray-500 uppercase tracking-wider mb-1">Wartung beginnt</label>
          <input v-model="start" type="datetime-local" class="input-field w-full" />
        </div>
        <div>
          <label class="block text-xs font-bold text-gray-500 uppercase tracking-wider mb-1">Anwendung wieder verfügbar ab</label>
          <input v-model="ende" type="datetime-local" class="input-field w-full" />
        </div>
        <p v-if="error" class="text-xs text-red-600">{{ error }}</p>
      </div>

      <div class="mt-8 flex gap-3">
        <button v-if="hatAktiveAnkuendigung" @click="loeschen" :disabled="isSubmitting" class="btn-danger">Löschen</button>
        <button @click="$emit('close')" class="btn-secondary flex-1">Abbrechen</button>
        <button @click="speichern" :disabled="!start || !ende || isSubmitting" class="btn-primary flex-1 flex items-center justify-center gap-2">
          <LoaderIcon v-if="isSubmitting" class="w-4 h-4 animate-spin" />
          Speichern
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue';
import { X as XIcon, Info as InfoIcon, Loader as LoaderIcon } from '@lucide/vue';
import api from '../api/axios';
import { extractErrorMessage } from '../utils/errorMessage';

const props = defineProps({
  isVisible: Boolean
});

const emit = defineEmits(['close', 'saved', 'deleted']);

const start = ref('');
const ende = ref('');
const error = ref('');
const isSubmitting = ref(false);
const hatAktiveAnkuendigung = ref(false);

const toDatetimeLocal = (isoString) => isoString ? isoString.substring(0, 16) : '';

watch(() => props.isVisible, async (visible) => {
  if (!visible) return;
  error.value = '';
  try {
    const res = await api.get('/api/wartungshinweis');
    hatAktiveAnkuendigung.value = !!(res.data?.startZeitpunkt && res.data?.endeZeitpunkt);
    start.value = toDatetimeLocal(res.data?.startZeitpunkt);
    ende.value = toDatetimeLocal(res.data?.endeZeitpunkt);
  } catch (e) {
    console.error('Fehler beim Laden des Wartungshinweises:', e);
  }
});

const speichern = async () => {
  isSubmitting.value = true;
  error.value = '';
  try {
    await api.put('/api/wartungshinweis', { startZeitpunkt: start.value, endeZeitpunkt: ende.value });
    emit('saved');
    emit('close');
  } catch (e) {
    error.value = extractErrorMessage(e);
  } finally {
    isSubmitting.value = false;
  }
};

const loeschen = async () => {
  isSubmitting.value = true;
  error.value = '';
  try {
    await api.delete('/api/wartungshinweis');
    emit('deleted');
    emit('close');
  } catch (e) {
    error.value = extractErrorMessage(e);
  } finally {
    isSubmitting.value = false;
  }
};
</script>

<style scoped>
@reference "tailwindcss";

.input-field {
  @apply rounded-lg border border-gray-300 px-3 py-2 text-sm text-gray-900 focus:ring-2 focus:ring-indigo-500 bg-white;
}
.btn-primary {
  @apply rounded-lg bg-indigo-600 px-4 py-2 text-white font-bold hover:bg-indigo-700 transition shadow-sm disabled:opacity-50;
}
.btn-secondary {
  @apply bg-white text-gray-700 px-4 py-2 rounded-lg hover:bg-gray-50 font-bold border border-gray-200 transition shadow-sm;
}
.btn-danger {
  @apply bg-white text-red-600 px-4 py-2 rounded-lg hover:bg-red-50 font-bold border border-red-200 transition shadow-sm disabled:opacity-50;
}
.animate-fade-in { animation: fadeIn 0.2s ease-out; }
@keyframes fadeIn { from { opacity: 0; transform: scale(0.95); } to { opacity: 1; transform: scale(1); } }
</style>
