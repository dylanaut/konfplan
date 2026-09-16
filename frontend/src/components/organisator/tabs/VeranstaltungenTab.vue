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
              <button @click="emit('openVeranstaltungEditor', v)" class="text-gray-600" title="Bearbeiten" aria-label="Bearbeiten">
                <PencilIcon class="w-3.5 h-3.5 inline"/>
              </button>
              <button @click="emit('deleteVeranstaltung', v.id)" class="text-red-600" title="Löschen" aria-label="Löschen">
                <Trash2Icon class="w-3.5 h-3.5 inline"/>
              </button>
            </td>
          </tr>
          <tr v-if="selectedVid === v.id" class="bg-gray-50/50">
            <td colspan="3" class="px-4 py-4 space-y-6">
              <div class="flex flex-col gap-6">
                <!-- Gruppenkategorien (#690) -->
                <div class="space-y-2">
                  <button @click="expandedSections.gruppenkategorien = !expandedSections.gruppenkategorien"
                          class="w-full flex items-center gap-3 text-[10px] font-black text-indigo-700 uppercase tracking-widest border-b border-indigo-100 pb-1 hover:bg-indigo-50 transition-colors">
                    <ChevronDownIcon v-if="!expandedSections.gruppenkategorien" class="w-3 h-3 shrink-0"/>
                    <ChevronUpIcon v-else class="w-3 h-3 shrink-0"/>
                    <div class="flex items-center gap-2">
                      <TagsIcon class="w-3 h-3"/> Gruppenkategorien ({{ gruppenkategorieStore.gruppenkategorien.length }})
                    </div>
                  </button>
                  <div v-if="expandedSections.gruppenkategorien" class="animate-fade-in space-y-3 p-4 bg-white rounded-lg border border-gray-200 shadow-sm">
                    <div class="flex justify-end">
                      <button @click="openGruppenkategorieEditor(null)" class="btn-secondary text-xs py-1 px-3">+ Neue Kategorie</button>
                    </div>
                    <div v-if="gruppenkategorieStore.gruppenkategorien.length > 0" class="space-y-2">
                      <div v-for="kat in gruppenkategorieStore.gruppenkategorien" :key="kat.id" class="border border-gray-200 rounded-lg overflow-hidden">
                        <div class="flex items-center justify-between p-2 bg-gray-50">
                          <button @click="toggleKategorie(kat.id)" class="flex items-center gap-2 text-xs font-bold text-gray-800">
                            <ChevronRightIcon v-if="!expandedKategorien[kat.id]" class="w-3 h-3"/>
                            <ChevronDownIcon v-else class="w-3 h-3"/>
                            {{ kat.name }}
                            <span class="text-[9px] font-normal px-1.5 py-0.5 rounded-full bg-indigo-100 text-indigo-700">{{ kat.mehrwertig ? 'Mehrwertig' : 'Einwertig' }}</span>
                            <span class="text-[9px] font-normal px-1.5 py-0.5 rounded-full" :class="kat.pflicht ? 'bg-red-100 text-red-700' : 'bg-gray-100 text-gray-600'">{{ kat.pflicht ? 'Pflicht' : 'Kann' }}</span>
                            <span class="text-[9px] font-normal text-gray-400">({{ kat.werte.length }} Werte)</span>
                          </button>
                          <div class="space-x-2">
                            <button @click="openGruppenkategorieEditor(kat)" class="text-gray-500 hover:text-indigo-600" title="Kategorie bearbeiten" aria-label="Kategorie bearbeiten">
                              <PencilIcon class="w-3.5 h-3.5"/>
                            </button>
                            <button @click="handleDeleteGruppenkategorie(kat)" class="text-gray-500 hover:text-red-600" title="Kategorie löschen" aria-label="Kategorie löschen">
                              <Trash2Icon class="w-3.5 h-3.5"/>
                            </button>
                          </div>
                        </div>
                        <div v-if="expandedKategorien[kat.id]" class="p-3 space-y-2">
                          <div class="flex gap-2">
                            <input v-model="newWertByKategorie[kat.id]" @keyup.enter="handleAddWert(kat)" placeholder="Neuer Wert..." class="input-field text-xs flex-grow"/>
                            <button @click="handleAddWert(kat)" class="btn-secondary text-xs py-1 px-3">Hinzufügen</button>
                          </div>
                          <ul v-if="kat.werte.length > 0" class="space-y-1 text-xs">
                            <li v-for="wert in kat.werte" :key="wert.id" class="flex justify-between items-center p-1.5 bg-gray-50 rounded">
                              <span>{{ wert.wert }}</span>
                              <div class="space-x-2">
                                <button @click="handleRenameWert(wert)" class="text-gray-500 hover:text-indigo-600"><PencilIcon class="w-3 h-3"/></button>
                                <button @click="handleRemoveWert(wert)" class="text-gray-500 hover:text-red-600"><Trash2Icon class="w-3 h-3"/></button>
                              </div>
                            </li>
                          </ul>
                          <div v-else class="text-center text-gray-400 text-[10px]">Keine Werte definiert.</div>
                        </div>
                      </div>
                    </div>
                    <div v-else class="text-center text-gray-400 text-[10px]">Keine Gruppenkategorien definiert.</div>
                  </div>
                </div>

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
                        <tr v-for="vortrag in paginatedVSubVortraege" :key="vortrag.id" class="hover:bg-indigo-50/30 transition">
                          <td class="px-3 py-1.5 font-semibold text-gray-800">{{ vortrag.titel }}</td>
                          <td class="px-3 py-1.5 text-gray-600">{{ vortrag.referentName }}</td>
                          <td class="px-3 py-1.5 text-gray-600">{{ vortrag.referentOrganisation }}</td>
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

                <!-- Teilnehmer -->
                <div class="space-y-2">
                  <button @click="expandedSections.teilnehmer = !expandedSections.teilnehmer"
                          class="w-full flex items-center gap-3 text-[10px] font-black text-indigo-700 uppercase tracking-widest border-b border-indigo-100 pb-1 hover:bg-indigo-50 transition-colors">
                    <ChevronDownIcon v-if="!expandedSections.teilnehmer" class="w-3 h-3 shrink-0"/>
                    <ChevronUpIcon v-else class="w-3 h-3 shrink-0"/>
                    <div class="flex items-center gap-2">
                      <UsersIcon class="w-3 h-3"/> Teilnehmer ({{ filteredVSubParticipants.length }})
                    </div>
                  </button>
                  <div v-if="expandedSections.teilnehmer" class="animate-fade-in space-y-2">
                    <div v-if="filteredVSubParticipants.length > 0" class="bg-white rounded-lg border border-gray-200 overflow-hidden shadow-sm">
                      <table class="min-w-full divide-y divide-gray-200 text-[10px]">
                        <thead class="bg-gray-50 text-[8px] uppercase font-bold text-gray-500">
                        <tr>
                          <th @click="toggleSort('v_teilnehmer', 'lastName')" class="px-3 py-1.5 text-left cursor-pointer hover:text-indigo-600 transition">Name <ArrowUpDownIcon class="w-2.5 h-2.5 inline ml-0.5"/></th>
                          <th v-for="kat in gruppenkategorieStore.gruppenkategorien" :key="kat.id"
                              @click="toggleSort('v_teilnehmer', gruppenkategorieSortKey(kat.name))"
                              class="px-3 py-1.5 text-left cursor-pointer hover:text-indigo-600 transition">{{ kat.name }} <ArrowUpDownIcon class="w-2.5 h-2.5 inline ml-0.5"/></th>
                          <th class="px-3 py-1.5 text-right">Aktionen</th>
                        </tr>
                        </thead>
                        <tbody class="divide-y divide-gray-100">
                        <tr v-for="part in paginatedVSubParticipants" :key="part.id" class="hover:bg-indigo-50/30 transition">
                          <td class="px-3 py-1.5 font-semibold text-gray-800" :title="part.loginName">{{ part.firstName }} {{ part.lastName }}</td>
                          <td v-for="kat in gruppenkategorieStore.gruppenkategorien" :key="kat.id" class="px-3 py-1.5 text-gray-600">
                            {{ getGruppenkategorieWerte(part, kat.name).join(', ') }}
                          </td>
                          <td class="px-3 py-1.5 text-right">
                            <button @click="emit('openUserModal', part)" class="text-indigo-600" title="Bearbeiten" aria-label="Bearbeiten">
                              <PencilIcon class="w-3.5 h-3.5 inline"/>
                            </button>
                          </td>
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

    <GruppenkategorieEditorModal :isVisible="showGruppenkategorieModal" :kategorie="selectedKategorie"
                                  @close="showGruppenkategorieModal = false" @save="handleSaveGruppenkategorie"/>
  </section>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue';
import { useGruppenkategorieStore } from '../../../stores/gruppenkategorie';
import {
  ArrowUpDown as ArrowUpDownIcon,
  ChevronDown as ChevronDownIcon,
  ChevronRight as ChevronRightIcon,
  ChevronUp as ChevronUpIcon,
  FileText as FileTextIcon,
  Pencil as PencilIcon,
  Tags as TagsIcon,
  Trash2 as Trash2Icon,
  Upload as UploadIcon,
  Users as UsersIcon,
} from '@lucide/vue';
import PaginationControls from '../../PaginationControls.vue';
import GruppenkategorieEditorModal from './GruppenkategorieEditorModal.vue';
import {
  getGruppenkategorieSortValue,
  getGruppenkategorieWerte,
  gruppenkategorieNameFromSortKey,
  gruppenkategorieSortKey,
  isGruppenkategorieSortKey,
} from '../../../composables/useGruppenkategorieColumns';

const props = defineProps({
  veranstaltungen: Array,
  selectedVid: Number,
  vortraege: Array,
  teilnehmer: Array,
  pageSize: Number,
  canImportVeranstaltung: Boolean
});

const emit = defineEmits(['triggerUpload', 'openVeranstaltungEditor', 'deleteVeranstaltung', 'selectVeranstaltung', 'openUserModal']);

const gruppenkategorieStore = useGruppenkategorieStore();
const expandedKategorien = reactive({});
const newWertByKategorie = reactive({});
const showGruppenkategorieModal = ref(false);
const selectedKategorie = ref(null);

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
  gruppenkategorien: false,
  vortraege: false,
  teilnehmer: false
});

