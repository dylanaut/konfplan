<template>
  <div v-if="isVisible" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4">
    <div class="w-full max-w-2xl rounded-xl bg-white p-6 shadow-2xl">
      <div class="flex items-center justify-between mb-6">
        <h2 class="text-xl font-bold text-gray-900">
          {{ user?.id ? 'Benutzer bearbeiten' : 'Neuen Benutzer anlegen' }}
        </h2>
        <button class="text-gray-500 hover:text-gray-700" @click="$emit('close')">✕</button>
      </div>

      <form class="grid grid-cols-1 md:grid-cols-2 gap-4" @submit.prevent="save">
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Vorname</label>
          <input v-model="form.firstName" type="text" class="input-field" required />
        </div>

        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Nachname</label>
          <input v-model="form.lastName" type="text" class="input-field" required />
        </div>

        <div class="md:col-span-2">
          <label class="block text-sm font-medium text-gray-700 mb-1">E-Mail</label>
          <input v-model="form.email" type="email" class="input-field" required />
        </div>

        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Rolle</label>
          <select v-model="form.role" class="input-field" required>
            <option value="PARTICIPANT">Teilnehmer</option>
            <option value="SPEAKER">Referent</option>
            <option value="ADMIN">Administrator</option>
          </select>
        </div>

        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Organisation</label>
          <input v-model="form.organization" type="text" class="input-field" />
        </div>

        <div class="md:col-span-1">
          <label class="block text-sm font-medium text-gray-700 mb-1">Position im Job</label>
          <input v-model="form.jobPosition" type="text" class="input-field" />
        </div>

        <div class="md:col-span-2 flex items-center gap-2">
          <input id="isActive" v-model="form.isActive" type="checkbox" class="h-4 w-4" />
          <label for="isActive" class="text-sm text-gray-700">Aktiv</label>
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
  user: { type: Object, default: null },
});

const emit = defineEmits(['close', 'save']);

const form = reactive({
  id: null,
  firstName: '',
  lastName: '',
  email: '',
  role: 'PARTICIPANT',
  organization: '',
  jobPosition: '',
  isActive: true,
});

watch(
    () => props.user,
    (val) => {
      form.id = val?.id ?? null;
      form.firstName = val?.firstName ?? '';
      form.lastName = val?.lastName ?? '';
      form.email = val?.email ?? '';
      form.role = val?.role ?? 'PARTICIPANT';
      form.organization = val?.organization ?? '';
      form.jobPosition = val?.jobPosition ?? '';
      form.isActive = val?.isActive ?? true;
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
