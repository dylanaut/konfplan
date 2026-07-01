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

    <!-- Raum-Verfügbarkeits-Matrix (nur wenn eine Veranstaltung ausgewählt ist und Slots existieren) -->
    <div v-if="showRoomAvailability" class="space-y-2">
      <div
        @click="showRoomAvailabilityBlock = !showRoomAvailabilityBlock"
        class="w-full flex items-center justify-between gap-3 text-[10px] font-black text-indigo-700 uppercase tracking-widest bg-white p-3 rounded-xl border border-gray-100 shadow-sm cursor-pointer hover:bg-gray-50 transition">
        <div class="flex items-center gap-3">
          <ChevronDownIcon v-if="!showRoomAvailabilityBlock" class="w-3.5 h-3.5 shrink-0"/>
          <ChevronUpIcon v-else class="w-3.5 h-3.5 shrink-0"/>
          <div class="flex items-center gap-2">
            <CheckSquareIcon class="w-4 h-4"/>
            Raum-Verfügbarkeiten verwalten
          </div>
        </div>
        <button v-if="availabilityStore.hasDirtyAvailabilities()"
                @click.stop="availabilityStore.saveAvailabilities(selectedVid)"
                :disabled="isEventFinished"
                class="btn-save-all">
          <SaveAllIcon class="w-3.5 h-3.5"/>
          Alle Änderungen speichern
        </button>
      </div>

      <div v-show="showRoomAvailabilityBlock">
        <div v-if="buildingsWithEventRooms.length > 0"
             class="bg-white shadow rounded-xl overflow-x-auto border border-gray-100">
          <table class="min-w-full divide-y divide-gray-200 text-xs">
            <thead class="bg-gray-50 text-[9px] uppercase font-bold text-gray-500">
            <tr>
              <th class="px-4 py-1.5 text-left font-bold">Raum</th>
              <th v-for="slot in sortedSlots" :key="slot.id"
                  class="px-2 py-2 text-center text-[8px] font-bold text-gray-500">
                {{ formatTime(slot.startTime) }}
              </th>
            </tr>
            </thead>
            <tbody class="divide-y divide-gray-100">
            <template v-for="g in buildingsWithEventRooms" :key="g.id">
              <tr class="bg-gray-50">
                <td :colspan="sortedSlots.length + 1"
                    class="px-4 py-1.5 font-bold text-gray-600 flex items-center gap-1.5">
                  <Building2Icon class="w-3.5 h-3.5"/>
                  {{ g.name }}
                </td>
              </tr>
              <tr v-for="r in g.eventRaeume" :key="r.id"
                  :class="['hover:bg-gray-50', availabilityStore.isRoomAvailabilityChanged(r.id) ? 'bg-orange-50/50' : '']">
                <td class="px-4 py-2 font-bold">
                  <div class="flex items-center gap-1.5">
                    <span>{{ r.name }}</span>
                    <AlertTriangleIcon v-if="availabilityStore.getRoomBlockingEvent(r.id)"
                                       class="w-3.5 h-3.5 text-amber-500 shrink-0"
                                       :title="`Kollision mit Veranstaltung: ${availabilityStore.getRoomBlockingEvent(r.id)}`"/>
                  </div>
                </td>
                <td v-for="slot in sortedSlots" :key="slot.id" class="px-2 py-2 text-center">
                  <input type="checkbox" :checked="availabilityStore.isRoomAvailable(r.id, slot.id)"
                         @change="availabilityStore.toggleRoomAvailability(r.id, slot.id)"
                         :disabled="isEventFinished"
                         class="rounded text-indigo-600 focus:ring-indigo-500 h-3 w-3"/>
                </td>
              </tr>
            </template>
            </tbody>
          </table>
        </div>
        <div v-else class="bg-white p-8 rounded-xl text-center border-2 border-dashed border-gray-200 text-gray-500">
          <Building2Icon class="w-10 h-10 mx-auto mb-3 text-gray-400"/>
          <p class="font-bold">Für diese Veranstaltung sind keine Räume zugeordnet.</p>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue';
import {
  AlertTriangle as AlertTriangleIcon,
  ArrowUpDown as ArrowUpDownIcon,
  Building2 as Building2Icon,
  CheckSquare as CheckSquareIcon,
  ChevronDown as ChevronDownIcon,
  ChevronRight as ChevronRightIcon,
  ChevronUp as ChevronUpIcon,
  Pencil as PencilIcon,
  SaveAll as SaveAllIcon,
  Trash2 as Trash2Icon
} from '@lucide/vue';
import PaginationControls from '../../PaginationControls.vue';
import { useAvailabilityStore } from '../../../stores/availability';

const props = defineProps({
  gebaeude: Array, // Jetzt werden Gebäude statt Räume übergeben
  pageSize: Number,
  selectedVid: Number,
  sortedSlots: { type: Array, default: () => [] },
  isEventFinished: Boolean
});

const availabilityStore = useAvailabilityStore();

const showRoomAvailabilityBlock = ref(false);

// Nur anzeigen, wenn eine Veranstaltung ausgewählt ist und für sie Slots festgelegt wurden
const showRoomAvailability = computed(() => !!props.selectedVid && props.sortedSlots.length > 0);

const formatTime = (t) => t ? new Date(t).toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' }) : '';

// Gebäude mit ihren zur Veranstaltung gehörenden Räumen (die in der Verfügbarkeits-Map enthalten sind)
const buildingsWithEventRooms = computed(() => {
  return [...props.gebaeude]
    .map(g => ({
      ...g,
      eventRaeume: (g.raeume || []).filter(r => availabilityStore.roomAvailabilities.has(r.id))
    }))
    .filter(g => g.eventRaeume.length > 0)
    .sort((a, b) => String(a.name || '').localeCompare(String(b.name || '')));
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
.btn-save-all { @apply bg-orange-500 text-white text-[10px] px-3 py-1 rounded-md shadow-sm transition-all flex items-center gap-2 hover:bg-orange-600 disabled:opacity-50; }
.btn-secondary { @apply bg-white text-gray-700 px-3 py-1.5 rounded-lg hover:bg-gray-50 font-bold border border-gray-200 transition shadow-sm cursor-pointer disabled:opacity-50; }
.input-field { @apply rounded-lg border border-gray-300 px-2 py-1 text-gray-900 focus:ring-2 focus:ring-indigo-500 bg-white; }
.animate-fade-in { animation: fadeIn 0.3s ease-in-out; }
@keyframes fadeIn { from { opacity: 0; transform: translateY(5px); } to { opacity: 1; transform: translateY(0); } }
</style>
