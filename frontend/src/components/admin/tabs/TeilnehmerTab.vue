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
        <select v-model="filters.gruppen" class="input-field text-xs py-1 px-2 pr-8">
          <option value="">Alle Gruppen</option>
          <option v-for="g in teilnehmerGruppen" :key="g" :value="g">{{ g }}</option>
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
        @click="togglePrioritaetenAnzeige"
        class="w-full flex items-center justify-between gap-3 text-[10px] font-black text-indigo-700 uppercase tracking-widest bg-white p-3 rounded-xl border border-gray-100 shadow-sm cursor-pointer hover:bg-gray-50 transition">
        <div class="flex items-center gap-3">
          <ChevronDownIcon v-if="!showPrioritaetenBlock" class="w-3.5 h-3.5 shrink-0"/>
          <ChevronUpIcon v-else class="w-3.5 h-3.5 shrink-0"/>
          <div class="flex items-center gap-2">
            <StarIcon class="w-4 h-4"/>
            Wahl-Prioritäten verwalten
          </div>
        </div>
        <div class="flex items-center gap-2" @click.stop>
          <button @click="emit('triggerUpload', `/api/admin/veranstaltungen/${selectedVid}/prioritaeten/import`)"
                  class="btn-secondary text-xs py-1 px-2 flex items-center gap-1">
            <UploadIcon class="w-3.5 h-3.5"/>
            Prio Import
          </button>
          <button v-if="changedPriorities.size > 0" @click="emit('saveAllParticipantPriorities')"
                  :disabled="isEventFinished" class="btn-save-all">
            <SaveAllIcon class="w-3.5 h-3.5"/>
            Alle Änderungen speichern
          </button>
        </div>
      </div>

      <div v-show="showPrioritaetenBlock" class="space-y-4">
        <!-- Legende für Wahlvorträge -->
        <div v-if="sortedWahlvortraege.length > 0"
             class="bg-indigo-50/50 p-3 rounded-xl border border-indigo-100 text-[10px]">
          <h3 class="font-black text-indigo-900 uppercase mb-2 flex items-center gap-2">
            <InfoIcon class="w-3.5 h-3.5"/>
            Legende der Wahlvorträge
          </h3>
          <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-x-6 gap-y-2">
            <div v-for="group in groupedWahlvortraege" :key="group.berufsfeld">
              <h4 class="font-bold text-indigo-800 uppercase text-[9px] mb-1">{{ group.berufsfeld }}</h4>
              <div class="space-y-1">
                <div v-for="vortrag in group.vortraege" :key="'legende-'+vortrag.id" class="flex gap-2 items-start">
                  <span class="font-black text-indigo-600 shrink-0 w-4 text-right">{{
                      sortedWahlvortraege.indexOf(vortrag) + 1
                    }}:</span>
                  <span class="text-gray-700 truncate"
                        :title="`${vortrag.referentName || 'N/A'}${vortrag.referentOrganisation ? ' [' + vortrag.referentOrganisation + ']' : ''}`">
                    {{ vortrag.titel }}
                  </span>
                </div>
              </div>
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
              <th v-for="(vortrag, index) in sortedWahlvortraege" :key="vortrag.id"
                  class="px-1 py-2 text-center text-[9px] font-black text-indigo-600 w-14 min-w-[56px] border-r border-gray-100"
                  :title="vortrag.titel">
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
                        :title="u.firstName + ' ' + u.lastName">
                      {{ u.firstName }} {{ u.lastName }}
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
              <td v-for="vortrag in sortedWahlvortraege" :key="'prio-'+u.id+'-'+vortrag.id"
                  class="px-1 py-1 text-center border-r border-gray-50">
                <input type="number" min="0" max="10"
                       v-model.number="getParticipantPrio(u.id, vortrag.id).prioWert"
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
        @click="toggleVerfuegbarkeitenAnzeige"
        class="w-full flex items-center justify-between gap-3 text-[10px] font-black text-indigo-700 uppercase tracking-widest bg-white p-3 rounded-xl border border-gray-100 shadow-sm cursor-pointer hover:bg-gray-50 transition">
        <div class="flex items-center gap-3">
          <ChevronDownIcon v-if="!showVerfuegbarkeitenBlock" class="w-3.5 h-3.5 shrink-0"/>
          <ChevronUpIcon v-else class="w-3.5 h-3.5 shrink-0"/>
          <div class="flex items-center gap-2">
            <CheckSquareIcon class="w-4 h-4"/>
            Verfügbarkeiten verwalten
          </div>
        </div>
        <button v-if="availabilityStore.hasDirtyAvailabilities()" @click.stop="availabilityStore.saveAvailabilities(selectedVid)"
                :disabled="isEventFinished"
                class="btn-save-all">
          <SaveAllIcon class="w-3.5 h-3.5"/>
          Alle Änderungen speichern
        </button>
      </div>

      <div v-show="showVerfuegbarkeitenBlock">
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
              <th @click="toggleSort('teilnehmer', 'gruppen')"
                  class="px-4 py-1.5 text-left cursor-pointer hover:text-indigo-600 transition font-bold">Gruppen
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
              <th v-if="planErstellt" class="px-4 py-1.5 text-center font-bold">Plan</th>
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
              <td class="px-4 py-2 font-bold" :title="u.email">{{ u.firstName }} {{ u.lastName }}</td>
              <td class="px-4 py-2 text-gray-500">{{ (u.gruppen || []).slice().sort().join(', ') }}</td>
              <td class="px-4 py-2 text-center">
                <div @click="emit('toggleParticipantActive', u)" class="cursor-pointer">
                  <CheckIcon v-if="u.isActive" class="w-4 h-4 text-green-500 mx-auto"/>
                  <XIcon v-else class="w-4 h-4 text-red-500 mx-auto"/>
                </div>
              </td>
              <td v-for="slot in sortedSlots" :key="slot.id" class="px-2 py-2 text-center">
                <input type="checkbox" :checked="availabilityStore.isUserAvailable(u.id, slot.id)"
                       @change="availabilityStore.toggleUserAvailability(u.id, slot.id)"
                       :disabled="isEventFinished" class="rounded text-indigo-600 focus:ring-indigo-500 h-3 w-3"/>
              </td>
              <td v-if="planErstellt" class="px-4 py-2 text-center">
                <button @click="openParticipantPlan(u)" class="text-indigo-600 hover:text-indigo-800"
                        title="Belegungsplan anzeigen">
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
  </section>
