<template>
  <section class="space-y-4 animate-fade-in">
    <div class="flex justify-between items-center bg-white p-3 rounded-xl border border-gray-100 shadow-sm">
      <h2 class="text-lg font-bold text-gray-800">Vorträge</h2>
      <div class="flex gap-2">
        <input v-model="filters.vortraege" placeholder="Suchen..." class="input-field text-xs py-1 px-2"/>
        <button @click="emit('triggerUpload', `/api/veranstaltungen/${selectedVid}/vortraege/import`)"
                :disabled="!canImportVortraege"
                class="btn-secondary flex items-center gap-2 text-xs py-1 px-3">
          <UploadIcon class="w-3.5 h-3.5"/>
          Import
        </button>
        <button @click="emit('openVortragEditor', null)" class="btn-primary text-xs py-1 px-3">+ Neu</button>
      </div>
    </div>
    <div v-if="filteredVortraege.length > 0" class="bg-white shadow rounded-xl overflow-hidden border border-gray-100">
      <table class="min-w-full divide-y divide-gray-200 text-xs">
        <thead class="bg-gray-50 text-[9px] uppercase font-bold text-gray-500">
        <tr>
          <th @click="toggleSort('vortraege', 'titel')" class="px-4 py-1.5 text-left cursor-pointer hover:text-indigo-600 transition font-bold">Titel <ArrowUpDownIcon class="w-3 h-3 inline ml-0.5"/></th>
          <th @click="toggleSort('vortraege', 'referentName')" class="px-4 py-1.5 text-left cursor-pointer hover:text-indigo-600 transition font-bold">Referent <ArrowUpDownIcon class="w-3 h-3 inline ml-0.5"/></th>
          <th @click="toggleSort('vortraege', 'referentOrganisation')" class="px-4 py-1.5 text-left cursor-pointer hover:text-indigo-600 transition font-bold">Organisation <ArrowUpDownIcon class="w-3 h-3 inline ml-0.5"/></th>
          <th @click="toggleSort('vortraege', 'istPflicht')" class="px-4 py-1.5 text-center cursor-pointer hover:text-indigo-600 transition font-bold">Pflicht <ArrowUpDownIcon class="w-3 h-3 inline ml-0.5"/></th>
          <th class="px-4 py-1.5 text-right font-bold">Aktionen</th>
        </tr>
        </thead>
        <tbody class="divide-y divide-gray-100">
        <tr v-for="v in paginatedVortraege" :key="v.id" class="hover:bg-gray-50">
          <td class="px-4 py-2 font-bold">{{ v.titel }}</td>
          <td class="px-4 py-2">{{ v.referentName }}</td>
          <td class="px-4 py-2 text-gray-600">{{ v.referentOrganisation || '-' }}</td>
          <td class="px-4 py-2 text-center">
            <span v-if="v.istPflicht" class="text-[10px] bg-red-100 text-red-700 px-2 py-0.5 rounded-full font-black uppercase tracking-tighter">Ja</span>
            <span v-else class="text-[10px] text-gray-400">Nein</span>
          </td>
          <td class="px-4 py-2 text-right">
            <button @click="emit('openVortragEditor', v)" class="text-indigo-600" title="Bearbeiten">
              <PencilIcon class="w-3.5 h-3.5 inline"/>
            </button>
            <button @click="emit('deleteVortrag', v.id)" class="text-red-600 ml-3">
              <Trash2Icon class="w-3.5 h-3.5 inline"/>
            </button>
          </td>
        </tr>
        </tbody>
      </table>
      <PaginationControls v-model:currentPage="pages.vortraege" :totalItems="filteredVortraege.length" :pageSize="pageSize"/>
    </div>
    <div v-else class="bg-white p-8 rounded-xl text-center border-2 border-dashed border-gray-200 text-gray-500">
      <FileTextIcon class="w-10 h-10 mx-auto mb-3 text-gray-400" />
      <p class="font-bold">Bitte Vorträge erfassen.</p>
    </div>
  </section>
</template>

<script setup>
import { computed, reactive, watch } from 'vue';
import {
  ArrowUpDown as ArrowUpDownIcon,
  FileText as FileTextIcon,
  Pencil as PencilIcon,
  Trash2 as Trash2Icon,
  Upload as UploadIcon
} from '@lucide/vue';
import PaginationControls from '../../PaginationControls.vue';

const props = defineProps({
  vortraege: Array,
  selectedVid: Number,
  pageSize: Number,
  canImportVortraege: Boolean
});

const emit = defineEmits(['triggerUpload', 'openVortragEditor', 'deleteVortrag']);

const pages = reactive({
  vortraege: 1
});

const filters = reactive({
  vortraege: ''
});

const sorts = reactive({
  vortraege: { key: 'titel', dir: 'asc' }
});

watch(() => filters.vortraege, () => { pages.vortraege = 1; });

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

const filteredVortraege = computed(() => {
  const list = props.vortraege.map(v => ({
    ...v,
    referentName: v.referentName,
    referentOrganisation: v.referentOrganisation || ''
  }));
  return processList(list, filters.vortraege, sorts.vortraege);
});
const paginatedVortraege = computed(() => paginate(filteredVortraege.value, pages.vortraege));
</script>

<style scoped>
.btn-primary { @apply rounded-lg bg-indigo-600 px-3 py-1.5 text-white font-bold hover:bg-indigo-700 transition shadow-sm border-none cursor-pointer disabled:opacity-50; }
.btn-secondary { @apply bg-white text-gray-700 px-3 py-1.5 rounded-lg hover:bg-gray-50 font-bold border border-gray-200 transition shadow-sm cursor-pointer disabled:opacity-50; }
.input-field { @apply rounded-lg border border-gray-300 px-2 py-1 text-gray-900 focus:ring-2 focus:ring-indigo-500 bg-white; }
.animate-fade-in { animation: fadeIn 0.3s ease-in-out; }
@keyframes fadeIn { from { opacity: 0; transform: translateY(5px); } to { opacity: 1; transform: translateY(0); } }
</style>
