<template>
  <div v-if="isVisible" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4">
    <div class="w-full max-w-md rounded-xl bg-white p-6 shadow-2xl">
      <div class="flex items-center justify-between mb-6">
        <h2 class="text-xl font-bold text-gray-900">
          {{ kategorie?.id ? 'Gruppenkategorie bearbeiten' : 'Neue Gruppenkategorie anlegen' }}
        </h2>
        <button class="text-gray-500 hover:text-gray-700" @click="close">✕</button>
      </div>

      <div v-if="error" class="mb-4 p-3 bg-red-100 border border-red-200 text-red-700 text-sm rounded-lg animate-fade-in">
        {{ error }}
      </div>

      <form class="space-y-4" @submit.prevent="save">
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Name</label>
          <input v-model="form.name" type="text" class="input-field" required />
        </div>

        <div class="flex items-center gap-2">
          <input id="mehrwertig" v-model="form.mehrwertig" type="checkbox" class="h-5 w-5" />
          <label for="mehrwertig" class="text-sm font-medium">Mehrwertig (mehrere Werte pro Teilnehmer zulässig)</label>
        </div>

        <div class="flex items-center gap-2">
          <input id="pflicht" v-model="form.pflicht" type="checkbox" class="h-5 w-5" />
          <label for="pflicht" class="text-sm font-medium">Pflicht (jeder Teilnehmer muss einen Wert haben)</label>
        </div>

        <div class="flex justify-end gap-3 pt-4 border-t">
          <button type="button" class="btn-secondary" @click="close">Abbrechen</button>
          <button type="submit" class="btn-primary">Speichern</button>
        </div>
      </form>
    </div>
  </div>
</template>

<script setup>
import { reactive, watch } from 'vue';

const props = defineProps({
  isVisible: { type: Boolean, required: true },
  kategorie: { type: Object, default: null },
  error: { type: String, default: '' }
});

const emit = defineEmits(['close', 'save']);

const form = reactive({
  id: null,
  name: '',
  mehrwertig: false,
  pflicht: false,
});

watch(
    () => props.kategorie,
    (val) => {
      form.id = val?.id ?? null;
      form.name = val?.name ?? '';
      form.mehrwertig = val?.mehrwertig ?? false;
      form.pflicht = val?.pflicht ?? false;
    },
    { immediate: true }
);

const close = () => {
  emit('close');
};

const save = () => {
  emit('save', { ...form });
};
</script>

<style scoped>
@reference "tailwindcss";

.input-field { @apply w-full rounded-lg border border-gray-300 px-3 py-2 text-gray-900 focus:ring-2 focus:ring-indigo-500 bg-white text-sm; }
.btn-primary { @apply rounded-lg bg-indigo-600 px-4 py-2 text-white font-bold hover:bg-indigo-700 transition; }
.btn-secondary { @apply rounded-lg bg-gray-100 px-4 py-2 text-gray-700 font-medium hover:bg-gray-200 transition; }
.animate-fade-in { animation: fadeIn 0.3s ease-in-out; }
@keyframes fadeIn { from { opacity: 0; transform: translateY(5px); } to { opacity: 1; transform: translateY(0); } }
</style>
