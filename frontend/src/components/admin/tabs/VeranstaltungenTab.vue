<template>
  <section class="space-y-4">
    <div class="flex justify-between items-center bg-white p-3 rounded-xl border border-gray-100 shadow-sm">
      <h2 class="text-lg font-bold text-gray-800">Veranstaltungen</h2>
      <div class="flex gap-2">
        <input v-model="filters.veranstaltungen" placeholder="Suchen..." class="input-field text-xs py-1 px-2"/>
        <button @click="emit('triggerUpload', '/api/veranstaltungen/import')"
                :disabled="!canImportVeranstaltung"
                :class="{'opacity-50 cursor-not-allowed': !canImportVeranstaltung}"
                class="btn-secondary flex items-center gap-2 text-xs py-1 px-3">
          <UploadIcon class="w-3.5 h-3.5"/>
          Import
        </button>
        <button @click="emit('openVeranstaltungEditor', null)" class="btn-primary text-xs py-1 px-3">+ Neu</button>
      </div>
    </div>
    <div class="bg-white shadow rounded-xl overflow-hidden">
      <table class="min-w-full divide-y divide-gray-200">
        <thead class="bg-gray-50 text-[9px] uppercase font-bold text-gray-500">
        <tr>
          <th @click="toggleSort('veranstaltungen', 'name')" class="px-4 py-1.5 text-left cursor-pointer hover:text-indigo-600 transition font-bold">Name <ArrowUpDownIcon class="w-3 h-3 inline ml-0.5"/></th>
          <th class="px-4 py-1.5 text-left font-bold">Datum</th>
          <th class="px-4 py-1.5 text-right font-bold">Aktionen</th>
        </tr>
        </thead>
        <tbody class="text-xs">
        <template v-for="v in paginatedVeranstaltungen" :key="v.id">
          <tr :class="selectedVid === v.id ? 'bg-indigo-50 border-l-4 border-l-indigo-500' : ''">
            <td class="px-4 py-2 font-bold">{{ v.name }}</td>
            <td class="px-4 py-2">{{ formatDate(v.beginntAm) }}</td>
            <td class="px-4 py-2 text-right space-x-2">
              <button @click="emit('selectVeranstaltung', v.id)" class="text-indigo-600 font-bold hover:underline">Wählen</button>
              <button @click="emit('openVeranstaltungEditor', v)" class="text-gray-600" title="Bearbeiten">
                <PencilIcon class="w-3.5 h-3.5 inline"/>
              </button>
              <button @click="emit('deleteVeranstaltung', v.id)" class="text-red-600">
                <Trash2Icon class="w-3.5 h-3.5 inline"/>
              </button>
            </td>
          </tr>
          <tr v-if="selectedVid === v.id" class="bg-gray-50/50">
            <td colspan="3" class="px-4 py-4 space-y-6">
              <div class="flex flex-col gap-6">
                <!-- Vorträge & Referenten -->
                <div class="space-y-2">
                  <button @click="expandedSections.vortraege = !expandedSections.vortraege"
                          class="w-full flex items-center gap-3 text-[10px] font-black text-indigo-700 uppercase tracking-widest border-b border-indigo-100 pb-1 hover:bg-indigo-50 transition-colors">
                    <ChevronDownIcon v-if="!expandedSections.vortraege" class="w-3 h-3 shrink-0"/>
                    <ChevronUpIcon v-else class="w-3 h-3 shrink-0"/>
                    <div class="flex items-center gap-2">
                      <FileTextIcon class="w-3 h-3"/> Vorträge & Referenten ({{ vortraege.length }})
                    </div>
                  </button>
                  <div v-if="expandedSections.vortraege" class="animate-fade-in space-y-2">
                    <div v-if="vortraege.length > 0" class="bg-white rounded-lg border border-gray-200 overflow-hidden shadow-sm">
                      <table class="min-w-full divide-y divide-gray-200 text-[10px]">
                        <thead class="bg-gray-50 text-[8px] uppercase font-bold text-gray-500">
                        <tr>
                          <th @click="toggleSort('v_vortraege', 'titel')" class="px-3 py-1.5 text-left cursor-pointer hover:text-indigo-600 transition">Titel <ArrowUpDownIcon class="w-2.5 h-2.5 inline ml-0.5"/></th>
                          <th @click="toggleSort('v_vortraege', 'referentName')" class="px-3 py-1.5 text-left cursor-pointer hover:text-indigo-600 transition">Referent <ArrowUpDownIcon class="w-2.5 h-2.5 inline ml-0.5"/></th>
                          <th @click="toggleSort('v_vortraege', 'referentOrganisation')" class="px-3 py-1.5 text-left cursor-pointer hover:text-indigo-600 transition">Organisation <ArrowUpDownIcon class="w-2.5 h-2.5 inline ml-0.5"/></th>
                        </tr>
                        </thead>
                        <tbody class="divide-y divide-gray-100">
                        <tr v-for="talk in paginatedVSubVortraege" :key="talk.id" class="hover:bg-indigo-50/30 transition">
                          <td class="px-3 py-1.5 font-semibold text-gray-800">{{ talk.titel }}</td>
                          <td class="px-3 py-1.5 text-gray-600">{{ talk.referentName }}</td>
                          <td class="px-3 py-1.5 text-gray-600">{{ talk.referentOrganisation }}</td>
                        </tr>
                        </tbody>
                      </table>
                      <PaginationControls v-model:currentPage="pages.v_vortraege" :totalItems="filteredVSubVortraege.length" :pageSize="pageSize"/>
                    </div>
                    <div v-else class="p-4 bg-white rounded-lg border border-dashed border-gray-300 text-center text-gray-500 text-[10px]">
                      Bitte Vorträge erfassen.
                    </div>
                  </div>
                </div>

                <!-- Teilnehmer & Prioritäten -->
                <div class="space-y-2">
                  <button @click="expandedSections.teilnehmer = !expandedSections.teilnehmer"
                          class="w-full flex items-center gap-3 text-[10px] font-black text-indigo-700 uppercase tracking-widest border-b border-indigo-100 pb-1 hover:bg-indigo-50 transition-colors">
                    <ChevronDownIcon v-if="!expandedSections.teilnehmer" class="w-3 h-3 shrink-0"/>
                    <ChevronUpIcon v-else class="w-3 h-3 shrink-0"/>
                    <div class="flex items-center gap-2">
                      <UsersIcon class="w-3 h-3"/> Teilnehmer & Prioritäten ({{ teilnehmer.length }})
                    </div>
                  </button>
                  <div v-if="expandedSections.teilnehmer" class="animate-fade-in space-y-2">
                    <div v-if="teilnehmer.length > 0" class="bg-white rounded-lg border border-gray-200 overflow-hidden shadow-sm">
                      <table class="min-w-full divide-y divide-gray-200 text-[10px]">
                        <thead class="bg-gray-50 text-[8px] uppercase font-bold text-gray-500">
                        <tr>
                          <th @click="toggleSort('v_teilnehmer', 'lastName')" class="px-3 py-1.5 text-left cursor-pointer hover:text-indigo-600 transition">Name <ArrowUpDownIcon class="w-2.5 h-2.5 inline ml-0.5"/></th>
                          <th @click="toggleSort('v_teilnehmer', 'gruppe')" class="px-3 py-1.5 text-left cursor-pointer hover:text-indigo-600 transition">Gruppe <ArrowUpDownIcon class="w-2.5 h-2.5 inline ml-0.5"/></th>
                          <th class="px-3 py-1.5 text-left">Prios</th>
                        </tr>
                        </thead>
                        <tbody class="divide-y divide-gray-100">
                        <tr v-for="part in paginatedVSubParticipants" :key="part.id" class="hover:bg-indigo-50/30 transition">
                          <td class="px-3 py-1.5 font-semibold text-gray-800" :title="part.email">{{ part.lastName }}, {{ part.firstName }}</td>
                          <td class="px-3 py-1.5 text-gray-600">{{ part.gruppe }}</td>
                          <td class="px-3 py-1.5 text-gray-500">{{ part.prioritaeten?.map(p => `${p.vortragId}:${p.prioWert}`).join(', ') || '-' }}</td>
                        </tr>
                        </tbody>
                      </table>
                      <PaginationControls v-model:currentPage="pages.v_teilnehmer" :totalItems="filteredVSubParticipants.length" :pageSize="pageSize"/>
                    </div>
                    <div v-else class="p-4 bg-white rounded-lg border border-dashed border-gray-300 text-center text-gray-500 text-[10px]">
                      Bitte Teilnehmer erfassen.
                    </div>
                  </div>
                </div>
              </div>
            </td>
          </tr>
        </template>
        </tbody>
      </table>
      <PaginationControls v-model:currentPage="pages.veranstaltungen" :totalItems="filteredVeranstaltungen.length" :pageSize="pageSize"/>
    </div>
  </section>
