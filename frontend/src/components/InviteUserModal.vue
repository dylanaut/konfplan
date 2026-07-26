<template>
  <div v-if="isVisible" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4">
    <div class="w-full max-w-md rounded-xl bg-white p-6 shadow-2xl animate-fade-in">
      <div class="flex justify-between items-center mb-4">
        <h2 class="text-xl font-bold text-gray-900">Nutzer einladen</h2>
        <button @click="$emit('close')" class="text-gray-400 hover:text-gray-600">
          <XIcon class="w-6 h-6" />
        </button>
      </div>

      <div v-if="nutzer" class="mb-6">
        <p class="text-sm text-gray-600">
          Laden Sie <span class="font-bold text-gray-900">{{ nutzer.firstName }} {{ nutzer.lastName }}</span> zu einer weiteren Veranstaltung ein.
        </p>
      </div>

      <div class="space-y-4">
        <div>
          <label class="block text-xs font-bold text-gray-500 uppercase tracking-wider mb-1">Veranstaltung auswählen</label>
          <select v-model="selectedEventId" class="input-field w-full">
            <option :value="null" disabled>-- Bitte wählen --</option>
            <option v-for="event in futureEvents" :key="event.id" :value="event.id">
              {{ event.name }} ({{ formatDate(event.beginntAm) }})
            </option>
          </select>
          <p v-if="futureEvents.length === 0" class="text-xs text-red-500 mt-1">
            Keine zukünftigen Veranstaltungen verfügbar.
          </p>
        </div>

        <div class="bg-indigo-50 p-3 rounded-lg border border-indigo-100">
          <p class="text-[10px] text-indigo-700 leading-relaxed">
            <InfoIcon class="w-3 h-3 inline mr-1" />
            Der Nutzer wird per E-Mail über die Einladung informiert und der Veranstaltung hinzugefügt.
          </p>
        </div>
      </div>

      <div class="mt-8 flex gap-3">
        <button @click="$emit('close')" class="btn-secondary flex-1">Abbrechen</button>
        <button
          @click="confirmInvite"
          :disabled="!selectedEventId || isSubmitting"
          class="btn-primary flex-1 flex items-center justify-center gap-2"
        >
          <LoaderIcon v-if="isSubmitting" class="w-4 h-4 animate-spin" />
          Einladen
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue';
import { X as XIcon, Info as InfoIcon, Loader as LoaderIcon } from '@lucide/vue';

const props = defineProps({
  isVisible: Boolean,
  nutzer: Object,
  futureEvents: Array
});

const emit = defineEmits(['close', 'invite']);

const selectedEventId = ref(null);
const isSubmitting = ref(false);

const confirmInvite = async () => {
  if (!selectedEventId.value) return;
  isSubmitting.value = true;
  try {
    await emit('invite', { userId: props.nutzer.id, eventId: selectedEventId.value });
    selectedEventId.value = null;
  } finally {
    isSubmitting.value = false;
  }
};

const formatDate = (d) => d ? new Date(d).toLocaleDateString('de-DE') : '';
</script>

<style scoped>
.input-field {
  @apply rounded-lg border border-gray-300 px-3 py-2 text-sm text-gray-900 focus:ring-2 focus:ring-indigo-500 bg-white;
}
.btn-primary {
  @apply rounded-lg bg-indigo-600 px-4 py-2 text-white font-bold hover:bg-indigo-700 transition shadow-sm disabled:opacity-50;
}
.btn-secondary {
  @apply bg-white text-gray-700 px-4 py-2 rounded-lg hover:bg-gray-50 font-bold border border-gray-200 transition shadow-sm;
}
.animate-fade-in { animation: fadeIn 0.2s ease-out; }
@keyframes fadeIn { from { opacity: 0; transform: scale(0.95); } to { opacity: 1; transform: scale(1); } }
</style>
