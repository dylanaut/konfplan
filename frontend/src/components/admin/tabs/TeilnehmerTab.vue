<template>
  <section class="space-y-4 animate-fade-in">
    <div class="flex justify-between items-center bg-white p-3 rounded-xl border border-gray-100 shadow-sm">
      <div class="flex items-center gap-4">
        <h2 class="text-lg font-bold text-gray-800">Teilnehmer</h2>
        <!-- Batch-Aktionen -->
        <div v-if="selectedParticipantIds.length > 0"
             class="flex items-center gap-2 animate-fade-in bg-indigo-50 px-3 py-1 rounded-lg border border-indigo-100">
          <span class="text-[10px] font-bold text-indigo-600 uppercase">{{
              selectedParticipantIds.length
            }} ausgewählt:</span>
          <button @click="emit('batchDeactivateParticipants', selectedParticipantIds)"
                  class="text-xs text-orange-600 hover:underline font-bold">Deaktivieren
          </button>
          <button @click="emit('batchDeleteParticipants', selectedParticipantIds)"
                  class="text-xs text-red-600 hover:underline font-bold">Löschen
          </button>
          <button @click="emit('batchEmailParticipants', selectedParticipantIds)"
                  class="text-xs text-indigo-600 hover:underline font-bold flex items-center gap-1">
            <MailIcon class="w-3.5 h-3.5"/>
            E-Mail
          </button>
          <button @click="selectAllFilteredParticipants"
                  v-if="selectedParticipantIds.length < filteredParticipants.length"
                  class="text-[9px] bg-indigo-200 text-indigo-800 px-2 py-0.5 rounded hover:bg-indigo-300 ml-2 font-black">
            ALLE {{ filteredParticipants.length }} AUSWÄHLEN
          </button>
        </div>
      </div>
      <div class="flex gap-2">
        <select v-model="filters.participantGroup" class="input-field text-xs py-1 px-2">
          <option value="">Alle Gruppen</option>
          <option v-for="g in participantGroups" :key="g" :value="g">{{ g }}</option>
        </select>
        <input v-model="filters.teilnehmer" placeholder="Suchen..." class="input-field text-xs py-1 px-2"/>
        <button @click="emit('triggerUpload', `/api/veranstaltungen/${selectedVid}/teilnehmer/import`)"
                class="btn-secondary text-xs py-1 px-3">Import
        </button>
        <button @click="emit('openUserModal', {role: 'TEILNEHMER'})" class="btn-primary text-xs py-1 px-3">+ Neu
        </button>
      </div>
    </div>

    <!-- Prioritäten-Matrix (Collapsible) -->
    <div class="space-y-2">
      <div
          class="w-full flex items-center justify-between gap-3 text-[10px] font-black text-indigo-700 uppercase tracking-widest bg-white p-3 rounded-xl border border-gray-100 shadow-sm">
        <button @click="expandedSections.teilnehmerPrioritaeten = !expandedSections.teilnehmerPrioritaeten"
                class="flex items-center gap-3 hover:text-indigo-500">
          <ChevronDownIcon v-if="!expandedSections.teilnehmerPrioritaeten" class="w-3.5 h-3.5 shrink-0"/>
          <ChevronUpIcon v-else class="w-3.5 h-3.5 shrink-0"/>
          <div class="flex items-center gap-2">
            <StarIcon class="w-4 h-4"/>
            Wahl-Prioritäten verwalten
          </div>
        </button>
        <div class="flex items-center gap-2">
          <button @click.stop="emit('triggerUpload', `/api/admin/veranstaltungen/${selectedVid}/prioritaeten/import`)"
                  class="btn-secondary text-xs py-1 px-2 flex items-center gap-1">
            <UploadIcon class="w-3.5 h-3.5"/>
            Prios Import
          </button>
          <button v-if="changedPriorities.size > 0" @click="emit('saveAllParticipantPriorities')"
                  :disabled="isEventFinished" class="btn-save-all">
            <SaveAllIcon class="w-3.5 h-3.5"/>
            Alle Änderungen speichern
          </button>
        </div>
      </div>

      <div v-if="expandedSections.teilnehmerPrioritaeten" class="animate-fade-in space-y-4">
        <!-- Legende für Wahlvorträge -->
        <div v-if="electiveTalks.length > 0"
             class="bg-indigo-50/50 p-3 rounded-xl border border-indigo-100 text-[10px]">
          <h3 class="font-black text-indigo-900 uppercase mb-2 flex items-center gap-2">
            <InfoIcon class="w-3.5 h-3.5"/>
            Legende der Wahlvorträge
          </h3>
          <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-x-6 gap-y-1">
            <div v-for="(talk, index) in electiveTalks" :key="'legende-'+talk.id" class="flex gap-2 items-start">
              <span class="font-black text-indigo-600 shrink-0 w-4 text-right">{{ index + 1 }}:</span>
              <span class="text-gray-700 truncate"
                    :title="`${talk.referentName || 'N/A'}${talk.referentOrganisation ? ' [' + talk.referentOrganisation + ']' : ''}`">
                {{ talk.titel }}
              </span>
            </div>
          </div>
        </div>

        <div v-if="filteredParticipants.length > 0"
             class="bg-white shadow rounded-xl border border-gray-100 overflow-x-auto">
          <table class="min-w-full divide-y divide-gray-200 text-xs table-fixed">
            <thead class="bg-gray-50 text-[9px] uppercase font-bold text-gray-500">
            <tr>
              <th class="px-4 py-1.5 text-left font-bold sticky left-0 bg-gray-50 z-10 w-48 border-r border-gray-100">
                Name
              </th>
              <th v-for="(talk, index) in electiveTalks" :key="talk.id"
                  class="px-1 py-2 text-center text-[9px] font-black text-indigo-600 w-14 min-w-[56px] border-r border-gray-100"
                  :title="talk.titel">
                {{ index + 1 }}
              </th>
              <th class="w-auto"></th> <!-- Spacer column to prevent stretching -->
            </tr>
            </thead>
            <tbody class="divide-y divide-gray-100">
            <tr v-for="u in paginatedParticipants" :key="'prio-'+u.id" class="hover:bg-gray-50">
              <td class="px-4 py-2 font-bold sticky left-0 bg-white hover:bg-gray-50 z-10 border-r border-gray-100">
                <div class="flex items-center justify-between gap-2">
                  <span :class="isPrioChanged(u.id) ? 'text-orange-600' : 'text-gray-900'" class="truncate"
                        :title="u.lastName + ', ' + u.firstName">
                      {{ u.lastName }}, {{ u.firstName }}
                  </span>
                  <button v-if="isPrioChanged(u.id)"
                          @click="emit('saveParticipantPriorities', u.id)"
                          :disabled="isEventFinished"
                          class="bg-orange-600 hover:bg-orange-700 text-white text-[8px] px-1.5 py-0.5 rounded shadow-sm transition-all animate-fade-in flex items-center gap-1 shrink-0">
                    <SaveIcon class="w-3 h-3"/>
                    SAVE
                  </button>
                </div>
              </td>
              <td v-for="talk in electiveTalks" :key="'prio-'+u.id+'-'+talk.id"
                  class="px-1 py-1 text-center border-r border-gray-50">
                <input type="number" min="0" max="10"
                       v-model.number="getParticipantPrio(u.id, talk.id).prioWert"
                       @input="markPrioChanged(u.id)"
                       :disabled="isEventFinished"
                       class="w-12 text-center border rounded py-0.5 text-[10px] focus:ring-indigo-500 focus:border-indigo-500 border-gray-100"/>
              </td>
              <td></td>
            </tr>
            </tbody>
          </table>
          <PaginationControls v-model:currentPage="pages.teilnehmer" :totalItems="filteredParticipants.length"
                              :pageSize="pageSize"/>
        </div>
      </div>
    </div>

    <!-- Verfügbarkeits-Matrix (Collapsible) -->
    <div class="space-y-2">
      <div
          class="w-full flex items-center justify-between gap-3 text-[10px] font-black text-indigo-700 uppercase tracking-widest bg-white p-3 rounded-xl border border-gray-100 shadow-sm">
        <button @click="expandedSections.teilnehmerVerfuegbarkeit = !expandedSections.teilnehmerVerfuegbarkeit"
                class="flex items-center gap-3 hover:text-indigo-500">
          <ChevronDownIcon v-if="!expandedSections.teilnehmerVerfuegbarkeit" class="w-3.5 h-3.5 shrink-0"/>
          <ChevronUpIcon v-else class="w-3.5 h-3.5 shrink-0"/>
          <div class="flex items-center gap-2">
            <CheckSquareIcon class="w-4 h-4"/>
            Verfügbarkeiten verwalten
          </div>
        </button>
        <button v-if="hasDirtyAvailabilities" @click="emit('saveAllAvailabilities')" :disabled="isEventFinished"
                class="btn-save-all">
          <SaveAllIcon class="w-3.5 h-3.5"/>
          Alle Änderungen speichern
        </button>
      </div>

      <div v-if="expandedSections.teilnehmerVerfuegbarkeit" class="animate-fade-in">
        <div v-if="filteredParticipants.length > 0"
             class="bg-white shadow rounded-xl overflow-hidden border border-gray-100">
          <table class="min-w-full divide-y divide-gray-200 text-xs">
            <thead class="bg-gray-50 text-[9px] uppercase font-bold text-gray-500">
            <tr>
              <th class="px-4 py-1.5 text-left w-10">
                <input type="checkbox" :checked="isAllOnPageSelected" @change="toggleSelectAllPage"
                       class="rounded text-indigo-600 focus:ring-indigo-500 h-3 w-3"/>
              </th>
              <th @click="toggleSort('teilnehmer', 'lastName')"
                  class="px-4 py-1.5 text-left cursor-pointer hover:text-indigo-600 transition font-bold">Name
                <ArrowUpDownIcon class="w-3 h-3 inline ml-0.5"/>
              </th>
              <th @click="toggleSort('teilnehmer', 'gruppe')"
                  class="px-4 py-1.5 text-left cursor-pointer hover:text-indigo-600 transition font-bold">Gruppe
                <ArrowUpDownIcon class="w-3 h-3 inline ml-0.5"/>
              </th>
              <th @click="toggleSort('teilnehmer', 'isActive')"
                  class="px-4 py-1.5 text-center cursor-pointer hover:text-indigo-600 transition font-bold">Aktiv
                <ArrowUpDownIcon class="w-3 h-3 inline ml-0.5"/>
              </th>
              <th v-for="slot in sortedSlots" :key="slot.id"
                  class="px-2 py-2 text-center text-[8px] font-bold text-gray-500">
                {{ formatTime(slot.startTime) }}
              </th>
              <th class="px-4 py-1.5 text-center font-bold">Plan</th> <!-- New column for Plan icon -->
              <th class="px-4 py-1.5 text-right font-bold">Aktionen</th>
            </tr>
            </thead>
            <tbody class="divide-y divide-gray-100">
            <tr v-for="u in paginatedParticipants" :key="u.id"
                :class="['hover:bg-gray-50', selectedParticipantIds.includes(u.id) ? 'bg-indigo-50/50' : '', isAvailabilityChanged(u.id) ? 'bg-orange-50/50' : '']">
              <td class="px-4 py-2">
                <input type="checkbox" :value="u.id" v-model="selectedParticipantIds"
                       class="rounded text-indigo-600 focus:ring-indigo-500 h-3 w-3"/>
              </td>
              <td class="px-4 py-2 font-bold" :title="u.email">{{ u.lastName }}, {{ u.firstName }}</td>
              <td class="px-4 py-2 text-gray-500">{{ u.gruppe }}</td>
              <td class="px-4 py-2 text-center">
                <div @click="emit('toggleParticipantActive', u)" class="cursor-pointer">
                  <CheckIcon v-if="u.isActive" class="w-4 h-4 text-green-500 mx-auto"/>
                  <XIcon v-else class="w-4 h-4 text-red-500 mx-auto"/>
                </div>
              </td>
              <td v-for="slot in sortedSlots" :key="slot.id" class="px-2 py-2 text-center">
                <input type="checkbox" :checked="isAvailable(u.id, slot.id)"
                       @change="emit('toggleAvailability', u.id, slot.id, $event.target.checked)"
                       :disabled="isEventFinished" class="rounded text-indigo-600 focus:ring-indigo-500 h-3 w-3"/>
              </td>
              <td class="px-4 py-2 text-center">
                <button @click="openParticipantPlan(u)" class="text-indigo-600 hover:text-indigo-800" title="Belegungsplan anzeigen">
                  <FileTextIcon class="w-4 h-4 inline"/>
                </button>
              </td>
              <td class="px-4 py-2 text-right">
                <button @click="emit('openInviteModal', u)" class="text-indigo-600 ml-3" title="Einladen">
                  <MailIcon class="w-3.5 h-3.5 inline"/>
                </button>
                <button @click="emit('openUserModal', u)" class="text-indigo-600 ml-3" title="Bearbeiten">
                  <PencilIcon class="w-3.5 h-3.5 inline"/>
                </button>
                <button @click="emit('deleteUser', u.id)" class="text-red-600 ml-3">
                  <Trash2Icon class="w-3.5 h-3.5 inline"/>
                </button>
              </td>
            </tr>
            </tbody>
          </table>
          <PaginationControls v-model:currentPage="pages.teilnehmer" :totalItems="filteredParticipants.length"
                              :pageSize="pageSize"/>
        </div>
        <div v-else class="bg-white p-8 rounded-xl text-center border-2 border-dashed border-gray-200 text-gray-500">
          <UsersIcon class="w-10 h-10 mx-auto mb-3 text-gray-400"/>
          <p class="font-bold">Bitte Teilnehmer erfassen.</p>
        </div>
      </div>
    </div>

    <!-- HTML Display Modal -->
    <HtmlDisplayModal :isVisible="showPlanOverlay" :htmlContent="planHtmlContent" :title="planOverlayTitle" @close="showPlanOverlay = false" />
  </section>