</template>

<script setup>
import { computed, reactive, watch } from 'vue';
import {
  ArrowUpDown as ArrowUpDownIcon,
  ChevronDown as ChevronDownIcon,
  ChevronUp as ChevronUpIcon,
  FileText as FileTextIcon,
  Pencil as PencilIcon,
  Trash2 as Trash2Icon,
  Upload as UploadIcon,
  Users as UsersIcon
} from 'lucide-vue-next';
import PaginationControls from '../../PaginationControls.vue';

const props = defineProps({
  veranstaltungen: Array,
  selectedVid: Number,
  vortraege: Array,
  teilnehmer: Array,
  pageSize: Number,
  canImportVeranstaltung: Boolean
});

const emit = defineEmits(['triggerUpload', 'openVeranstaltungEditor', 'deleteVeranstaltung', 'selectVeranstaltung']);

const pages = reactive({
  veranstaltungen: 1,
  v_vortraege: 1,
  v_teilnehmer: 1
});

const filters = reactive({
  veranstaltungen: ''
});

const sorts = reactive({
  veranstaltungen: { key: 'name', dir: 'asc' },
  v_vortraege: { key: 'titel', dir: 'asc' },
  v_teilnehmer: { key: 'lastName', dir: 'asc' }
});

const expandedSections = reactive({
  vortraege: false,
  teilnehmer: false
});