</template>

<script setup>
import {computed, reactive, ref, watch} from 'vue';
import {useRouter} from 'vue-router';
import {
  ArrowUpDown as ArrowUpDownIcon,
  Check as CheckIcon,
  CheckSquare as CheckSquareIcon,
  ChevronDown as ChevronDownIcon,
  ChevronUp as ChevronUpIcon,
  FileText as FileTextIcon,
  Info as InfoIcon,
  Mail as MailIcon,
  Pencil as PencilIcon,
  Save as SaveIcon,
  SaveAll as SaveAllIcon,
  Star as StarIcon,
  Trash2 as Trash2Icon,
  Upload as UploadIcon,
  UsersIcon as UsersIcon,
  X as XIcon
} from '@lucide/vue';
import PaginationControls from '../../PaginationControls.vue';
import {useAvailabilityStore} from '../../../stores/availability';

const props = defineProps({
  teilnehmer: Array,
  selectedVid: Number,
  pageSize: Number,
  sortedSlots: Array,
  isEventFinished: Boolean,
  wahlvortraege: Array,
  participantPriorities: Object,
  changedPriorities: Set,
  planErstellt: Boolean,
});

const emit = defineEmits([
  'triggerUpload', 'openUserModal', 'deleteUser', 'toggleParticipantActive',
  'batchDeactivateParticipants', 'batchDeleteParticipants', 'batchEmailParticipants',
  'openInviteModal', 'saveParticipantPriorities',
  'saveAllParticipantPriorities'
]);

