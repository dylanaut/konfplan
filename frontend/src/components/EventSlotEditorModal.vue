<template>
  <div v-if="isVisible" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4">
    <div class="w-full max-w-lg rounded-xl bg-white p-6 shadow-2xl">
      <div class="flex items-center justify-between mb-6">
        <h2 class="text-xl font-bold text-gray-900">
          {{ slot?.id ? 'Zeit-Slot bearbeiten' : 'Neuen Zeit-Slot anlegen' }}
        </h2>
        <button class="text-gray-500 hover:text-gray-700" @click="$emit('close')">✕</button>
      </div>

      <form class="space-y-4" @submit.prevent="save">
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Bezeichnung</label>
          <input v-model="form.description" type="text" class="input-field" placeholder="z.B. Slot A, Vormittag..." required />
        </div>

        <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Beginn</label>
            <input v-model="form.startTime" type="datetime-local" class="input-field" required />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Ende</label>
            <input v-model="form.endTime" type="datetime-local" class="input-field" required />
          </div>
        </div>

        <div class="flex justify-end gap-3 pt-6 border-t mt-4">
          <button type="button" class="btn-secondary" @click="$emit('close')">Abbrechen</button>
          <button type="submit" class="btn-primary">
            {{ slot?.id ? 'Änderungen speichern' : 'Slot erstellen' }}
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
  slot: { type: Object, default: null },
});

const emit = defineEmits(['close', 'save']);

const form = reactive({
  id: null,
  description: '',
  startTime: '',
  endTime: '',
});

watch(
    () => props.slot,
    (val) => {
      form.id = val?.id ?? null;
      form.description = val?.description ?? '';
      // Format datetime-local string (yyyy-MM-ddThh:mm)
      form.startTime = val?.startTime ? val.startTime.slice(0, 16) : '';
      form.endTime = val?.endTime ? val.endTime.slice(0, 16) : '';
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