</template>

<script setup>
import {computed, reactive, ref, watch} from 'vue';
import {
  ArrowUpDown as ArrowUpDownIcon,
  Check as CheckIcon,
  CheckSquare as CheckSquareIcon,
  ChevronDown as ChevronDownIcon,
  ChevronUp as ChevronUpIcon,
  Info as InfoIcon,
  Mail as MailIcon,
  Pencil as PencilIcon,
  Save as SaveIcon,
  SaveAll as SaveAllIcon,
  Star as StarIcon,
  Trash2 as Trash2Icon,
  Upload as UploadIcon,
  Users as UsersIcon,
  X as XIcon,
  FileText as FileTextIcon // Import the FileTextIcon
} from 'lucide-vue-next';
import PaginationControls from '../../PaginationControls.vue';
import HtmlDisplayModal from '../../HtmlDisplayModal.vue'; // Import the new modal component
import api from '../../../api/axios'; // Import axios for API calls

const props = defineProps({
  teilnehmer: Array,
  selectedVid: Number,
  pageSize: Number,
  sortedSlots: Array,
  verfuegbarkeiten: Array,
  isEventFinished: Boolean,
  electiveTalks: Array,
  participantPriorities: Object,
  changedPriorities: Set,
  changedAvailabilities: Set,
  hasDirtyAvailabilities: Boolean,
});

