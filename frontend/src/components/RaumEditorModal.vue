<template>
  <div v-if="isVisible" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4">
    <div class="w-full max-w-2xl rounded-xl bg-white p-6 shadow-2xl overflow-y-auto max-h-[90vh]">
      <div class="flex items-center justify-between mb-6">
        <h2 class="text-xl font-bold text-gray-900">
          {{ raum?.id ? 'Raum bearbeiten' : 'Neuen Raum anlegen' }}
        </h2>
        <button class="text-gray-500 hover:text-gray-700" @click="$emit('close')">✕</button>
      </div>

      <form class="grid grid-cols-1 md:grid-cols-2 gap-4" @submit.prevent="save">
        <div class="md:col-span-2">
          <label class="block text-sm font-medium text-gray-700 mb-1">Name des Raums</label>
          <input v-model="form.name" type="text" class="input-field" required />
        </div>

        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Zugehöriges Gebäude</label>
          <select v-model="form.gebaeude.id" class="input-field" required :disabled="!!raum?.id">
            <option v-for="g in gebaeude" :key="g.id" :value="g.id">{{ g.name }}</option>
          </select>
          <p v-if="raum?.id" class="text-[10px] text-gray-400 mt-1 italic">Gebäude kann bei bestehenden Räumen nicht geändert werden.</p>
        </div>

        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Kapazität (Personen)</label>
          <input v-model.number="form.kapazitaet" type="number" min="1" class="input-field" required />
        </div>

        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Etage (optional)</label>
          <input v-model="form.etage" type="text" class="input-field" />
        </div>

        <div class="md:col-span-2 flex justify-end gap-3 pt-4 border-t mt-4">
          <button type="button" class="btn-secondary" @click="$emit('close')">Abbrechen</button>
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
  raum: { type: Object, default: null },
  gebaeude: { type: Array, default: () => [] }
});

const emit = defineEmits(['close', 'save']);

const form = reactive({
  id: null,
  name: '',
  kapazitaet: 10,
  etage: '',
  gebaeude: { id: null }
});

watch(
    () => props.raum,
    (val) => {
      form.id = val?.id ?? null;
      form.name = val?.name ?? '';
      form.kapazitaet = val?.kapazitaet ?? 10;
      form.etage = val?.etage ?? '';
      form.gebaeude.id = val?.gebaeude?.id ?? (props.gebaeude[0]?.id || null);
    },
    { immediate: true }
);

const save = () => {
  // Wir übergeben das Gebäude-Objekt mit ID, damit das Backend den Pfad auflösen kann
  emit('save', { ...form });
};
</script>

<style scoped>
@reference "tailwindcss";

.input-field { @apply w-full rounded-lg border border-gray-300 px-3 py-2 text-gray-900 focus:outline-none focus:ring-2 focus:ring-indigo-500 bg-white; }
.btn-primary { @apply rounded-lg bg-indigo-600 px-4 py-2 text-white hover:bg-indigo-700; }
.btn-secondary { @apply rounded-lg bg-gray-100 px-4 py-2 text-gray-700 hover:bg-gray-200; }
</style>
