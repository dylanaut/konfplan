<template>
  <section class="space-y-4 animate-fade-in">
    <div class="flex justify-between items-center bg-white p-3 rounded-xl border border-gray-100 shadow-sm">
      <h2 class="text-lg font-bold text-gray-800">Betrachter</h2>
      <div class="flex gap-2">
        <input v-model="filters.betrachter" placeholder="Suchen..." class="input-field text-xs py-1 px-2"/>
        <button @click="emit('openUserModal', {role: 'BETRACHTER'})" class="btn-primary text-xs py-1 px-3">+ Neu</button>
      </div>
    </div>
    <div v-if="filteredBetrachter.length > 0" class="bg-white shadow rounded-xl overflow-hidden border border-gray-100">
      <table class="min-w-full divide-y divide-gray-200 text-xs">
        <thead class="bg-gray-50 text-[9px] uppercase font-bold text-gray-500">
        <tr>
          <th @click="toggleSort('lastName')" class="px-4 py-1.5 text-left cursor-pointer hover:text-indigo-600 transition font-bold">Name <ArrowUpDownIcon class="w-3 h-3 inline ml-0.5"/></th>
          <th class="px-4 py-1.5 text-left font-bold">Sichtbare Gruppen</th>
          <th class="px-4 py-1.5 text-right font-bold">Aktionen</th>
        </tr>
        </thead>
        <tbody class="divide-y divide-gray-100">
        <tr v-for="u in paginatedBetrachter" :key="u.id" class="hover:bg-gray-50">
          <td class="px-4 py-2 font-bold" :title="u.email || ''">
            {{ u.firstName }} {{ u.lastName }}
            <span class="block font-normal text-gray-400">{{ u.loginName }}</span>
          </td>
          <td class="px-4 py-2 text-gray-500">
            <span v-if="sichtbareGruppen(u).length === 0" class="text-gray-400">–</span>
            <span v-else>{{ sichtbareGruppen(u).join(', ') }}</span>
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
      <PaginationControls v-model:currentPage="page.value" :totalItems="filteredBetrachter.length" :pageSize="pageSize"/>
    </div>
    <div v-else class="bg-white p-8 rounded-xl text-center border-2 border-dashed border-gray-200 text-gray-500">
      <EyeIcon class="w-10 h-10 mx-auto mb-3 text-gray-400" />
      <p class="font-bold">Bitte Betrachter erfassen.</p>
    </div>
  </section>
</template>

<script setup>
import { computed, reactive, watch } from 'vue';
import {
  ArrowUpDown as ArrowUpDownIcon,
  Eye as EyeIcon,
  Mail as MailIcon,
  Pencil as PencilIcon,
  Trash2 as Trash2Icon
} from '@lucide/vue';
import PaginationControls from '../../PaginationControls.vue';
import { getGruppenkategorieWerte } from '../../../composables/useGruppenkategorieColumns';

const props = defineProps({
  betrachter: Array,
  selectedVid: Number,
  pageSize: Number
});

const emit = defineEmits(['openUserModal', 'deleteUser', 'openInviteModal']);

const page = reactive({ value: 1 });
const filters = reactive({ betrachter: '' });
const sort = reactive({ key: 'lastName', dir: 'asc' });

watch(() => filters.betrachter, () => { page.value = 1; });

const betrachterForEvent = computed(() =>
  props.betrachter.filter(b => b && b.veranstaltungIds && Array.isArray(b.veranstaltungIds) && b.veranstaltungIds.includes(props.selectedVid))
);

const sichtbareGruppen = (betrachter) => {
  const kategorienNamen = Object.keys(betrachter?.gruppenwerteByKategorie || {});
  return kategorienNamen.flatMap(name => getGruppenkategorieWerte(betrachter, name));
};

const filteredBetrachter = computed(() => {
  let result = [...betrachterForEvent.value];
  if (filters.betrachter) {
    const f = filters.betrachter.toLowerCase();
    result = result.filter(item => {
      const searchStrings = [item.firstName, item.lastName, item.loginName, item.email, ...sichtbareGruppen(item)];
      return searchStrings.some(val => val && String(val).toLowerCase().includes(f));
    });
  }
  result.sort((a, b) => {
    const valA = a[sort.key] || '';
    const valB = b[sort.key] || '';
    const cmp = String(valA).localeCompare(String(valB));
    return sort.dir === 'asc' ? cmp : -cmp;
  });
  return result;
});

const paginatedBetrachter = computed(() => {
  const start = (page.value - 1) * props.pageSize;
  return filteredBetrachter.value.slice(start, start + props.pageSize);
});

const toggleSort = (field) => {
  if (sort.key === field) {
    sort.dir = sort.dir === 'asc' ? 'desc' : 'asc';
  } else {
    sort.key = field;
    sort.dir = 'asc';
  }
};
</script>

<style scoped>
@reference "tailwindcss";

.btn-primary { @apply rounded-lg bg-indigo-600 px-3 py-1.5 text-white font-bold hover:bg-indigo-700 transition shadow-sm border-none cursor-pointer disabled:opacity-50; }
.input-field { @apply rounded-lg border border-gray-300 px-2 py-1 text-gray-900 focus:ring-2 focus:ring-indigo-500 bg-white; }
.animate-fade-in { animation: fadeIn 0.3s ease-in-out; }
@keyframes fadeIn { from { opacity: 0; transform: translateY(5px); } to { opacity: 1; transform: translateY(0); } }
</style>
