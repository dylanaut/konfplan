<template>
  <div v-if="isVisible" class="fixed inset-0 z-50 flex items-center justify-center bg-black bg-opacity-50 p-4">
    <div class="bg-white rounded-lg shadow-xl w-full max-w-4xl h-5/6 flex flex-col">
      <!-- Modal Header -->
      <div class="flex justify-between items-center p-4 border-b border-gray-200">
        <h3 class="text-lg font-bold text-gray-900">{{ title }}</h3>
        <button @click="$emit('close')" class="text-gray-400 hover:text-gray-600">
          <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path></svg>
        </button>
      </div>

      <!-- Modal Body - Iframe for HTML content -->
      <div class="flex-grow p-4">
        <iframe :srcdoc="htmlContent" class="w-full h-full border rounded-md"></iframe>
      </div>

      <!-- Modal Footer -->
      <div class="p-4 border-t border-gray-200 text-right">
        <button @click="$emit('close')" class="px-4 py-2 bg-gray-200 text-gray-800 rounded-md hover:bg-gray-300">Schließen</button>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  isVisible: {
    type: Boolean,
    required: true
  },
  htmlContent: {
    type: String,
    default: ''
  },
  title: {
    type: String,
    default: 'Details'
  }
});

defineEmits(['close']);
</script>

<style scoped>
/* Add any specific styles for the modal here if needed */
</style>
