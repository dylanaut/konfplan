<template>
  <div class="d-flex align-items-center gap-3 mb-4 pb-3 border-bottom veranstaltung-header">
    <component :is="veranstaltung.logo_link ? 'a' : 'div'" v-if="veranstaltung.logo"
               :href="veranstaltung.logo_link || undefined"
               :target="veranstaltung.logo_link ? '_blank' : undefined"
               :rel="veranstaltung.logo_link ? 'noopener noreferrer' : undefined"
               class="flex-shrink-0">
      <img :src="veranstaltung.logo" :alt="veranstaltung.name" class="veranstaltung-logo" />
    </component>
    <div class="min-width-0">
      <h2 class="h5 mb-0">{{ veranstaltung.name }}</h2>
      <p class="text-muted small mb-0">{{ formatZeitraum(veranstaltung) }}</p>
      <p v-if="veranstaltung.organisatorNamen && veranstaltung.organisatorNamen.length" class="text-muted small mb-0">
        Organisation: {{ veranstaltung.organisatorNamen.join(', ') }}
      </p>
    </div>
  </div>
</template>

<script setup>
import { formatZeitraum } from '../utils/veranstaltungFormat';

defineProps({
  veranstaltung: {
    type: Object,
    required: true,
  },
});
</script>

<style scoped>
.veranstaltung-logo {
  height: 48px;
  width: auto;
  object-fit: contain;
}
</style>
