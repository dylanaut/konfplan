<template>
  <div v-if="isVisible" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4">
    <div class="w-full max-w-2xl rounded-xl bg-white p-6 shadow-2xl">
      <div class="flex items-center justify-between mb-6">
        <h2 class="text-xl font-bold text-gray-900">
          {{ veranstaltung?.id ? 'Veranstaltung bearbeiten' : 'Neue Veranstaltung anlegen' }}
        </h2>
        <button class="text-gray-500 hover:text-gray-700" @click="$emit('close')">✕</button>
      </div>

      <form class="grid grid-cols-1 md:grid-cols-2 gap-4" @submit.prevent="save">
        <div class="md:col-span-2">
          <label class="block text-sm font-medium text-gray-700 mb-1">Name der Veranstaltung</label>
          <input v-model="form.name" type="text" class="input-field" required />
        </div>

        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Beginnt am</label>
          <input v-model="form.beginntAm" type="datetime-local" class="input-field" required />
        </div>

        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Endet am (optional)</label>
          <input v-model="form.endetAm" type="datetime-local" class="input-field" />
        </div>

        <div class="md:col-span-2">
          <label class="block text-sm font-medium text-gray-700 mb-1">Ort / Adresse</label>
          <input v-model="form.ort" type="text" class="input-field" required />
        </div>

        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Logo URL (optional)</label>
          <input v-model="form.logo" type="text" class="input-field" placeholder="z.B. https://example.com/logo.png" />
        </div>

        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Logo Link URL (optional)</label>
          <input v-model="form.logo_link" type="url" class="input-field" placeholder="https://..." />
        </div>

        <div class="md:col-span-2">
          <label class="block text-sm font-medium text-gray-700 mb-1">Organisator (Admin)</label>
          <select v-model="form.organisator.id" class="input-field" required>
            <option v-for="admin in admins" :key="admin.id" :value="admin.id">
              {{ admin.lastName }}, {{ admin.firstName }} ({{ admin.email }})
            </option>
          </select>
        </div>

        <div class="md:col-span-2 flex justify-end gap-3 pt-4">
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
  veranstaltung: { type: Object, default: null },
  admins: { type: Array, default: () => [] }
});

const emit = defineEmits(['close', 'save']);

const form = reactive({
  id: null,
  name: '',
  beginntAm: '',
  endetAm: '',
  ort: '',
  logo: '',
  logo_link: '',
  organisator: { id: null },
  version: 0
});

watch(
    () => props.veranstaltung,
    (val) => {
      form.id = val?.id ?? null;
      form.name = val?.name ?? '';
      form.beginntAm = val?.beginntAm ? val.beginntAm.slice(0, 16) : '';
      form.endetAm = val?.endetAm ? val.endetAm.slice(0, 16) : '';
      form.ort = val?.ort ?? '';
      form.logo = val?.logo ?? '';
      form.logo_link = val?.logo_link ?? '';
      form.organisator.id = val?.organisator?.id ?? (props.admins[0]?.id || null);
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

.btn-primary {
  @apply rounded-lg bg-indigo-600 px-4 py-2 text-white hover:bg-indigo-700;
}

.btn-secondary {
  @apply rounded-lg bg-gray-100 px-4 py-2 text-gray-700 hover:bg-gray-200;
}
</style>
