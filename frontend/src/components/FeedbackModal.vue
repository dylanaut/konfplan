<template>
  <div v-if="isVisible" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4">
    <div class="w-full max-w-lg rounded-xl bg-white p-6 shadow-2xl animate-fade-in">
      <div class="flex justify-between items-center mb-4">
        <h2 class="text-xl font-bold text-gray-900">Verbesserungsvorschlag einreichen</h2>
        <button @click="close" class="text-gray-400 hover:text-gray-600">
          <XIcon class="w-6 h-6" />
        </button>
      </div>

      <div v-if="submitted" class="space-y-4">
        <p class="text-sm text-gray-700">Vielen Dank! Ihr Vorschlag wurde übermittelt.</p>
        <div class="flex justify-end">
          <button class="btn-primary" @click="close">Schließen</button>
        </div>
      </div>

      <form v-else class="space-y-4" @submit.prevent="submit">
        <div>
          <label class="block text-xs font-bold text-gray-500 uppercase tracking-wider mb-1">Titel</label>
          <input v-model="titel" type="text" class="input-field w-full" required maxlength="255" />
        </div>

        <div>
          <label class="block text-xs font-bold text-gray-500 uppercase tracking-wider mb-1">Beschreibung</label>
          <textarea v-model="beschreibung" rows="5" class="input-field w-full" required></textarea>
        </div>

        <div>
          <label class="block text-xs font-bold text-gray-500 uppercase tracking-wider mb-1">Dringlichkeit</label>
          <select v-model="dringlichkeit" class="input-field w-full" required>
            <option value="NIEDRIG">Niedrig</option>
            <option value="MITTEL">Mittel</option>
            <option value="HOCH">Hoch</option>
            <option value="KRITISCH">Kritisch</option>
          </select>
        </div>

        <p v-if="error" class="text-xs text-red-600">{{ error }}</p>

        <div class="flex justify-end gap-3 pt-2">
          <button type="button" class="btn-secondary" @click="close">Abbrechen</button>
          <button type="submit" :disabled="isSubmitting" class="btn-primary flex items-center gap-2">
            <LoaderIcon v-if="isSubmitting" class="w-4 h-4 animate-spin" />
            Absenden
          </button>
        </div>
      </form>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue';
import { X as XIcon, Loader as LoaderIcon } from '@lucide/vue';
import api from '../api/axios';

const props = defineProps({
  isVisible: Boolean
});

const emit = defineEmits(['close']);

const titel = ref('');
const beschreibung = ref('');
const dringlichkeit = ref('MITTEL');
const error = ref('');
const isSubmitting = ref(false);
const submitted = ref(false);

watch(() => props.isVisible, (visible) => {
  if (visible) {
    titel.value = '';
    beschreibung.value = '';
    dringlichkeit.value = 'MITTEL';
    error.value = '';
    submitted.value = false;
  }
});

const close = () => emit('close');

const submit = async () => {
  isSubmitting.value = true;
  error.value = '';
  try {
    await api.post('/api/verbesserungsvorschlaege', { titel: titel.value, beschreibung: beschreibung.value, dringlichkeit: dringlichkeit.value });
    submitted.value = true;
  } catch (e) {
    console.error('Fehler beim Einreichen des Verbesserungsvorschlags:', e);
    error.value = 'Der Vorschlag konnte nicht übermittelt werden. Bitte versuchen Sie es erneut.';
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
.animate-fade-in { animation: fadeIn 0.2s ease-out; }
@keyframes fadeIn { from { opacity: 0; transform: scale(0.95); } to { opacity: 1; transform: scale(1); } }
</style>
