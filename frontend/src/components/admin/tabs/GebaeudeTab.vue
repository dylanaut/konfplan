<template>
  <section class="space-y-4 animate-fade-in">
    <div class="flex justify-between items-center bg-white p-3 rounded-xl border border-gray-100 shadow-sm">
      <h2 class="text-lg font-bold text-gray-800">Gebäude & Räume</h2>
      <div class="flex gap-2">
        <input v-model="filters.gebaeude" placeholder="Suchen..." class="input-field text-xs py-1 px-2"/>
        <button @click="emit('openGebaeudeEditor', null)" class="btn-primary text-xs py-1 px-3">+ Neues Gebäude</button>
      </div>
    </div>

    <div v-if="filteredGebaeude.length > 0" class="bg-white shadow rounded-xl overflow-hidden border border-gray-100">
      <table class="min-w-full divide-y divide-gray-200 text-xs">
        <thead class="bg-gray-50 text-[9px] uppercase font-bold text-gray-500">
        <tr>
          <th @click="toggleSort('gebaeude', 'name')" class="px-4 py-1.5 text-left cursor-pointer hover:text-indigo-600 transition font-bold">Name <ArrowUpDownIcon class="w-3 h-3 inline ml-0.5"/></th>
          <th @click="toggleSort('gebaeude', 'ort')" class="px-4 py-1.5 text-left cursor-pointer hover:text-indigo-600 transition font-bold">Ort <ArrowUpDownIcon class="w-3 h-3 inline ml-0.5"/></th>
          <th @click="toggleSort('gebaeude', 'typ')" class="px-4 py-1.5 text-left cursor-pointer hover:text-indigo-600 transition font-bold">Typ <ArrowUpDownIcon class="w-3 h-3 inline ml-0.5"/></th>
          <th class="px-4 py-1.5 text-right font-bold">Aktionen</th>
        </tr>
        </thead>
        <tbody class="divide-y divide-gray-100">
        <template v-for="g in paginatedGebaeude" :key="g.id">
          <tr class="hover:bg-gray-50">
            <td class="px-4 py-2 font-bold">
              <button @click="toggleRooms(g.id)" class="flex items-center text-indigo-600 hover:text-indigo-800">
                <ChevronRightIcon v-if="!expandedBuildings[g.id]" class="w-3 h-3 mr-1"/>
                <ChevronDownIcon v-else class="w-3 h-3 mr-1"/>
                {{ g.name }}
              </button>
            </td>
            <td class="px-4 py-2">{{ g.ort }}</td>
            <td class="px-4 py-2">{{ g.typ }}</td>
            <td class="px-4 py-2 text-right">
              <button @click="emit('openGebaeudeEditor', g)" class="text-indigo-600" title="Gebäude bearbeiten">
                <PencilIcon class="w-3.5 h-3.5 inline"/>
              </button>
              <button @click="emit('deleteGebaeude', g)" class="text-red-600 ml-3" title="Gebäude löschen">
                <Trash2Icon class="w-3.5 h-3.5 inline"/>
              </button>
              <button @click="emit('openRaumEditor', null, g.id)" class="btn-primary text-xs py-1 px-3 ml-3" title="Raum hinzufügen">+ Raum</button>
            </td>
          </tr>
          <tr v-if="expandedBuildings[g.id]">
            <td :colspan="4" class="p-0">
              <div class="bg-gray-50 p-3 border-t border-gray-100">
                <h4 class="text-sm font-semibold text-gray-700 mb-2">Räume in {{ g.name }}</h4>
                <table class="min-w-full divide-y divide-gray-200 text-xs">
                  <thead class="bg-gray-100 text-[9px] uppercase font-bold text-gray-500">
                  <tr>
                    <th class="px-4 py-1.5 text-left">Name</th>
                    <th class="px-4 py-1.5 text-center">Kapazität</th>
                    <th class="px-4 py-1.5 text-left">Etage</th>
                    <th class="px-4 py-1.5 text-right">Aktionen</th>
                  </tr>
                  </thead>
                  <tbody class="divide-y divide-gray-100">
                  <tr v-for="r in g.raeume" :key="r.id" class="hover:bg-gray-100">
                    <td class="px-4 py-2">{{ r.name }}</td>
                    <td class="px-4 py-2 text-center">{{ r.kapazitaet }}</td>
                    <td class="px-4 py-2">{{ r.etage || '-' }}</td>
                    <td class="px-4 py-2 text-right">
                      <button @click="emit('openRaumEditor', r, g.id)" class="text-indigo-600" title="Raum bearbeiten">
                        <PencilIcon class="w-3.5 h-3.5 inline"/>
                      </button>
                      <button @click="emit('deleteRaum', r, g.id)" class="text-red-600 ml-3" title="Raum löschen">
                        <Trash2Icon class="w-3.5 h-3.5 inline"/>
                      </button>
                    </td>
                  </tr>
                  <tr v-if="!g.raeume || g.raeume.length === 0">
                    <td colspan="4" class="px-4 py-2 text-center text-gray-500">Keine Räume vorhanden.</td>
                  </tr>
                  </tbody>
                </table>
              </div>
            </td>
          </tr>
        </template>
        </tbody>
      </table>
      <PaginationControls v-model:currentPage="pages.gebaeude" :totalItems="filteredGebaeude.length" :pageSize="pageSize"/>
    </div>
    <div v-else class="bg-white p-8 rounded-xl text-center border-2 border-dashed border-gray-200 text-gray-500">
      <Building2Icon class="w-10 h-10 mx-auto mb-3 text-gray-400" />
      <p class="font-bold">Bitte Gebäude erfassen.</p>
    </div>
  </section>
