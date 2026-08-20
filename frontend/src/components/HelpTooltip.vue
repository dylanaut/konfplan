<template>
  <span class="relative inline-flex" ref="root">
    <button type="button"
            @click.stop="open = !open"
            class="text-current opacity-60 hover:opacity-100 transition-opacity"
            :aria-label="`Hilfe: ${label}`">
      <CircleQuestionMarkIcon class="w-3.5 h-3.5"/>
    </button>
    <div v-if="open"
         class="absolute z-50 top-full mt-1.5 left-1/2 -translate-x-1/2 w-64 rounded-lg bg-gray-900 text-white text-[11px] leading-relaxed font-normal normal-case tracking-normal p-3 shadow-2xl">
      <p v-if="label" class="font-bold mb-1">{{ label }}</p>
      <p class="whitespace-pre-line">{{ text }}</p>
    </div>
  </span>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue';
import { CircleQuestionMark as CircleQuestionMarkIcon } from '@lucide/vue';

defineProps({
  label: { type: String, default: '' },
  text: { type: String, required: true },
});

const open = ref(false);
const root = ref(null);

const handleClickOutside = (event) => {
  if (root.value && !root.value.contains(event.target)) {
    open.value = false;
  }
};

const handleEscape = (event) => {
  if (event.key === 'Escape') open.value = false;
};

onMounted(() => {
  document.addEventListener('click', handleClickOutside);
  document.addEventListener('keydown', handleEscape);
});

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside);
  document.removeEventListener('keydown', handleEscape);
});
</script>
