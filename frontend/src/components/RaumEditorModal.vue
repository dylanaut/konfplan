<template>
  <div v-if="isVisible" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4">
    <div class="w-full max-w-2xl rounded-xl bg-white p-6 shadow-2xl">
      <div class="flex items-center justify-between mb-6">
        <h2 class="text-xl font-bold text-gray-900">
          {{ raum?.id ? 'Raum bearbeiten' : 'Neuen Raum anlegen' }}
        </h2>
        <button class="text-gray-500 hover:text-gray-700" @click="$emit('close')">✕</button>
      </div>

      <form class="grid grid-cols-1 md:grid-cols-2 gap-4" @submit.prevent="save">
        <div class="md:col-span-2">
          <label class="block text-sm font-medium text-gray-700 mb-1">Name des Raums</label>
          <input v-model="form.name" type="text" class="input-field" required />
        </div>

        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Kapazität (Personen)</label>
          <input v-model.number="form.kapazitaet" type="number" min="1" class="input-field" required />
        </div>

        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Etage (optional)</label>
          <input v-model="form.etage" type="text" class="input-field" />
        </div>

        <!-- Verfügbare Slots (Checkboxes) -->
        <div class="md:col-span-2">
          <label class="block text-sm font-medium text-gray-700 mb-2">Verfügbare Zeit-Slots</label>
          <div class="grid grid-cols-1 sm:grid-cols-2 gap-2 max-h-48 overflow-y-auto border rounded-lg p-3 bg-gray-50">
            <div v-for="slot in slots" :key="slot.id" class="flex items-center gap-2">
              <input type="checkbox" :id="'slot-' + slot.id" :value="slot.id" v-model="selectedSlotIds" class="h-4 w-4 rounded text-indigo-600 focus:ring-indigo-500" />
              <label :for="'slot-' + slot.id" class="text-sm text-gray-700 truncate">
                {{ formatSlot(slot) }}
              </label>
            </div>
          </div>
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
import { reactive, watch, ref } from 'vue';

const props = defineProps({
  isVisible: { type: Boolean, required: true },
  raum: { type: Object, default: null },
  slots: { type: Array, default: () => [] }
});

const emit = defineEmits(['close', 'save']);

const selectedSlotIds = ref([]);

const form = reactive({
  id: null,
  name: '',
  kapazitaet: 10,
  etage: '',
});

watch(
    () => props.raum,
    (val) => {
      form.id = val?.id ?? null;
      form.name = val?.name ?? '';
      form.kapazitaet = val?.kapazitaet ?? 10;
      form.etage = val?.etage ?? '';
      selectedSlotIds.value = val?.verfuegbareSlots?.map(s => s.id) ?? [];
    },
    { immediate: true }
);

const formatSlot = (slot) => {
  const start = new Date(slot.startTime).toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' });
  const end = new Date(slot.endTime).toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' });
  return `${slot.description || 'Slot'}: ${start} - ${end}`;
};

const save = () => {
  // Mapping der IDs zurück zu Slot-Objekten für das Backend
  const verfuegbareSlots = props.slots.filter(s => selectedSlotIds.value.includes(s.id));
  emit('save', { ...form, verfuegbareSlots });
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
