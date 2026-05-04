<template>
  <section class="space-y-4">
    <div class="flex justify-between items-center bg-white p-3 rounded-xl border border-gray-100 shadow-sm">
      <h2 class="text-lg font-bold text-gray-800">Gebäude</h2>
      <div class="flex gap-2">
        <input v-model="filters.gebaeude" placeholder="Suchen..." class="input-field text-xs py-1 px-2"/>
        <button @click="emit('triggerUpload', '/api/gebaeude/import')"
                class="btn-secondary flex items-center gap-2 text-xs py-1 px-3">
          <UploadIcon class="w-3.5 h-3.5"/>
          Import
        </button>
        <button @click="emit('openGebaeudeEditor', null)" class="btn-primary text-xs py-1 px-3">+ Neu</button>
      </div>
    </div>
    <div class="bg-white shadow rounded-xl overflow-hidden">
      <table class="min-w-full divide-y divide-gray-200">
        <thead class="bg-gray-50 text-[9px] uppercase font-bold text-gray-500">
        <tr>
          <th @click="toggleSort('gebaeude', 'name')"
              class="px-4 py-1.5 text-left cursor-pointer hover:text-indigo-600 transition font-bold">Name
            <ArrowUpDownIcon class="w-3 h-3 inline ml-0.5"/>
          </th>
          <th class="px-4 py-1.5 text-left font-bold">Adresse</th>
          <th class="px-4 py-1.5 text-left font-bold">Typ</th>
          <th class="px-4 py-1.5 text-right font-bold">Aktionen</th>
        </tr>
        </thead>
        <tbody class="text-xs">
        <template v-for="g in paginatedGebaeude" :key="g.id">
          <tr class="bg-white hover:bg-gray-50 transition border-t border-gray-100">
            <td class="px-4 py-2 font-bold">{{ g.name }}</td>
            <td class="px-4 py-2 text-gray-600">
              <a :href="generateMapsUrl(g)"
                  target="_blank"
                  rel="noopener noreferrer"
                  class="text-blue-600 hover:underline flex items-center gap-1"
                  title="In Google Maps öffnen"
              >{{ g.strasse }} {{ g.hausnummer }}, {{ g.ort }}
                <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24"
                     stroke="currentColor">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                        d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14"/>
                </svg>
              </a>
            </td>
            <td class="px-4 py-2">{{ g.typ }}</td>
            <td class="px-4 py-2 text-right space-x-2">
              <button @click="emit('openGebaeudeEditor', g)" class="text-indigo-600" title="Bearbeiten">
                <PencilIcon class="w-3.5 h-3.5 inline"/>
              </button>
              <button @click="emit('deleteGebaeude', g.id)" class="text-red-600 ml-2">
                <Trash2Icon class="w-3.5 h-3.5 inline"/>
              </button>
            </td>
          </tr>
          <tr v-if="g.raeume && g.raeume.length > 0" class="bg-gray-50">
            <td colspan="4" class="px-4 py-2">
              <div class="flex items-center justify-between text-[8px] font-bold text-gray-500 uppercase mb-1">
                Räume in {{ g.name }}
                <button @click="emit('openRaumEditor', null, g.id)" class="btn-primary-xs text-[9px] px-2 py-0.5">+
                  Raum
                </button>
              </div>
              <div class="border border-gray-200 rounded-lg overflow-hidden bg-white">
                <table class="min-w-full divide-y divide-gray-200">
                  <thead class="bg-gray-100 text-[8px] uppercase font-bold text-gray-500">
                  <tr>
                    <th @click="toggleSort('raeume', 'name')"
                        class="px-3 py-1 text-left cursor-pointer hover:text-indigo-600 transition">Raum
                    </th>
                    <th @click="toggleSort('raeume', 'kapazitaet')"
                        class="px-3 py-1 text-left cursor-pointer hover:text-indigo-600 transition">Kapazität
                    </th>
                    <th @click="toggleSort('raeume', 'etage')"
                        class="px-3 py-1 text-left cursor-pointer hover:text-indigo-600 transition">Etage
                    </th>
                    <th class="px-3 py-1 text-right">Aktionen</th>
                  </tr>
                  </thead>
                  <tbody class="divide-y divide-gray-50 text-[10px]">
                  <tr v-for="r in sortRaeume(g.raeume)" :key="r.id" class="hover:bg-gray-50 transition">
                    <td class="px-3 py-1 font-medium text-gray-900">{{ r.name }}</td>
                    <td class="px-3 py-1 text-gray-600">{{ r.kapazitaet }}</td>
                    <td class="px-3 py-1 text-gray-600">{{ r.etage || '-' }}</td>
                    <td class="px-3 py-1 text-right space-x-2">
                      <button @click="emit('openRaumEditor', r, g.id)" class="text-indigo-600" title="Bearbeiten">
                        <PencilIcon class="w-3.5 h-3.5 inline"/>
                      </button>
                      <button @click="emit('deleteRaum', r)" class="text-red-600">
                        <Trash2Icon class="w-3.5 h-3.5 inline"/>
                      </button>
                    </td>
                  </tr>
                  </tbody>
                </table>
              </div>
            </td>
          </tr>
        </template>
        </tbody>
      </table>
      <PaginationControls v-model:currentPage="pages.gebaeude" :totalItems="filteredGebaeude.length"
                          :pageSize="pageSize"/>
    </div>
  </section>
