<template>
  <div v-if="isVisible" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4">
    <div class="w-full max-w-2xl rounded-xl bg-white p-6 shadow-2xl">
      <div class="flex items-center justify-between mb-6">
        <h2 class="text-xl font-bold text-gray-900">
          {{ vortrag?.id ? 'Vortrag bearbeiten' : 'Neuen Vortrag anlegen' }}
        </h2>
        <button class="text-gray-500 hover:text-gray-700" @click="$emit('close')">✕</button>
      </div>

      <form class="space-y-4" @submit.prevent="save">
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Titel</label>
          <input v-model="form.title" type="text" class="input-field" required />
        </div>

        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Abstract / Beschreibung</label>
          <textarea v-model="form.abstractText" rows="4" class="input-field"></textarea>
        </div>

        <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Zielgruppe</label>
            <input v-model="form.targetAudience" type="text" class="input-field" />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Referent</label>
            <select v-model="form.referent.id" class="input-field" required>
              <option :value="null">Bitte wählen...</option>
              <option v-for="r in referenten" :key="r.id" :value="r.id">
                {{ r.lastName }}, {{ r.firstName }}
              </option>
            </select>
          </div>
        </div>

        <div class="grid grid-cols-1 md:grid-cols-2 gap-4 bg-gray-50 p-4 rounded-lg border border-gray-100">
          <div class="flex items-center gap-2">
            <input id="istPflicht" v-model="form.istPflicht" type="checkbox" class="h-5 w-5 text-indigo-600 rounded" />
            <label for="istPflicht" class="text-sm font-bold text-gray-900">Pflichtvortrag</label>
          </div>
          <div class="flex items-center gap-2">
            <input id="readyToRepeat" v-model="form.readyToRepeat" type="checkbox" class="h-5 w-5 text-indigo-600 rounded" />
            <label for="readyToRepeat" class="text-sm text-gray-700">Wiederholbar</label>
          </div>
        </div>

        <div class="flex justify-end gap-3 pt-4 border-t">
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
  vortrag: { type: Object, default: null },
  referenten: { type: Array, default: () => [] }
});

const emit = defineEmits(['close', 'save']);

const form = reactive({
  id: null,
  title: '',
  abstractText: '',
  targetAudience: '',
  referent: { id: null },
  maxRepetitions: 1,
  readyToRepeat: false,
  istPflicht: false,
  version: 0
});

watch(
    () => props.vortrag,
    (val) => {
      form.id = val?.id ?? null;
      form.title = val?.title ?? '';
      form.abstractText = val?.abstractText ?? '';
      form.targetAudience = val?.targetAudience ?? '';
      form.referent.id = val?.referent?.id ?? null;
      form.maxRepetitions = val?.maxRepetitions ?? 1;
      form.readyToRepeat = val?.readyToRepeat ?? false;
      form.istPflicht = val?.istPflicht ?? false;
      form.version = val?.version ?? 0;
    },
    { immediate: true }
);

const save = () => {
  emit('save', { ...form });
};
</script>

<style scoped>
.input-field {
  @apply w-full rounded-lg border border-gray-300 px-3 py-2 text-gray-900 focus:outline-none focus:ring-2 focus:ring-indigo-500 bg-white;
}
.btn-primary { @apply rounded-lg bg-indigo-600 px-4 py-2 text-white hover:bg-indigo-700; }
.btn-secondary { @apply rounded-lg bg-gray-100 px-4 py-2 text-gray-700 hover:bg-gray-200; }
</style>
