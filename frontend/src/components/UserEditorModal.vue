<template>
  <div v-if="isVisible" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4">
    <div
      class="w-full max-w-2xl rounded-xl bg-white p-6 shadow-2xl overflow-y-auto max-h-[90vh]"
      role="dialog"
      aria-modal="true"
      aria-labelledby="user-editor-modal-title"
    >
      <div class="flex items-center justify-between mb-6">
        <h2 id="user-editor-modal-title" class="text-xl font-bold text-gray-900">
          {{ nutzer?.id ? 'Nutzer bearbeiten' : 'Neuen Nutzer anlegen' }}
        </h2>
        <button class="text-gray-500 hover:text-gray-700" aria-label="Dialog schließen" @click="$emit('close')">✕</button>
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
          <label class="block text-sm font-medium text-gray-700 mb-1">Anmeldename</label>
          <input v-model="form.loginName" type="text" class="input-field" required :disabled="!!nutzer?.id" />
          <p v-if="nutzer?.id" class="text-xs text-gray-500 mt-1">Der Anmeldename kann nach dem Anlegen nicht mehr geändert werden.</p>
        </div>

        <div class="md:col-span-2">
          <label class="block text-sm font-medium text-gray-700 mb-1">
            E-Mail{{ form.role === 'ADMIN' ? ' (erforderlich für Administratoren)' : ' (optional)' }}
          </label>
          <input v-model="form.email" type="email" class="input-field" :required="form.role === 'ADMIN'" />
          <p v-if="form.role === 'ADMIN'" class="text-xs text-gray-500 mt-1">
            Ohne E-Mail-Adresse kann ein vergessenes Passwort nicht selbst zurückgesetzt werden.
          </p>
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
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Job-Rolle / Position</label>
            <input v-model="form.jobRole" type="text" class="input-field" placeholder="z.B. Softwareentwickler, Manager..." />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Organisation</label>
            <input v-model="form.organisation" type="text" class="input-field" placeholder="z.B. msg systems ag" />
          </div>
        </div>

        <!-- Rollenspezifische Felder: TEILNEHMER -->
        <div v-if="form.role === 'TEILNEHMER'" class="md:col-span-2 bg-green-50 p-4 rounded-lg">
          <h3 class="text-xs font-bold text-green-700 uppercase tracking-wider mb-3">Gruppenzugehörigkeit</h3>
          <div class="space-y-2">
            <p v-if="groupStore.gruppen.length === 0" class="text-xs text-gray-500">Für die ausgewählte Veranstaltung sind keine Gruppen definiert.</p>
            <div v-else class="grid grid-cols-2 md:grid-cols-3 gap-2">
              <div v-for="gruppe in groupStore.gruppen" :key="gruppe" class="flex items-center gap-2 bg-white p-2 rounded-md border">
                <input :id="`gruppe-${gruppe}`" type="checkbox" :value="gruppe" v-model="form.gruppen" class="h-4 w-4 rounded text-indigo-600 focus:ring-indigo-500 border-gray-300">
                <label :for="`gruppe-${gruppe}`" class="text-sm font-medium text-gray-700">{{ gruppe }}</label>
              </div>
            </div>
          </div>
        </div>

        <div v-if="form.role === 'TEILNEHMER'" class="md:col-span-2 bg-amber-50 p-4 rounded-lg">
          <h3 class="text-xs font-bold text-amber-700 uppercase tracking-wider mb-3">Neigungen</h3>
          <div class="grid grid-cols-2 md:grid-cols-3 gap-2">
            <div v-for="neigung in neigungStore.neigungen" :key="neigung.name" class="flex items-center gap-2 bg-white p-2 rounded-md border" :title="neigung.beschreibung">
              <input :id="`neigung-${neigung.name}`" type="checkbox" :value="neigung.name" v-model="form.neigungen" class="h-4 w-4 rounded text-indigo-600 focus:ring-indigo-500 border-gray-300">
              <label :for="`neigung-${neigung.name}`" class="text-sm font-medium text-gray-700">{{ neigung.bezeichnung }}</label>
            </div>
          </div>
        </div>

        <div class="md:col-span-2 flex items-center gap-2 mt-2">
          <input id="isActive" v-model="form.isActive" type="checkbox" class="h-4 w-4" />
          <label for="isActive" class="text-sm text-gray-700 font-medium">Nutzerkonto ist aktiv</label>
        </div>

        <div class="md:col-span-2 flex justify-end gap-3 pt-6 border-t mt-4">
          <button type="button" class="btn-secondary" @click="$emit('close')">Abbrechen</button>
          <button type="submit" class="btn-primary">
            {{ nutzer?.id ? 'Änderungen speichern' : 'Nutzer erstellen' }}
          </button>
        </div>
      </form>
    </div>
  </div>
</template>

<script setup>
import { reactive, watch } from 'vue';
import { useGroupStore } from '../stores/group';
import { useNeigungStore } from '../stores/neigung';

const props = defineProps({
  isVisible: { type: Boolean, required: true },
  nutzer: { type: Object, default: null },
  selectedVid: { type: Number, default: null }
});

const emit = defineEmits(['close', 'save']);
const groupStore = useGroupStore();
const neigungStore = useNeigungStore();
neigungStore.fetchNeigungen();

const form = reactive({
  id: null,
  loginName: '',
  firstName: '',
  lastName: '',
  email: '',
  role: 'TEILNEHMER',
  isActive: true,
  jobRole: '',
  organisation: '',
  gruppen: [],
  neigungen: [],
  version: null,
});

watch(
    [() => props.isVisible, () => props.selectedVid],
    ([visible, selectedVid]) => {
      if (visible && selectedVid) {
        groupStore.fetchGruppen(selectedVid);
      }
    }
);

watch(
    () => props.nutzer,
    (val) => {
      form.id = val?.id ?? null;
      form.version = val?.version ?? null;
      form.loginName = val?.loginName ?? '';
      form.firstName = val?.firstName ?? '';
      form.lastName = val?.lastName ?? '';
      form.email = val?.email ?? '';
      form.role = val?.role ?? 'TEILNEHMER';
      form.isActive = val?.isActive ?? true;
      form.jobRole = val?.jobRole ?? '';
      form.organisation = val?.organisation ?? '';
      form.gruppen = val?.gruppen ? [...val.gruppen] : [];
      form.neigungen = val?.neigungen ? [...val.neigungen] : [];
    },
    { immediate: true, deep: false }
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
