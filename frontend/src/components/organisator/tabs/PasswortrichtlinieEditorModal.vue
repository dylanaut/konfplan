<template>
  <div v-if="isVisible" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4">
    <div class="w-full max-w-md rounded-xl bg-white p-6 shadow-2xl">
      <div class="flex items-center justify-between mb-6">
        <h2 class="text-xl font-bold text-gray-900">Passwortrichtlinie für {{ rolle }}</h2>
        <button class="text-gray-500 hover:text-gray-700" @click="close">✕</button>
      </div>

      <div class="flex gap-2 mb-4">
        <button type="button" class="btn-secondary text-xs" @click="anwendenStandard">Standard</button>
        <button type="button" class="btn-secondary text-xs" @click="anwendenJuniorPin">Junior-PIN (6 Ziffern)</button>
      </div>

      <form class="space-y-4" @submit.prevent="save">
        <div class="flex items-center gap-2">
          <input id="nurZiffern" v-model="form.nurZiffern" type="checkbox" class="h-5 w-5" />
          <label for="nurZiffern" class="text-sm font-medium">Nur-Ziffern-PIN (ignoriert die Zeichenklassen unten)</label>
        </div>

        <div class="grid grid-cols-2 gap-4">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Mindestlänge</label>
            <input v-model.number="form.minLaenge" type="number" min="1" class="input-field" required />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Maximallänge (leer = unbegrenzt)</label>
            <input v-model.number="form.maxLaenge" type="number" min="1" class="input-field" />
          </div>
        </div>

        <div v-if="!form.nurZiffern" class="space-y-2">
          <div class="flex items-center gap-2">
            <input id="grossbuchstabe" v-model="form.erfordertGrossbuchstabe" type="checkbox" class="h-5 w-5" />
            <label for="grossbuchstabe" class="text-sm font-medium">Großbuchstabe erforderlich</label>
          </div>
          <div class="flex items-center gap-2">
            <input id="kleinbuchstabe" v-model="form.erfordertKleinbuchstabe" type="checkbox" class="h-5 w-5" />
            <label for="kleinbuchstabe" class="text-sm font-medium">Kleinbuchstabe erforderlich</label>
          </div>
          <div class="flex items-center gap-2">
            <input id="ziffer" v-model="form.erfordertZiffer" type="checkbox" class="h-5 w-5" />
            <label for="ziffer" class="text-sm font-medium">Ziffer erforderlich</label>
          </div>
          <div class="flex items-center gap-2">
            <input id="sonderzeichen" v-model="form.erfordertSonderzeichen" type="checkbox" class="h-5 w-5" />
            <label for="sonderzeichen" class="text-sm font-medium">Sonderzeichen erforderlich</label>
          </div>
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
  rolle: { type: String, default: '' },
  richtlinie: { type: Object, default: null },
});

const emit = defineEmits(['close', 'save']);

const form = reactive({
  minLaenge: 8,
  maxLaenge: null,
  erfordertGrossbuchstabe: true,
  erfordertKleinbuchstabe: true,
  erfordertZiffer: true,
  erfordertSonderzeichen: true,
  nurZiffern: false,
});

watch(
  () => props.richtlinie,
  (val) => {
    form.minLaenge = val?.minLaenge ?? 8;
    form.maxLaenge = val?.maxLaenge ?? null;
    form.erfordertGrossbuchstabe = val?.erfordertGrossbuchstabe ?? true;
    form.erfordertKleinbuchstabe = val?.erfordertKleinbuchstabe ?? true;
    form.erfordertZiffer = val?.erfordertZiffer ?? true;
    form.erfordertSonderzeichen = val?.erfordertSonderzeichen ?? true;
    form.nurZiffern = val?.nurZiffern ?? false;
  },
  { immediate: true }
);

const anwendenStandard = () => {
  form.minLaenge = 8;
  form.maxLaenge = null;
  form.erfordertGrossbuchstabe = true;
  form.erfordertKleinbuchstabe = true;
  form.erfordertZiffer = true;
  form.erfordertSonderzeichen = true;
  form.nurZiffern = false;
};

const anwendenJuniorPin = () => {
  form.minLaenge = 6;
  form.maxLaenge = 6;
  form.erfordertGrossbuchstabe = false;
  form.erfordertKleinbuchstabe = false;
  form.erfordertZiffer = false;
  form.erfordertSonderzeichen = false;
  form.nurZiffern = true;
};

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
</style>
