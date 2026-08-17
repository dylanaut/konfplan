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
      <p v-if="veranstaltung.organisatoren && veranstaltung.organisatoren.length" class="text-muted small mb-0">
        Organisation:
        <template v-for="(organisator, index) in veranstaltung.organisatoren" :key="organisator.id">
          <a :href="mailtoLink(organisator.email)">{{ organisator.name }}</a><span v-if="index < veranstaltung.organisatoren.length - 1">, </span>
        </template>
      </p>
    </div>
  </div>
</template>

<script setup>
import { formatZeitraum } from '../utils/veranstaltungFormat';

const props = defineProps({
  veranstaltung: {
    type: Object,
    required: true,
  },
});

const mailtoLink = (email) => `mailto:${email}?subject=${encodeURIComponent(props.veranstaltung.name)}`;
</script>

<style scoped>
.veranstaltung-logo {
  height: 48px;
  width: auto;
  object-fit: contain;
}
</style>
