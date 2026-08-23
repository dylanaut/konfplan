<template>
  <div v-if="isVisible" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4">
    <div class="w-full max-w-2xl rounded-xl bg-white p-6 shadow-2xl overflow-y-auto max-h-[90vh]">
      <div class="flex items-center justify-between mb-6">
        <h2 class="text-xl font-bold text-gray-900">
          {{ form.id ? 'Vortragsdetails bearbeiten' : 'Neuen Vortrag anlegen' }}
        </h2>
        <button class="text-gray-500 hover:text-gray-700" @click="close">✕</button>
      </div>

      <div v-if="isLocked" class="mb-6 p-4 bg-red-50 border border-red-200 rounded-lg text-red-800 text-sm">
        Die Deadline für diese Veranstaltung ist bereits abgelaufen. Änderungen sind nicht mehr möglich.
      </div>

      <form class="space-y-4" @submit.prevent="save">
        <div v-if="!form.id">
          <label class="block text-sm font-medium text-gray-700 mb-1">Veranstaltung auswählen</label>
          <select v-model="form.veranstaltungId" class="input-field" required>
            <option :value="null" disabled>-- Bitte wählen --</option>
            <option v-for="event in events" :key="event.id" :value="event.id" :disabled="isDeadlinePassed(event.deadlineReferenten)">
              {{ event.name }} {{ isDeadlinePassed(event.deadlineReferenten) ? '(Abgelaufen)' : '' }}
            </option>
          </select>
        </div>

        <div>
          <label class="block text-sm font-medium text-gray-700">Titel des Vortrags</label>
          <input v-model="form.titel" type="text" class="input-field" :disabled="isLocked" />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700">Abstract (Kurzbeschreibung)</label>
          <textarea v-model="form.inhalt" rows="4" class="input-field" :disabled="isLocked"></textarea>
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700">Benötigte Ausstattung (optional)</label>
          <textarea v-model="form.ausstattung" rows="2" class="input-field" placeholder="z.B. Beamer, Whiteboard, Starkstromanschluss..." :disabled="isLocked"></textarea>
        </div>

        <div v-if="false" class="flex items-center gap-4 p-4 bg-indigo-50 rounded-lg">
          <input v-model="form.wiederholbar" type="checkbox" class="w-5 h-5 text-indigo-600" id="repeat" :disabled="isLocked" />
          <label for="repeat" class="text-sm font-medium text-indigo-900">
            Ich bin bereit, den Vortrag bei hoher Nachfrage mehrfach zu halten.
          </label>
        </div>

        <div class="p-4 bg-amber-50 rounded-lg">
          <label class="block text-sm font-medium text-gray-700 mb-2">Neigungen (welche Neigungen adressiert dieser Vortrag inhaltlich?)</label>
          <div class="grid grid-cols-2 md:grid-cols-3 gap-2">
            <div v-for="neigung in neigungStore.neigungen" :key="neigung.name" class="flex items-center gap-2 bg-white p-2 rounded-md border" :title="neigung.beschreibung">
              <input :id="`talk-neigung-${neigung.name}`" type="checkbox" :value="neigung.name" v-model="form.neigungen" class="h-4 w-4 rounded text-indigo-600 focus:ring-indigo-500 border-gray-300" :disabled="isLocked">
              <label :for="`talk-neigung-${neigung.name}`" class="text-sm font-medium text-gray-700">{{ neigung.bezeichnung }}</label>
            </div>
          </div>
        </div>

        <div class="flex justify-end gap-3 pt-4 border-t">
          <button type="button" class="btn-secondary" @click="close">Abbrechen</button>
          <button v-if="!isLocked" type="submit" class="btn-primary">Speichern</button>
        </div>
      </form>
    </div>
  </div>
</template>

<script setup>
import { reactive, watch, computed } from 'vue';
import { useNeigungStore } from '../stores/neigung';

const neigungStore = useNeigungStore();
neigungStore.fetchNeigungen();

const props = defineProps({
  isVisible: { type: Boolean, required: true },
  talk: { type: Object, default: null },
  events: { type: Array, default: () => [] },
});

const emit = defineEmits(['close', 'save']);

const form = reactive({
  id: null,
  version: null,
  veranstaltungId: null,
  titel: '',
  inhalt: '',
  ausstattung: '',
  abschluss: null,
  wiederholbar: false,
  neigungen: [],
});

watch(
    () => props.talk,
    (val) => {
      form.id = val?.id ?? null;
      form.version = val?.version ?? null;
      form.veranstaltungId = val?.veranstaltungId ?? null;
      form.titel = val?.titel ?? '';
      form.inhalt = val?.inhalt ?? '';
      form.ausstattung = val?.ausstattung ?? '';
      form.abschluss = val?.abschluss ?? null;
      form.wiederholbar = val?.wiederholbar ?? false;
      form.neigungen = val?.neigungen ? [...val.neigungen] : [];
    },
    { immediate: true }
);

const isDeadlinePassed = (deadline) => {
  if (!deadline) return false;
  return new Date(deadline) < new Date();
};

const isLocked = computed(() => {
  const event = props.events.find(e => e.id === form.veranstaltungId);
  return event ? isDeadlinePassed(event.deadlineReferenten) : false;
});

const close = () => {
  emit('close');
};

const save = () => {
  emit('save', { ...form });
};
</script>

<style scoped>
.input-field { @apply w-full rounded-lg border border-gray-300 px-3 py-2 text-gray-900 focus:ring-2 focus:ring-indigo-500 bg-white text-sm; }
.btn-primary { @apply rounded-lg bg-indigo-600 px-4 py-2 text-white font-bold hover:bg-indigo-700 transition; }
.btn-secondary { @apply rounded-lg bg-gray-100 px-4 py-2 text-gray-700 font-medium hover:bg-gray-200 transition; }
</style>
