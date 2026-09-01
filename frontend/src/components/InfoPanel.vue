<template>
  <div class="w-72 bg-white text-gray-800 rounded-lg shadow-2xl border border-gray-200 p-4 z-50">
    <div class="flex items-center justify-between mb-3">
      <h3 class="font-bold text-sm">{{ info?.name || 'KonfPlan' }}</h3>
      <button @click="close" class="text-gray-400 hover:text-gray-600">✕</button>
    </div>

    <div v-if="loading" class="text-xs text-gray-500">Lade Informationen...</div>
    <div v-else-if="error" class="text-xs text-red-600">Fehler beim Laden der Anwendungsinformationen.</div>
    <dl v-else class="space-y-1 text-xs">
      <div class="flex justify-between gap-2">
        <dt class="text-gray-500">Version</dt>
        <dd class="font-mono">{{ info?.version || '—' }}</dd>
      </div>
      <div class="flex justify-between gap-2">
        <dt class="text-gray-500">Build-Datum</dt>
        <dd class="font-mono">{{ formattedBuildTime }}</dd>
      </div>
      <div class="flex justify-between gap-2">
        <dt class="text-gray-500">Git-Commit</dt>
        <dd class="font-mono">{{ shortCommit }}</dd>
      </div>
    </dl>

    <a href="mailto:konfplan@yahoo.com" class="block mt-3 text-xs text-indigo-600 hover:underline">
      konfplan@yahoo.com
    </a>

    <div class="mt-3 pt-3 border-t border-gray-200">
      <p class="text-[10px] uppercase font-bold text-gray-400 mb-1.5">Benutzerhandbücher</p>
      <div class="space-y-1">
        <a v-for="hb in sichtbareHandbuecher" :key="hb.datei"
           :href="`${apiBase}/handbuecher/${hb.datei}`" target="_blank"
           class="flex items-center gap-1.5 text-xs text-indigo-600 hover:underline">
          <FileTextIcon class="w-3.5 h-3.5 shrink-0"/> {{ hb.label }}
        </a>
      </div>
    </div>

    <div class="mt-3 pt-3 border-t border-gray-200">
      <button @click="emit('open-feedback')"
              class="flex items-center gap-1.5 text-xs text-indigo-600 hover:underline">
        <MessageSquarePlusIcon class="w-3.5 h-3.5 shrink-0"/>Was geht besser?
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { FileText as FileTextIcon, MessageSquarePlus as MessageSquarePlusIcon } from '@lucide/vue';
import api from '../api/axios';
import { useAuthStore } from '../stores/auth';

const auth = useAuthStore();

// In Produktion (Quinoa) teilen sich Frontend und Backend einen Origin - relative Links reichen.
// Im lokalen Dev-Betrieb laufen Frontend (:5173) und Backend (:9000) auf getrennten Origins,
// ein rein relativer Link würde sonst gegen den Vite-Dev-Server aufgelöst (SPA-Fallback statt PDF).
const apiBase = import.meta.env.VITE_API_URL || '';

const handbuecher = [
  { datei: 'Benutzerhandbuch-Admin.pdf', label: 'Für Administratoren' },
  { datei: 'Benutzerhandbuch-Referent.pdf', label: 'Für Referenten' },
  { datei: 'Benutzerhandbuch-Teilnehmer.pdf', label: 'Für Teilnehmer' },
];

// Teilnehmer und Referenten sehen nur ihr eigenes Handbuch; Admins (bzw. jede andere/unbekannte
// Rolle) sehen weiterhin alle drei.
const sichtbareHandbuecher = computed(() => {
  if (auth.isParticipant) return handbuecher.filter(hb => hb.datei === 'Benutzerhandbuch-Teilnehmer.pdf');
  if (auth.isSpeaker) return handbuecher.filter(hb => hb.datei === 'Benutzerhandbuch-Referent.pdf');
  return handbuecher;
});

const emit = defineEmits(['close', 'open-feedback']);

const info = ref(null);
const loading = ref(true);
const error = ref(false);

const close = () => emit('close');

const formattedBuildTime = computed(() => {
  if (!info.value?.buildTime) return '—';
  return new Date(info.value.buildTime).toLocaleString('de-DE');
});

const shortCommit = computed(() => {
  const commit = info.value?.gitCommit;
  return commit ? commit.substring(0, 7) : '—';
});

onMounted(async () => {
  try {
    const res = await api.get('/api/info');
    info.value = res.data;
  } catch (e) {
    console.error('Fehler beim Laden der Anwendungsinformationen:', e);
    error.value = true;
  } finally {
    loading.value = false;
  }
});
</script>