const emit = defineEmits([
  'triggerUpload', 'openUserModal', 'deleteUser', 'toggleParticipantActive',
  'batchDeactivateParticipants', 'batchDeleteParticipants', 'batchEmailParticipants',
  'openInviteModal', 'toggleAvailability', 'saveParticipantPriorities',
  'saveAllAvailabilities', 'saveAllParticipantPriorities'
]);

const pages = reactive({
  teilnehmer: 1
});

const filters = reactive({
  teilnehmer: '',
  participantGroup: ''
});

const sorts = reactive({
  teilnehmer: {key: 'lastName', dir: 'asc'}
});

const expandedSections = reactive({
  teilnehmerVerfuegbarkeit: false,
  teilnehmerPrioritaeten: true
});

const selectedParticipantIds = ref([]);

// State for the new plan overlay
const showPlanOverlay = ref(false);
const planHtmlContent = ref('');
const planOverlayTitle = ref('');

watch(() => filters.teilnehmer, () => {
  pages.teilnehmer = 1;
});
watch(() => filters.participantGroup, () => {
  pages.teilnehmer = 1;
  selectedParticipantIds.value = [];
});

const participantGroups = computed(() => {
  const groups = new Set(props.teilnehmer.map(t => t.gruppe).filter(Boolean));
  return Array.from(groups).sort();
});

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

