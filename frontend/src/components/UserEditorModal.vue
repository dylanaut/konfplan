<template>
  <div v-if="isVisible" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4">
    <div class="w-full max-w-2xl rounded-xl bg-white p-6 shadow-2xl overflow-y-auto max-h-[90vh]">
      <div class="flex items-center justify-between mb-6">
        <h2 class="text-xl font-bold text-gray-900">
          {{ nutzer?.id ? 'Benutzer bearbeiten' : 'Neuen Benutzer anlegen' }}
        </h2>
        <button class="text-gray-500 hover:text-gray-700" @click="$emit('close')">✕</button>
      </div>

      <form class="grid grid-cols-1 md:grid-cols-2 gap-4" @submit.prevent="save">
        <!-- Basis-Daten -->
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

        <div class="md:col-span-2">
          <label class="block text-sm font-medium text-gray-700 mb-1">Rolle</label>
          <select v-model="form.role" class="input-field" required :disabled="!!nutzer?.id">
            <option value="TEILNEHMER">Teilnehmer</option>
            <option value="REFERENT">Referent</option>
            <option value="ADMIN">Administrator</option>
          </select>
        </div>

        <!-- Rollenspezifische Felder: REFERENT -->
        <div v-if="form.role === 'REFERENT'" class="md:col-span-2 bg-blue-50 p-4 rounded-lg grid grid-cols-1 md:grid-cols-2 gap-4">
          <h3 class="md:col-span-2 text-xs font-bold text-blue-700 uppercase tracking-wider">Referenten-Profil</h3>
          <div class="md:col-span-2">
            <label class="block text-sm font-medium text-gray-700 mb-1">Job-Rolle / Position</label>
            <input v-model="form.jobRole" type="text" class="input-field" placeholder="z.B. Softwareentwickler, Manager..." />
          </div>
          <div class="md:col-span-2">
            <label class="block text-sm font-medium text-gray-700 mb-1">Biografie / Kurzvita</label>
            <textarea v-model="form.biography" rows="3" class="input-field" placeholder="Erzählen Sie etwas über den Referenten..."></textarea>
          </div>
        </div>

        <!-- Rollenspezifische Felder: TEILNEHMER -->
        <div v-if="form.role === 'TEILNEHMER'" class="md:col-span-2 bg-green-50 p-4 rounded-lg">
          <h3 class="text-xs font-bold text-green-700 uppercase tracking-wider mb-3">Teilnehmer-Details</h3>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Organisation / Schule</label>
            <input v-model="form.organization" type="text" class="input-field" placeholder="Firma / Bildungseinrichtung" />
          </div>
        </div>

        <div class="md:col-span-2 flex items-center gap-2 mt-2">
          <input id="isActive" v-model="form.isActive" type="checkbox" class="h-4 w-4" />
          <label for="isActive" class="text-sm text-gray-700 font-medium">Benutzerkonto ist aktiv</label>
        </div>

        <div class="md:col-span-2 flex justify-end gap-3 pt-6 border-t mt-4">
          <button type="button" class="btn-secondary" @click="$emit('close')">Abbrechen</button>
          <button type="submit" class="btn-primary">
            {{ nutzer?.id ? 'Änderungen speichern' : 'Benutzer erstellen' }}
          </button>
        </div>
      </form>
    </div>
  </div>
</template>

<script setup>
import { reactive, watch } from 'vue';

const props = defineProps({
  isVisible: { type: Boolean, required: true },
  nutzer: { type: Object, default: null },
});

const emit = defineEmits(['close', 'save']);

const form = reactive({
  id: null,
  firstName: '',
  lastName: '',
  email: '',
  role: 'TEILNEHMER',
  isActive: true,
  biography: '',
  organization: '',
  jobRole: '',
});

watch(
    () => props.nutzer,
    (val) => {
      form.id = val?.id ?? null;
      form.firstName = val?.firstName ?? '';
      form.lastName = val?.lastName ?? '';
      form.email = val?.email ?? '';
      form.role = val?.role ?? 'TEILNEHMER';
      form.isActive = val?.isActive ?? true;
      form.biography = val?.biography ?? '';
      form.organization = val?.organization ?? '';
      form.jobRole = val?.jobRole ?? '';
    },
    { immediate: true }
);

const save = () => {
  emit('save', { ...form });
};
</script>

<style scoped>
.input-field {
  @apply w-full rounded-lg border border-gray-300 px-3 py-2 text-gray-900 focus:outline-none focus:ring-2 focus:ring-indigo-500 bg-white text-sm;
}
.btn-primary { @apply rounded-lg bg-indigo-600 px-4 py-2 text-white font-bold hover:bg-indigo-700 transition shadow-sm; }
.btn-secondary { @apply rounded-lg bg-gray-100 px-4 py-2 text-gray-700 font-medium hover:bg-gray-200 transition; }
</style>