watch(() => props.selectedVid, (newVid) => {
  if (newVid) {
    expandedSections.vortraege = true;
  }
});

watch(() => filters.veranstaltungen, () => { pages.veranstaltungen = 1; });

const toggleKategorie = (id) => {
  expandedKategorien[id] = !expandedKategorien[id];
};

const openGruppenkategorieEditor = (kat) => {
  selectedKategorie.value = kat;
  showGruppenkategorieModal.value = true;
};

const handleSaveGruppenkategorie = async (form) => {
  const ok = form.id
      ? await gruppenkategorieStore.updateGruppenkategorie(props.selectedVid, form.id, form)
      : await gruppenkategorieStore.createGruppenkategorie(props.selectedVid, form);
  if (ok) {
    showGruppenkategorieModal.value = false;
  }
};

const handleDeleteGruppenkategorie = async (kat) => {
  if (confirm(`Soll die Gruppenkategorie "${kat.name}" wirklich gelöscht werden? Alle Zuordnungen bei Teilnehmern werden entfernt.`)) {
    await gruppenkategorieStore.deleteGruppenkategorie(props.selectedVid, kat);
  }
};

const handleAddWert = async (kat) => {
  const wert = (newWertByKategorie[kat.id] || '').trim();
  if (wert) {
    await gruppenkategorieStore.addWert(props.selectedVid, kat.id, wert);
    newWertByKategorie[kat.id] = '';
  }
};

