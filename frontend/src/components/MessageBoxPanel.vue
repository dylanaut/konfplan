<template>
  <div class="w-80 bg-white text-gray-800 rounded-lg shadow-2xl border border-gray-200 p-4 z-50 max-h-[70vh] overflow-y-auto">
    <div class="flex items-center justify-between mb-3">
      <h3 class="font-bold text-sm">Nachrichten</h3>
      <button @click="close" class="text-gray-400 hover:text-gray-600">✕</button>
    </div>

    <div v-if="loading" class="text-xs text-gray-500">Lade Nachrichten...</div>
    <div v-else-if="error" class="text-xs text-red-600">Fehler beim Laden der Nachrichten.</div>
    <p v-else-if="messageBox.messages.length === 0" class="text-xs text-gray-500">Keine Nachrichten vorhanden.</p>
    <ul v-else class="space-y-2">
      <li v-for="message in messageBox.messages" :key="message.id"
          @click="handleClick(message)"
          class="p-2 rounded-lg cursor-pointer border"
          :class="message.gelesenAm ? 'bg-gray-50 border-gray-100' : 'bg-indigo-50 border-indigo-100'">
        <div class="flex items-center justify-between gap-2">
          <span class="text-xs font-bold" :class="message.gelesenAm ? 'text-gray-600' : 'text-indigo-700'">{{ message.titel }}</span>
          <span v-if="!message.gelesenAm" class="w-2 h-2 rounded-full bg-indigo-600 shrink-0"></span>
        </div>
        <p class="text-xs text-gray-600 mt-1">{{ message.inhalt }}</p>
        <p class="text-[10px] text-gray-400 mt-1">Von {{ message.absender || 'System' }} · {{ formatDateTime(message.erstelltAm) }}</p>
      </li>
    </ul>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { useMessageBoxStore } from '../stores/messageBox';

const messageBox = useMessageBoxStore();
const emit = defineEmits(['close']);

const loading = ref(true);
const error = ref(false);

const close = () => emit('close');

const formatDateTime = (d) => new Date(d).toLocaleString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });

const handleClick = (message) => {
  if (!message.gelesenAm) {
    messageBox.markAsRead(message.id);
  }
};

onMounted(async () => {
  try {
    await messageBox.fetchMessages();
  } catch (e) {
    error.value = true;
  } finally {
    loading.value = false;
  }
});
</script>
