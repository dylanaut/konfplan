<template>
  <div v-if="totalPages > 1 || editable" class="flex items-center justify-between px-4 py-2 bg-gray-50 border-t border-gray-100 gap-3">
    <div class="flex items-center gap-1.5 text-[10px] text-gray-500">
      <span>Seite</span>
      <input
          v-if="editable"
          type="number" min="1" :max="totalPages || 1"
          :value="currentPage"
          @change="goToPage($event.target.value)"
          class="w-12 text-center border rounded py-0.5 text-[10px]"
      />
      <span v-else>{{ currentPage }}</span>
      <span>von {{ totalPages }}</span>
    </div>
    <div class="flex items-center gap-3">
      <label v-if="editable" class="flex items-center gap-1.5 text-[10px] text-gray-500">
        Einträge/Seite
        <input
            type="number" min="1"
            :value="pageSize"
            @change="changePageSize($event.target.value)"
            class="w-14 text-center border rounded py-0.5 text-[10px]"
        />
      </label>
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
  },
  editable: {
    type: Boolean,
    default: false
  }
});

const emit = defineEmits(['update:currentPage', 'update:pageSize']);

const totalPages = computed(() => Math.ceil(props.totalItems / props.pageSize));

const goToPage = (value) => {
  const parsed = parseInt(value, 10);
  const clamped = Math.min(Math.max(isNaN(parsed) ? props.currentPage : parsed, 1), totalPages.value || 1);
  emit('update:currentPage', clamped);
};

const changePageSize = (value) => {
  const parsed = parseInt(value, 10);
  emit('update:pageSize', isNaN(parsed) || parsed < 1 ? props.pageSize : parsed);
};
</script>

<style scoped>
@reference "tailwindcss";

.btn-secondary {
  @apply bg-white text-gray-700 px-3 py-1.5 rounded-lg hover:bg-gray-50 font-bold border border-gray-200 transition shadow-sm cursor-pointer disabled:opacity-50;
}
</style>
