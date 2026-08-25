<template>
  <img v-if="src && !hasError" :src="src" :alt="alt" :class="imgClass" @error="hasError = true" />
</template>

<script setup>
import { ref, watch } from 'vue';

const props = defineProps({
  src: { type: String, default: null },
  alt: { type: String, default: '' },
  imgClass: { type: String, default: '' },
});

const hasError = ref(false);

watch(() => props.src, () => {
  hasError.value = false;
});
</script>
