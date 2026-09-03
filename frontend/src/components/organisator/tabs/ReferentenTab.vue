<template>
  <section class="space-y-4 animate-fade-in">
    <div class="flex justify-between items-center bg-white p-3 rounded-xl border border-gray-100 shadow-sm">
      <h2 class="text-lg font-bold text-gray-800">Referenten und Verfügbarkeiten</h2>
      <div class="flex gap-2">
        <input v-model="filters.referenten" placeholder="Suchen..." class="input-field text-xs py-1 px-2"/>
        <button @click="emit('triggerUpload', `/api/veranstaltungen/${selectedVid}/referenten/import`)"
                class="btn-secondary text-xs py-1 px-3">Import
        </button>
        <button @click="emit('triggerUpload', `/api/organisator/veranstaltungen/${selectedVid}/referenten/verfuegbarkeiten/import`)"
                class="btn-secondary text-xs py-1 px-3 flex items-center gap-1">
          <UploadIcon class="w-3.5 h-3.5"/>
          Verfügbarkeiten Import
        </button>
        <button v-if="availabilityStore.hasDirtyAvailabilities()" @click="availabilityStore.saveAvailabilities(selectedVid)"
                :disabled="isEventFinished"
                class="btn-save-all">
          <SaveAllIcon class="w-3.5 h-3.5"/>
          Alle Änderungen speichern
        </button>
        <button @click="openMailToAll" :disabled="referentsWithEmail.length === 0"
                title="Öffnet dein Mailprogramm mit allen Referenten dieser Veranstaltung in BCC"
                class="btn-secondary text-xs py-1 px-3 flex items-center gap-1">
          <MailIcon class="w-3.5 h-3.5"/>
          Alle per Mail ({{ referentsWithEmail.length }})
        </button>
        <button @click="emit('openUserModal', {role: 'REFERENT'})" class="btn-primary text-xs py-1 px-3">+ Neu</button>
      </div>
    </div>
    <div v-if="filteredSpeakers.length > 0" class="bg-white shadow rounded-xl overflow-hidden border border-gray-100">
      <table class="min-w-full divide-y divide-gray-200 text-xs">
        <thead class="bg-gray-50 text-[9px] uppercase font-bold text-gray-500">
        <tr>
          <th @click="toggleSort('referenten', 'lastName')" class="px-4 py-1.5 text-left cursor-pointer hover:text-indigo-600 transition font-bold">Name <ArrowUpDownIcon class="w-3 h-3 inline ml-0.5"/></th>
          <th @click="toggleSort('referenten', 'organisation')" class="px-4 py-1.5 text-left cursor-pointer hover:text-indigo-600 transition font-bold">Organisation <ArrowUpDownIcon class="w-3 h-3 inline ml-0.5"/></th>
          <th v-for="slot in sortedSlots" :key="slot.id" class="px-2 py-2 text-center text-[8px] font-bold text-gray-500">
            {{ formatTime(slot.startTime) }}
          </th>
          <th v-if="planErstellt" class="px-4 py-1.5 text-center font-bold">Plan</th>
          <th class="px-4 py-1.5 text-right font-bold">Aktionen</th>
        </tr>
        </thead>
        <tbody class="divide-y divide-gray-100">
        <tr v-for="u in paginatedSpeakers" :key="u.id" class="hover:bg-gray-50">
          <td class="px-4 py-2 font-bold" :title="u.email || ''">
            {{ u.firstName }} {{ u.lastName }}
            <span class="block font-normal text-gray-400">{{ u.loginName }}</span>
          </td>
          <td class="px-4 py-2 text-gray-500">{{ u.organisation }}</td>
          <td v-for="slot in sortedSlots" :key="slot.id" class="px-2 py-2 text-center">
            <input type="checkbox" :checked="availabilityStore.isUserAvailable(u.id, slot.id)" @change="availabilityStore.toggleUserAvailability(u.id, slot.id)" :disabled="isEventFinished" class="rounded text-indigo-600 focus:ring-indigo-500 h-3 w-3" />
          </td>
          <td v-if="planErstellt" class="px-4 py-2 text-center">
            <button @click="openReferentPlan(u)" class="text-indigo-600 hover:text-indigo-800" title="Belegungsplan anzeigen" aria-label="Belegungsplan anzeigen">
              <FileTextIcon class="w-4 h-4 inline"/>
            </button>
          </td>
          <td class="px-4 py-2 text-right">
            <button @click="emit('openUserModal', u)" class="text-indigo-600 ml-3" title="Bearbeiten" aria-label="Bearbeiten">
              <PencilIcon class="w-3.5 h-3.5 inline"/>
            </button>
            <button @click="emit('openInviteModal', u)" class="text-indigo-600 ml-3" title="Einladen" aria-label="Einladen">
              <MailIcon class="w-3.5 h-3.5 inline"/>
            </button>
            <button @click="emit('deleteUser', u.id)" class="text-red-600 ml-3" title="Löschen" aria-label="Löschen">
              <Trash2Icon class="w-3.5 h-3.5 inline"/>
            </button>
          </td>
        </tr>
        </tbody>
      </table>
      <PaginationControls v-model:currentPage="pages.referenten" :totalItems="filteredSpeakers.length" :pageSize="pageSize"/>
    </div>
    <div v-else class="bg-white p-8 rounded-xl text-center border-2 border-dashed border-gray-200 text-gray-500">
      <UserIcon class="w-10 h-10 mx-auto mb-3 text-gray-400" />
      <p class="font-bold">Bitte Referenten erfassen.</p>
    </div>
  </section>
