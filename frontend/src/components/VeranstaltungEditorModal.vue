<template>
  <div v-if="isVisible" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4">
    <div class="w-full max-w-2xl rounded-xl bg-white p-6 shadow-2xl overflow-y-auto max-h-[90vh]">
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

        <!-- Organisatoren Auswahl (Multi-Select) -->
        <div class="md:col-span-2">
          <label class="block text-sm font-medium text-gray-700 mb-2">Organisatoren (Admins)</label>
          <div class="grid grid-cols-1 sm:grid-cols-2 gap-2 max-h-40 overflow-y-auto p-3 bg-gray-50 border rounded-lg">
            <div v-for="admin in admins" :key="admin.id" class="flex items-center gap-2">
              <input type="checkbox" :id="'admin-'+admin.id" :value="admin.id" v-model="form.organisatorIds" class="h-4 w-4 rounded border-gray-300 text-indigo-600 focus:ring-indigo-500" />
              <label :for="'admin-'+admin.id" class="text-sm text-gray-700 truncate">
                {{ admin.lastName }}, {{ admin.firstName }}
              </label>
            </div>
            <div v-if="admins.length === 0" class="text-xs text-gray-500 italic p-1">
              Keine Administratoren gefunden. Legen Sie zuerst welche an.
            </div>
          </div>
        </div>

        <!-- Gebaeude Auswahl (Multi-Select) -->
        <div class="md:col-span-2">
          <label class="block text-sm font-medium text-gray-700 mb-2">Zugehörige Gebäude</label>
          <div class="grid grid-cols-1 sm:grid-cols-2 gap-2 max-h-40 overflow-y-auto p-3 bg-gray-50 border rounded-lg">
            <div v-for="g in allGebaeude" :key="g.id" class="flex items-center gap-2">
              <input type="checkbox" :id="'g-'+g.id" :value="g.id" v-model="selectedGebaeudeIds" class="h-4 w-4 rounded border-gray-300 text-indigo-600 focus:ring-indigo-500" />
              <label :for="'g-'+g.id" class="text-sm text-gray-700 truncate">
                {{ g.name }} ({{ g.ort }})
              </label>
            </div>
          </div>
        </div>

        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Logo URL (optional)</label>
          <input v-model="form.logo" type="text" class="input-field" placeholder="https://..." />
        </div>

        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Logo Link URL (optional)</label>
          <input v-model="form.logo_link" type="url" class="input-field" placeholder="https://..." />
        </div>

        <div class="md:col-span-2 flex justify-end gap-3 pt-4 border-t">
          <button type="button" class="btn-secondary" @click="$emit('close')">Abbrechen</button>
          <button type="submit" class="btn-primary" :disabled="form.organisatorIds.length === 0">Speichern</button>
        </div>
      </form>
    </div>
  </div>
</template>

<script setup>
import { reactive, watch, ref } from 'vue';

const props = defineProps({
  isVisible: { type: Boolean, required: true },
  veranstaltung: { type: Object, default: null },
  admins: { type: Array, default: () => [] },
  allGebaeude: { type: Array, default: () => [] }
});

const emit = defineEmits(['close', 'save']);
const selectedGebaeudeIds = ref([]);

const form = reactive({
  id: null,
  name: '',
  beginntAm: '',
  endetAm: '',
  logo: '',
  logo_link: '',
  organisatorIds: [],
  version: 0
});

watch(
    () => props.veranstaltung,
    (val) => {
      form.id = val?.id ?? null;
      form.name = val?.name ?? '';
      form.beginntAm = val?.beginntAm ? val.beginntAm.slice(0, 16) : '';
      form.endetAm = val?.endetAm ? val.endetAm.slice(0, 16) : '';
      form.logo = val?.logo ?? '';
      form.logo_link = val?.logo_link ?? '';
      form.organisatorIds = val?.organisatorIds ?? [];
      form.version = val?.version ?? 0;
      selectedGebaeudeIds.value = val?.gebaeude?.map(g => g.id) ?? [];
    },
    { immediate: true }
);

const save = () => {
  const gebaeude = selectedGebaeudeIds.value.map(id => ({ id }));
  emit('save', { ...form, gebaeude });
};
</script>

<style scoped>
.input-field { @apply w-full rounded-lg border border-gray-300 px-3 py-2 text-gray-900 focus:outline-none focus:ring-2 focus:ring-indigo-500 bg-white; }
.btn-primary { @apply rounded-lg bg-indigo-600 px-4 py-2 text-white hover:bg-indigo-700 disabled:opacity-50; }
.btn-secondary { @apply rounded-lg bg-gray-100 px-4 py-2 text-gray-700 hover:bg-gray-200; }
</style>
