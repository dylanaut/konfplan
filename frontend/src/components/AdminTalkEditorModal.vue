<template>
  <div v-if="isVisible" class="fixed inset-0 z-50 overflow-y-auto" aria-labelledby="modal-title" role="dialog" aria-modal="true">
    <!-- Backdrop -->
    <div class="flex items-end justify-center min-h-screen pt-4 px-4 pb-20 text-center sm:block sm:p-0">
      <div class="fixed inset-0 bg-gray-500 bg-opacity-75 transition-opacity" @click="close" aria-hidden="true"></div>

      <!-- Modal-Panel -->
      <span class="hidden sm:inline-block sm:align-middle sm:h-screen" aria-hidden="true">&#8203;</span>
      <div class="inline-block align-bottom bg-white rounded-lg text-left overflow-hidden shadow-xl transform transition-all sm:my-8 sm:align-middle sm:max-w-lg sm:w-full">

        <div class="bg-white px-4 pt-5 pb-4 sm:p-6 sm:pb-4">
          <div class="sm:flex sm:items-start">
            <div class="mx-auto flex-shrink-0 flex items-center justify-center h-12 w-12 rounded-full bg-indigo-100 sm:mx-0 sm:h-10 sm:w-10">
              <Edit3Icon class="h-6 w-6 text-indigo-600" />
            </div>
            <div class="mt-3 text-center sm:mt-0 sm:ml-4 sm:text-left w-full">
              <h3 class="text-lg leading-6 font-medium text-gray-900" id="modal-title">Vortrag bearbeiten</h3>

              <div class="mt-4 space-y-4">
                <!-- Titel -->
                <div>
                  <label class="block text-sm font-medium text-gray-700">Titel</label>
                  <input v-model="localTalk.title" type="text" class="edit-input" />
                </div>

                <!-- Referent (Dropdown) -->
                <div>
                  <label class="block text-sm font-medium text-gray-700">Referent</label>
                  <select v-model="localTalk.speaker.id" class="edit-input">
                    <option v-for="s in speakers" :key="s.id" :value="s.id">
                      {{ s.lastName }}, {{ s.firstName }} ({{ s.organization }})
                    </option>
                  </select>
                </div>

                <!-- Zielpublikum -->
                <div>
                  <label class="block text-sm font-medium text-gray-700">Zielpublikum</label>
                  <input v-model="localTalk.targetAudience" type="text" class="edit-input" />
                </div>

                <!-- Abstract -->
                <div>
                  <label class="block text-sm font-medium text-gray-700">Abstract</label>
                  <textarea v-model="localTalk.abstractText" rows="5" class="edit-input"></textarea>
                </div>

                <p class="text-xs text-gray-400">Datensatz-Version: {{ localTalk.version }}</p>
              </div>
            </div>
          </div>
        </div>

        <!-- Buttons -->
        <div class="bg-gray-50 px-4 py-3 sm:px-6 sm:flex sm:flex-row-reverse">
          <button @click="save" type="button" class="w-full inline-flex justify-center rounded-md border border-transparent shadow-sm px-4 py-2 bg-indigo-600 text-base font-medium text-white hover:bg-indigo-700 focus:outline-none sm:ml-3 sm:w-auto sm:text-sm">
            Speichern
          </button>
          <button @click="close" type="button" class="mt-3 w-full inline-flex justify-center rounded-md border border-gray-300 shadow-sm px-4 py-2 bg-white text-base font-medium text-gray-700 hover:bg-gray-50 focus:outline-none sm:mt-0 sm:ml-3 sm:w-auto sm:text-sm">
            Abbrechen
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue';
import { Edit3 as Edit3Icon } from 'lucide-vue-next';

const props = defineProps({
  isVisible: Boolean,
  talk: Object,
  speakers: Array
});

const emit = defineEmits(['close', 'save']);

// Lokale Kopie des Vortrags, um reaktive Seiteneffekte in der Liste zu vermeiden
const localTalk = ref({});

watch(() => props.talk, (newVal) => {
  if (newVal) {
    localTalk.value = JSON.parse(JSON.stringify(newVal)); // Deep Copy
  }
}, { immediate: true });

const close = () => emit('close');
const save = () => emit('save', localTalk.value);
</script>

<style scoped>
.edit-input {
  @apply mt-1 block w-full border-gray-300 rounded-md shadow-sm focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm border p-2;
}
</style>