</template>

<script setup>
import {computed, reactive, watch} from 'vue';
import {
  ArrowUpDown as ArrowUpDownIcon,
  Pencil as PencilIcon,
  Trash2 as Trash2Icon,
  Upload as UploadIcon
} from 'lucide-vue-next';
import PaginationControls from '../../PaginationControls.vue';

const props = defineProps({
  gebaeude: Array,
  pageSize: Number,
  sorts: Object
});

const emit = defineEmits(['triggerUpload', 'openGebaeudeEditor', 'deleteGebaeude', 'openRaumEditor', 'deleteRaum']);

const pages = reactive({
  gebaeude: 1
});

const filters = reactive({
  gebaeude: ''
});

const internalSorts = reactive({
  gebaeude: {key: 'name', dir: 'asc'},
  raeume: {key: 'name', dir: 'asc'}
});

watch(() => filters.gebaeude, () => {
  pages.gebaeude = 1;
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
  if (internalSorts[key].key === field) {
    internalSorts[key].dir = internalSorts[key].dir === 'asc' ? 'desc' : 'asc';
  } else {
    internalSorts[key].key = field;
    internalSorts[key].dir = 'asc';
  }
};

const sortRaeume = (raumList) => {
  const result = [...raumList];
  const config = internalSorts.raeume;
  result.sort((a, b) => {
    const valA = a[config.key] || '';
    const valB = b[config.key] || '';
    if (typeof valA === 'number' && typeof valB === 'number') {
      return config.dir === 'asc' ? valA - valB : valB - valA;
    }
    const cmp = String(valA).localeCompare(String(valB));
    return config.dir === 'asc' ? cmp : -cmp;
  });
  return result;
};

const filteredGebaeude = computed(() => processList(props.gebaeude, filters.gebaeude, internalSorts.gebaeude));
const paginatedGebaeude = computed(() => paginate(filteredGebaeude.value, pages.gebaeude));

const generateMapsUrl = (g) => {
  // Adresse zusammenfügen
  const address = `${g.strasse} ${g.hausnummer}, ${g.plz} ${g.ort}`;
  // URL-konform encodieren
  return `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(address)}`;
};
</script>

<style scoped>
.btn-primary {
  @apply rounded-lg bg-indigo-600 px-3 py-1.5 text-white font-bold hover:bg-indigo-700 transition shadow-sm border-none cursor-pointer disabled:opacity-50;
}

.btn-primary-xs {
  @apply rounded-md bg-indigo-600 px-2 py-0.5 text-white font-bold hover:bg-indigo-700 transition shadow-sm border-none cursor-pointer;
}

.btn-secondary {
  @apply bg-white text-gray-700 px-3 py-1.5 rounded-lg hover:bg-gray-50 font-bold border border-gray-200 transition shadow-sm cursor-pointer disabled:opacity-50;
}

.input-field {
  @apply rounded-lg border border-gray-300 px-2 py-1 text-gray-900 focus:ring-2 focus:ring-indigo-500 bg-white;
}
</style>
