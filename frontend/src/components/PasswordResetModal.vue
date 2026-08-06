<template>
  <div v-if="isVisible" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4">
    <div class="w-full max-w-md rounded-xl bg-white p-6 shadow-2xl animate-fade-in">
      <div class="flex justify-between items-center mb-4">
        <h2 class="text-xl font-bold text-gray-900">Passwort zurücksetzen</h2>
        <button @click="$emit('close')" class="text-gray-400 hover:text-gray-600">
          <XIcon class="w-6 h-6" />
        </button>
      </div>

      <div v-if="nutzer" class="mb-6">
        <p class="text-sm text-gray-600">
          Neues Passwort für <span class="font-bold text-gray-900">{{ nutzer.firstName }} {{ nutzer.lastName }}</span> ({{ nutzer.loginName }}) setzen.
        </p>
      </div>

      <div class="space-y-4">
        <div>
          <label class="block text-xs font-bold text-gray-500 uppercase tracking-wider mb-1">Neues Passwort</label>
          <input v-model="newPassword" type="password" class="input-field w-full" placeholder="Min. 8 Zeichen" />
        </div>

        <div class="bg-indigo-50 p-3 rounded-lg border border-indigo-100">
          <p class="text-[10px] text-indigo-700 leading-relaxed">
            <InfoIcon class="w-3 h-3 inline mr-1" />
            Das Passwort wird sofort geändert. Bitte teilen Sie es dem Nutzer auf einem sicheren Weg mit.
          </p>
        </div>
      </div>

      <div class="mt-8 flex gap-3">
        <button @click="$emit('close')" class="btn-secondary flex-1">Abbrechen</button>
        <button
          @click="confirmReset"
          :disabled="newPassword.length < 8 || isSubmitting"
          class="btn-primary flex-1 flex items-center justify-center gap-2"
        >
          <LoaderIcon v-if="isSubmitting" class="w-4 h-4 animate-spin" />
          Zurücksetzen
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue';
import { X as XIcon, Info as InfoIcon, Loader as LoaderIcon } from '@lucide/vue';

const props = defineProps({
  isVisible: Boolean,
  nutzer: Object
});

const emit = defineEmits(['close', 'reset']);

const newPassword = ref('');
const isSubmitting = ref(false);

watch(() => props.isVisible, (visible) => {
  if (visible) newPassword.value = '';
});

const confirmReset = async () => {
  if (newPassword.value.length < 8) return;
  isSubmitting.value = true;
  try {
    await emit('reset', { userId: props.nutzer.id, newPassword: newPassword.value });
    newPassword.value = '';
  } finally {
    isSubmitting.value = false;
  }
};
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