watch(() => filters.veranstaltungen, () => { pages.veranstaltungen = 1; });

const formatDate = (d) => d ? new Date(d).toLocaleDateString('de-DE') : '';

const getNestedValue = (obj, path) => {
  return path.split('.').reduce((acc, part) => acc && acc[part], obj);
};

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
    const valA = getNestedValue(a, sortConfig.key) || '';
    const valB = getNestedValue(b, sortConfig.key) || '';
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

const filteredVeranstaltungen = computed(() => processList(props.veranstaltungen, filters.veranstaltungen, sorts.veranstaltungen));
const paginatedVeranstaltungen = computed(() => paginate(filteredVeranstaltungen.value, pages.veranstaltungen));

const filteredVSubVortraege = computed(() => {
  return processList(props.vortraege, '', sorts.v_vortraege);
});
const paginatedVSubVortraege = computed(() => paginate(filteredVSubVortraege.value, pages.v_vortraege));

const filteredVSubParticipants = computed(() => {
  return processList(props.teilnehmer, '', sorts.v_teilnehmer);
});
const paginatedVSubParticipants = computed(() => paginate(filteredVSubParticipants.value, pages.v_teilnehmer));

</script>

<style scoped>
.btn-primary { @apply rounded-lg bg-indigo-600 px-3 py-1.5 text-white font-bold hover:bg-indigo-700 transition shadow-sm border-none cursor-pointer disabled:opacity-50; }
.btn-secondary { @apply bg-white text-gray-700 px-3 py-1.5 rounded-lg hover:bg-gray-50 font-bold border border-gray-200 transition shadow-sm cursor-pointer disabled:opacity-50; }
.input-field { @apply rounded-lg border border-gray-300 px-2 py-1 text-gray-900 focus:ring-2 focus:ring-indigo-500 bg-white; }
.animate-fade-in { animation: fadeIn 0.3s ease-in-out; }
@keyframes fadeIn { from { opacity: 0; transform: translateY(5px); } to { opacity: 1; transform: translateY(0); } }
</style>
