<template>
  <div v-if="totalPages > 1" class="flex items-center justify-between px-4 py-2 bg-gray-50 border-t border-gray-100">
    <span class="text-[10px] text-gray-500">Seite {{ currentPage }} von {{ totalPages }}</span>
    <div class="flex gap-1.5">
      <button
          class="btn-secondary text-[10px] py-0.5 px-2"
          :disabled="currentPage === 1"
          @click="emit('update:currentPage', currentPage - 1)"
      >
        Zurück
      </button>
      <button
          class="btn-secondary text-[10px] py-0.5 px-2"
          :disabled="currentPage === totalPages"
          @click="emit('update:currentPage', currentPage + 1)"
      >
        Weiter
      </button>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue';

const props = defineProps({
  currentPage: {
    type: Number,
    required: true
  },
  totalItems: {
    type: Number,
    required: true
  },
  pageSize: {
    type: Number,
    required: true
  }
});

const emit = defineEmits(['update:currentPage']);

const totalPages = computed(() => Math.ceil(props.totalItems / props.pageSize));
</script>

<style scoped>
@reference "tailwindcss";

.btn-secondary {
  @apply bg-white text-gray-700 px-3 py-1.5 rounded-lg hover:bg-gray-50 font-bold border border-gray-200 transition shadow-sm cursor-pointer disabled:opacity-50;
}
</style>