</template>

<script setup>
import { computed, reactive, watch } from 'vue';
import {
  ArrowUpDown as ArrowUpDownIcon,
  Building2 as Building2Icon,
  ChevronDown as ChevronDownIcon,
  ChevronRight as ChevronRightIcon,
  Pencil as PencilIcon,
  Trash2 as Trash2Icon
} from 'lucide-vue-next';
import PaginationControls from '../../PaginationControls.vue';

const props = defineProps({
  gebaeude: Array, // Jetzt werden Gebäude statt Räume übergeben
  pageSize: Number
});

const emit = defineEmits([
  'openGebaeudeEditor',
  'deleteGebaeude',
  'openRaumEditor',
  'deleteRaum'
]);

const pages = reactive({
  gebaeude: 1
});

const filters = reactive({
  gebaeude: ''
});

const sorts = reactive({
  gebaeude: { key: 'name', dir: 'asc' }
});

const expandedBuildings = reactive({}); // Zustand für auf-/zuklappbare Gebäude

const toggleRooms = (id) => {
  expandedBuildings[id] = !expandedBuildings[id];
};

watch(() => filters.gebaeude, () => { pages.gebaeude = 1; });

// Helper function to get nested property values
const getNestedValue = (obj, path) => {
  return path.split('.').reduce((acc, part) => acc && acc[part], obj);
};

const processList = (list, filterText, sortConfig) => {
  let result = [...list];
  if (filterText) {
    const f = filterText.toLowerCase();
    result = result.filter(item => {
      const searchValues = [
        item.name,
        item.ort,
        item.typ,
        item.strasse,
        item.hausnummer,
        item.postleitzahl,
        ...(item.raeume ? item.raeume.map(r => [r.name, r.etage, r.kapazitaet]) : [])
      ].flat().map(val => String(val || '').toLowerCase());
      return searchValues.some(val => val.includes(f));
    });
  }
  result.sort((a, b) => {
    const valA = getNestedValue(a, sortConfig.key);
    const valB = getNestedValue(b, sortConfig.key);

    if (typeof valA === 'number' && typeof valB === 'number') {
      return sortConfig.dir === 'asc' ? valA - valB : valB - valA;
    }
    const cmp = String(valA || '').localeCompare(String(valB || ''));
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

const filteredGebaeude = computed(() => processList(props.gebaeude, filters.gebaeude, sorts.gebaeude));
const paginatedGebaeude = computed(() => paginate(filteredGebaeude.value, pages.gebaeude));
</script>

<style scoped>
.btn-primary { @apply rounded-lg bg-indigo-600 px-3 py-1.5 text-white font-bold hover:bg-indigo-700 transition shadow-sm border-none cursor-pointer disabled:opacity-50; }
.btn-secondary { @apply bg-white text-gray-700 px-3 py-1.5 rounded-lg hover:bg-gray-50 font-bold border border-gray-200 transition shadow-sm cursor-pointer disabled:opacity-50; }
.input-field { @apply rounded-lg border border-gray-300 px-2 py-1 text-gray-900 focus:ring-2 focus:ring-indigo-500 bg-white; }
.animate-fade-in { animation: fadeIn 0.3s ease-in-out; }
@keyframes fadeIn { from { opacity: 0; transform: translateY(5px); } to { opacity: 1; transform: translateY(0); } }
</style>