const handleRenameWert = async (wert) => {
  const neuerWert = prompt(`Wert "${wert.wert}" umbenennen in:`, wert.wert);
  if (neuerWert && neuerWert.trim() && neuerWert.trim() !== wert.wert) {
    await gruppenkategorieStore.renameWert(props.selectedVid, wert, neuerWert.trim());
  }
};

const handleRemoveWert = async (wert) => {
  if (confirm(`Soll der Wert "${wert.wert}" wirklich gelöscht werden? Er wird von allen Teilnehmern entfernt.`)) {
    await gruppenkategorieStore.removeWert(props.selectedVid, wert);
  }
};

const formatDate = (d) => d ? new Date(d).toLocaleDateString('de-DE') : '';

const getNestedValue = (obj, path) => {
  if (isGruppenkategorieSortKey(path)) {
    return getGruppenkategorieSortValue(obj, gruppenkategorieNameFromSortKey(path));
  }
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
  const eventParticipants = props.teilnehmer.filter(t => t.veranstaltungIds.includes(props.selectedVid));
  return processList(eventParticipants, '', sorts.v_teilnehmer);
});
const paginatedVSubParticipants = computed(() => paginate(filteredVSubParticipants.value, pages.v_teilnehmer));

</script>

<style scoped>
@reference "tailwindcss";

.btn-primary { @apply rounded-lg bg-indigo-600 px-3 py-1.5 text-white font-bold hover:bg-indigo-700 transition shadow-sm border-none cursor-pointer disabled:opacity-50; }
.btn-secondary { @apply bg-white text-gray-700 px-3 py-1.5 rounded-lg hover:bg-gray-50 font-bold border border-gray-200 transition shadow-sm cursor-pointer disabled:opacity-50; }
.input-field { @apply rounded-lg border border-gray-300 px-2 py-1 text-gray-900 focus:ring-2 focus:ring-indigo-500 bg-white; }
.animate-fade-in { animation: fadeIn 0.3s ease-in-out; }
@keyframes fadeIn { from { opacity: 0; transform: translateY(5px); } to { opacity: 1; transform: translateY(0); } }
</style>