const availabilityStore = useAvailabilityStore();
const router = useRouter();

const pages = reactive({
  teilnehmer: 1
});

const filters = reactive({
  teilnehmer: '',
  gruppen: ''
});

const sorts = reactive({
  teilnehmer: {key: 'lastName', dir: 'asc'}
});

const   showVerfuegbarkeitenBlock = ref(false);
const showPrioritaetenBlock = ref(true);

const toggleVerfuegbarkeitenAnzeige = () => {
  console.log('[Toggle] Verfügbarkeit vorher:', showVerfuegbarkeitenBlock.value);
  showVerfuegbarkeitenBlock.value = !showVerfuegbarkeitenBlock.value;
  console.log('[Toggle] Verfügbarkeit nachher:', showVerfuegbarkeitenBlock.value);
};
const togglePrioritaetenAnzeige = () => {
  console.log('[Toggle] Prioritäten vorher:', showPrioritaetenBlock.value);
  showPrioritaetenBlock.value = !showPrioritaetenBlock.value;
  console.log('[Toggle] Prioritäten nachher:', showPrioritaetenBlock.value);
};

const selectedParticipantIds = ref([]);

watch(() => props.selectedVid, (newVid) => {
  if (newVid) {
    availabilityStore.fetchAvailabilities(newVid);
  }
}, {immediate: true});

watch(() => filters.teilnehmer, () => {
  pages.teilnehmer = 1;
});
watch(() => filters.gruppen, () => {
  pages.teilnehmer = 1;
  selectedParticipantIds.value = [];
});

const teilnehmerGruppen = computed(() => {
  const groups = new Set(props.teilnehmer.flatMap(t => t.gruppen || []).filter(Boolean));
  return Array.from(groups).sort();
});

const sortedWahlvortraege = computed(() => {
  return [...props.wahlvortraege].sort((a, b) => {
    const berufsfeldA = a.berufsfeld || 'Sonstige';
    const berufsfeldB = b.berufsfeld || 'Sonstige';
    if (berufsfeldA < berufsfeldB) return -1;
    if (berufsfeldA > berufsfeldB) return 1;
    return a.titel.localeCompare(b.titel);
  });
});

const groupedWahlvortraege = computed(() => {
  const grouped = sortedWahlvortraege.value.reduce((acc, vortrag) => {
    const key = vortrag.berufsfeld || 'Sonstige';
    if (!acc[key]) {
      acc[key] = [];
    }
    acc[key].push(vortrag);
    return acc;
  }, {});

  return Object.keys(grouped).sort().map(key => ({
    berufsfeld: key,
    vortraege: grouped[key]
  }));
});

const processList = (list, filterText, sortConfig) => {
  let result = [...list];

  if (filterText) {
    const f = filterText.toLowerCase();
    result = result.filter(item => {
      // Safely build a search string from the item's properties
      const searchString = Object.values(item).map(value => {
        if (value === null || typeof value === 'undefined') {
          return '';
        }
        if (typeof value === 'object') {
          // For nested objects or arrays, just join their values, ignoring deeper levels for simplicity
          return Object.values(value).join(' ');
        }
        return String(value);
      }).join(' ').toLowerCase();
      return searchString.includes(f);
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
  let list = props.teilnehmer.filter(t => t && t.veranstaltungIds && Array.isArray(t.veranstaltungIds) && t.veranstaltungIds.includes(props.selectedVid));
  if (filters.gruppen) {
    list = list.filter(t => (t.gruppen || []).includes(filters.gruppen));
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

const isAvailabilityChanged = (userId) => {
  return availabilityStore.isUserAvailabilityChanged(userId);
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

const openParticipantPlan = (participant) => {
  if (!props.selectedVid) {
    alert('Bitte zuerst eine Veranstaltung auswählen.');
    return;
  }
  router.push({ name: 'LaufzettelTeilnehmer', params: { vid: props.selectedVid, tid: participant.id } });
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
