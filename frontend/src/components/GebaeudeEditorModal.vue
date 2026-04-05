<template>
  <div v-if="isVisible" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4">
    <div class="w-full max-w-2xl rounded-xl bg-white p-6 shadow-2xl overflow-y-auto max-h-[90vh]">
      <div class="flex items-center justify-between mb-6">
        <h2 class="text-xl font-bold text-gray-900">
          {{ gebaeude?.id ? 'Gebäude bearbeiten' : 'Neues Gebäude anlegen' }}
        </h2>
        <button class="text-gray-500 hover:text-gray-700" @click="$emit('close')">✕</button>
      </div>

      <form class="grid grid-cols-1 md:grid-cols-2 gap-4" @submit.prevent="save">
        <div class="md:col-span-2">
          <label class="block text-sm font-medium text-gray-700 mb-1">Name des Gebäudes</label>
          <input v-model="form.name" type="text" class="input-field" required />
        </div>

        <div class="md:col-span-2">
          <label class="block text-sm font-medium text-gray-700 mb-1">Gebäudetyp</label>
          <select v-model="form.typ" class="input-field" required>
            <option value="HAUPTGEBÄUDE">Hauptgebäude</option>
            <option value="NEBENGEBÄUDE">Nebengebäude</option>
            <option value="FACHTRAKT">Fachtrakt</option>
            <option value="SPORTHALLE">Sporthalle</option>
            <option value="MENSA_AULA">Mensa/Aula</option>
            <option value="EXTERN">Extern</option>
            <option value="PROVISORIUM">Provisorium</option>
          </select>
        </div>

        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Straße</label>
          <input v-model="form.strasse" type="text" class="input-field" required />
        </div>

        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Hausnummer (optional)</label>
          <input v-model="form.hausnummer" type="text" class="input-field" />
        </div>

        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Postleitzahl</label>
          <input v-model="form.postleitzahl" type="text" class="input-field" required />
        </div>

        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Ort</label>
          <input v-model="form.ort" type="text" class="input-field" required />
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
  gebaeude: { type: Object, default: null },
});

const emit = defineEmits(['close', 'save']);

const form = reactive({
  id: null,
  name: '',
  typ: 'HAUPTGEBÄUDE',
  strasse: '',
  hausnummer: '',
  postleitzahl: '',
  ort: '',
});

watch(
    () => props.gebaeude,
    (val) => {
      form.id = val?.id ?? null;
      form.name = val?.name ?? '';
      form.typ = val?.typ ?? 'HAUPTGEBÄUDE';
      form.strasse = val?.strasse ?? '';
      form.hausnummer = val?.hausnummer ?? '';
      form.postleitzahl = val?.postleitzahl ?? '';
      form.ort = val?.ort ?? '';
    },
    { immediate: true }
);

const save = () => {
  emit('save', { ...form });
};
</script>

<style scoped>
.input-field { @apply w-full rounded-lg border border-gray-300 px-3 py-2 text-gray-900 focus:outline-none focus:ring-2 focus:ring-indigo-500 bg-white text-sm; }
.btn-primary { @apply rounded-lg bg-indigo-600 px-4 py-2 text-white font-bold hover:bg-indigo-700 transition shadow-sm; }
.btn-secondary { @apply rounded-lg bg-gray-100 px-4 py-2 text-gray-700 font-medium hover:bg-gray-200 transition; }
</style>