</template>

<script setup>
import { computed, reactive, watch } from 'vue';
import { useRouter } from 'vue-router';
import {
  ArrowUpDown as ArrowUpDownIcon,
  FileText as FileTextIcon,
  Mail as MailIcon,
  Pencil as PencilIcon,
  SaveAll as SaveAllIcon,
  Trash2 as Trash2Icon,
  Upload as UploadIcon,
  User as UserIcon
} from '@lucide/vue';
import PaginationControls from '../../PaginationControls.vue';
import { useAvailabilityStore } from '../../../stores/availability';

const props = defineProps({
  referenten: Array,
  selectedVid: Number,
  pageSize: Number,
  sortedSlots: Array,
  isEventFinished: Boolean,
  planErstellt: Boolean
});

const emit = defineEmits(['triggerUpload', 'openUserModal', 'deleteUser', 'openInviteModal']);

const availabilityStore = useAvailabilityStore();
const router = useRouter();

const pages = reactive({
  referenten: 1
});

const filters = reactive({
  referenten: ''
});

const sorts = reactive({
  referenten: { key: 'lastName', dir: 'asc' }
});

watch(() => filters.referenten, () => { pages.referenten = 1; });

const processList = (list, filterText, sortConfig) => {
  let result = [...list];
  if (filterText) {
    const f = filterText.toLowerCase();
    result = result.filter(item => {
      const searchStrings = Object.values(item).map(v => v && typeof v === 'object' ? Object.values(v) : v).flat();
      return searchStrings.some(val => val && String(val).toLowerCase().includes(f));
    });
  }
  result.sort((a, b) => {
    const valA = a[sortConfig.key] || '';
    const valB = b[sortConfig.key] || '';
    if (typeof valA === 'number' && typeof valB === 'number') {
      return sortConfig.dir === 'asc' ? valA - valB : valB - valA;
    }
    const cmp = String(valA).localeCompare(String(valB));
    return sortConfig.dir === 'asc' ? cmp : -cmp;
  });
  return result;
};

const paginate = (list, page) => {
  const start = (page - 1) * props.pageSize;
  return list.slice(start, start + props.pageSize);
};

const toggleSort = (key, field) => {
  if (sorts[key].key === field) {
    sorts[key].dir = sorts[key].dir === 'asc' ? 'desc' : 'asc';
  } else {
    sorts[key].key = field;
    sorts[key].dir = 'asc';
  }
};

const speakersForEvent = computed(() =>
  props.referenten.filter(r => r && r.veranstaltungIds && Array.isArray(r.veranstaltungIds) && r.veranstaltungIds.includes(props.selectedVid))
);
const filteredSpeakers = computed(() => processList(speakersForEvent.value, filters.referenten, sorts.referenten));
const paginatedSpeakers = computed(() => paginate(filteredSpeakers.value, pages.referenten));

// Unabhaengig vom Suchfeld oben - "alle Referenten der Veranstaltung" meint alle, nicht nur die
// aktuell gefilterte Tabellenansicht.
const referentsWithEmail = computed(() => speakersForEvent.value.filter(r => r.email));

const openMailToAll = () => {
  if (referentsWithEmail.value.length === 0) return;
  const bcc = referentsWithEmail.value.map(r => encodeURIComponent(r.email)).join(',');
  window.location.href = `mailto:?bcc=${bcc}`;
};

const formatTime = (t) => t ? new Date(t).toLocaleTimeString('de-DE', {hour: '2-digit', minute: '2-digit'}) : '';

const openReferentPlan = (referent) => {
  if (!props.selectedVid) {
    alert('Bitte zuerst eine Veranstaltung auswählen.');
    return;
  }
  router.push({ name: 'LaufzettelReferent', params: { vid: props.selectedVid, rid: referent.id } });
};
</script>

<style scoped>
@reference "tailwindcss";

.btn-primary { @apply rounded-lg bg-indigo-600 px-3 py-1.5 text-white font-bold hover:bg-indigo-700 transition shadow-sm border-none cursor-pointer disabled:opacity-50; }
.btn-secondary { @apply bg-white text-gray-700 px-3 py-1.5 rounded-lg hover:bg-gray-50 font-bold border border-gray-200 transition shadow-sm cursor-pointer disabled:opacity-50; }
.btn-save-all { @apply bg-orange-500 text-white text-[10px] px-3 py-1 rounded-md shadow-sm transition-all flex items-center gap-2 hover:bg-orange-600 disabled:opacity-50; }
.input-field { @apply rounded-lg border border-gray-300 px-2 py-1 text-gray-900 focus:ring-2 focus:ring-indigo-500 bg-white; }
.animate-fade-in { animation: fadeIn 0.3s ease-in-out; }
@keyframes fadeIn { from { opacity: 0; transform: translateY(5px); } to { opacity: 1; transform: translateY(0); } }
</style>