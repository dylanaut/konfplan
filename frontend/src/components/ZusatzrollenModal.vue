<template>
  <div v-if="isVisible" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4">
    <div class="w-full max-w-md rounded-xl bg-white p-6 shadow-2xl animate-fade-in">
      <div class="flex justify-between items-center mb-4">
        <h2 class="text-xl font-bold text-gray-900">Zusatzrollen verwalten</h2>
        <button @click="$emit('close')" class="text-gray-400 hover:text-gray-600">
          <XIcon class="w-6 h-6" />
        </button>
      </div>

      <div v-if="nutzer" class="mb-6">
        <p class="text-sm text-gray-600">
          Zusätzliche Rollen für <span class="font-bold text-gray-900">{{ nutzer.firstName }} {{ nutzer.lastName }}</span>
          ({{ nutzer.loginName }}, Primärrolle {{ ROLE_LABELS[nutzer.role] || nutzer.role }}) festlegen.
        </p>
      </div>

      <div v-if="availableRoles.length === 0" class="text-sm text-gray-500">
        Für diesen Nutzer können keine Zusatzrollen vergeben werden.
      </div>

      <div v-else class="space-y-2">
        <label v-for="role in availableRoles" :key="role"
               class="flex items-center gap-2 p-2 rounded-lg border border-gray-200 hover:bg-gray-50 cursor-pointer">
          <input type="checkbox" :value="role" v-model="selected" class="rounded text-indigo-600 focus:ring-indigo-500" />
          <span class="text-sm font-bold text-gray-800">{{ ROLE_LABELS[role] }}</span>
        </label>
      </div>

      <div class="bg-indigo-50 p-3 rounded-lg border border-indigo-100 mt-4">
        <p class="text-[10px] text-indigo-700 leading-relaxed">
          <InfoIcon class="w-3 h-3 inline mr-1" />
          Eine Zusatzrolle gibt sofortigen Zugriff auf das jeweilige Dashboard. Referent/Teilnehmer-Zusatzrollen
          können erst wieder entzogen werden, wenn keine eigenen Vorträge bzw. Prioritäten mehr zugeordnet sind.
        </p>
      </div>

      <div class="mt-8 flex gap-3">
        <button @click="$emit('close')" class="btn-secondary flex-1">Abbrechen</button>
        <button
          @click="confirmSave"
          :disabled="!hasChanges || isSubmitting"
          class="btn-primary flex-1 flex items-center justify-center gap-2"
        >
          <LoaderIcon v-if="isSubmitting" class="w-4 h-4 animate-spin" />
          Speichern
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue';
import { X as XIcon, Info as InfoIcon, Loader as LoaderIcon } from '@lucide/vue';
import { useAuthStore, ROLE_LABELS } from '../stores/auth';

const props = defineProps({
  isVisible: Boolean,
  nutzer: Object,
});

const emit = defineEmits(['close', 'save']);

const auth = useAuthStore();
const selected = ref([]);
const originalRoles = ref([]);
const isSubmitting = ref(false);

// Welche Zusatzrollen der AUFRUFENDE Nutzer überhaupt vergeben darf (siehe #751,
// OrganisatorService#validateGrantMatrix): ein Administrator darf TEILNEHMER/REFERENT/BETRACHTER
// vergeben, ein einfacher Organisator nur REFERENT. Wer als Ziel-Nutzer in Frage kommt (Admin ->
// Organisator/Administrator, Organisator -> sich selbst/Teilnehmer) prüft bereits serverseitig -
// dieser Dialog wird ohnehin nur für zulässige Ziel-Nutzer geöffnet (siehe OrganisatorenTab.vue/
// TeilnehmerTab.vue).
const availableRoles = computed(() => {
  if (auth.isAdministrator) return ['TEILNEHMER', 'REFERENT', 'BETRACHTER'];
  if (auth.isOrganisator) return ['REFERENT'];
  return [];
});

const hasChanges = computed(() =>
  selected.value.length !== originalRoles.value.length
  || selected.value.some((r) => !originalRoles.value.includes(r))
);

watch(() => props.isVisible, (visible) => {
  if (!visible) return;
  // Nur Rollen vorbelegen, die der Dialog ueberhaupt anzeigt - eine Zusatzrolle, die der aktuelle
  // Aufrufer nicht selbst vergeben/entziehen darf (z.B. TEILNEHMER bei einem Organisator-Aufrufer),
  // bleibt dadurch unangetastet statt beim Speichern versehentlich entzogen zu werden.
  const vorhandene = props.nutzer?.zusatzRollen ?? [];
  originalRoles.value = vorhandene.filter((r) => availableRoles.value.includes(r));
  selected.value = [...originalRoles.value];
});

const confirmSave = async () => {
  if (!hasChanges.value) return;
  const toGrant = selected.value.filter((r) => !originalRoles.value.includes(r));
  const toRevoke = originalRoles.value.filter((r) => !selected.value.includes(r));

  isSubmitting.value = true;
  try {
    await emit('save', { userId: props.nutzer.id, toGrant, toRevoke });
  } finally {
    isSubmitting.value = false;
  }
};
</script>

<style scoped>
@reference "tailwindcss";

.btn-primary {
  @apply rounded-lg bg-indigo-600 px-4 py-2 text-white font-bold hover:bg-indigo-700 transition shadow-sm disabled:opacity-50;
}
.btn-secondary {
  @apply bg-white text-gray-700 px-4 py-2 rounded-lg hover:bg-gray-50 font-bold border border-gray-200 transition shadow-sm;
}
.animate-fade-in { animation: fadeIn 0.2s ease-out; }
@keyframes fadeIn { from { opacity: 0; transform: scale(0.95); } to { opacity: 1; transform: scale(1); } }
</style>