const filteredParticipants = computed(() => {
  let list = [...props.teilnehmer];
  if (filters.participantGroup) {
    list = list.filter(t => t.gruppe === filters.participantGroup);
  }
  return processList(list, filters.teilnehmer, sorts.teilnehmer);
});
const paginatedParticipants = computed(() => paginate(filteredParticipants.value, pages.teilnehmer));

const isAllOnPageSelected = computed(() => {
  if (paginatedParticipants.value.length === 0) return false;
  return paginatedParticipants.value.every(p => selectedParticipantIds.value.includes(p.id));
});

const toggleSelectAllPage = () => {
  if (isAllOnPageSelected.value) {
    const pageIds = paginatedParticipants.value.map(p => p.id);
    selectedParticipantIds.value = selectedParticipantIds.value.filter(id => !pageIds.includes(id));
  } else {
    const pageIds = paginatedParticipants.value.map(p => p.id);
    const newIds = pageIds.filter(id => !selectedParticipantIds.value.includes(id));
    selectedParticipantIds.value.push(...newIds);
  }
};

const selectAllFilteredParticipants = () => {
  selectedParticipantIds.value = filteredParticipants.value.map(p => p.id);
};

const isAvailable = (userId, slotId) => {
  return props.verfuegbarkeiten.some(v => v.userId === userId && v.slotId === slotId && v.isAvailable);
};

const isAvailabilityChanged = (userId) => {
  for (const key of props.changedAvailabilities) {
    if (key.startsWith(`${userId}-`)) {
      return true;
    }
  }
  return false;
};

const getParticipantPrio = (userId, talkId) => {
  if (!props.participantPriorities[userId]) props.participantPriorities[userId] = {};
  if (!props.participantPriorities[userId][talkId]) props.participantPriorities[userId][talkId] = {prioWert: 0};
  return props.participantPriorities[userId][talkId];
};

const markPrioChanged = (userId) => {
  props.changedPriorities.add(userId);
};

const isPrioChanged = (userId) => props.changedPriorities.has(userId);

const formatTime = (t) => t ? new Date(t).toLocaleTimeString('de-DE', {hour: '2-digit', minute: '2-digit'}) : '';

// New method to open participant plan
const openParticipantPlan = async (participant) => {
  if (!props.selectedVid) {
    alert('Bitte zuerst eine Veranstaltung auswählen.');
    return;
  }
  planOverlayTitle.value = `Belegungsplan für ${participant.firstName} ${participant.lastName}`;
  try {
    const response = await api.get(`/api/reports/${props.selectedVid}/teilnehmer/${participant.id}/laufzettel`, {
      headers: { 'Accept': 'text/html' }
    });
    planHtmlContent.value = response.data;
    showPlanOverlay.value = true;
  } catch (error) {
    console.error('Fehler beim Laden des Belegungsplans:', error);
    planHtmlContent.value = `<p class="text-red-500">Fehler beim Laden des Belegungsplans. Möglicherweise wurde noch kein Plan berechnet oder es ist ein Serverfehler aufgetreten.</p>`;
    showPlanOverlay.value = true;
  }
};
</script>

<style scoped>
.btn-primary {
  @apply rounded-lg bg-indigo-600 px-3 py-1.5 text-white font-bold hover:bg-indigo-700 transition shadow-sm border-none cursor-pointer disabled:opacity-50;
}

.btn-secondary {
  @apply bg-white text-gray-700 px-3 py-1.5 rounded-lg hover:bg-gray-50 font-bold border border-gray-200 transition shadow-sm cursor-pointer disabled:opacity-50;
}

.btn-save-all {
  @apply bg-orange-500 text-white text-[10px] px-3 py-1 rounded-md shadow-sm transition-all flex items-center gap-2 hover:bg-orange-600 disabled:opacity-50;
}

.input-field {
  @apply rounded-lg border border-gray-300 px-2 py-1 text-gray-900 focus:ring-2 focus:ring-indigo-500 bg-white;
}

.animate-fade-in {
  animation: fadeIn 0.3s ease-in-out;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(5px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>