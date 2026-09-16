<template>
  <div class="bg-white p-6 rounded-xl shadow-sm border border-gray-100 space-y-4">
    <div class="flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
      <div>
        <h2 class="text-lg font-bold text-gray-900">Onboarding-Status</h2>
        <p class="text-xs text-gray-500 mt-0.5">Nutzer, die noch kein echtes eigenes Passwort vergeben haben (weder selbst per "Passwort vergessen" noch nach einem Organisator-Reset).</p>
      </div>
      <div class="flex items-center gap-3">
        <input v-model="filterText" placeholder="Suchen..." class="input-field text-xs py-1 px-2"/>
        <label class="flex items-center gap-1.5 text-xs text-gray-600 whitespace-nowrap">
          <input type="checkbox" v-model="nurOhneEchtesPasswort" class="rounded text-indigo-600 focus:ring-indigo-500 h-3.5 w-3.5"/>
          Nur ohne echtes Passwort
        </label>
        <button @click="load" :disabled="loading" class="btn-secondary flex items-center gap-2 text-xs py-1 px-3 whitespace-nowrap">
          <RefreshCwIcon class="w-3.5 h-3.5" :class="{'animate-spin': loading}"/>
          Aktualisieren
        </button>
      </div>
    </div>

    <div v-if="error" class="bg-red-50 border border-red-200 text-red-700 text-xs rounded-xl p-3">{{ error }}</div>

    <p v-if="!loading && !error" class="text-xs text-gray-500">
      {{ ohneEchtesPasswort.length }} von {{ status.length }} Nutzern haben noch kein echtes Passwort vergeben.
    </p>

    <div v-if="!loading && displayStatus.length === 0" class="text-center py-12 bg-gray-50 rounded-xl border-2 border-dashed border-gray-200">
      <p class="text-gray-500">{{ status.length === 0 ? 'Keine Nutzer gefunden.' : 'Keine Treffer.' }}</p>
    </div>

    <div v-else-if="!loading" class="overflow-x-auto border border-gray-100 rounded-lg shadow-sm">
      <table class="min-w-full divide-y divide-gray-200">
        <thead class="bg-gray-50 text-[9px] uppercase font-bold text-gray-500">
        <tr>
          <th @click="toggleSort('loginName')" class="px-4 py-1.5 text-left cursor-pointer hover:text-indigo-600 transition">LoginName <ArrowUpDownIcon class="w-3 h-3 inline ml-0.5"/></th>
          <th @click="toggleSort('role')" class="px-4 py-1.5 text-left cursor-pointer hover:text-indigo-600 transition">Rolle <ArrowUpDownIcon class="w-3 h-3 inline ml-0.5"/></th>
          <th v-for="kategorieName in gruppenkategorieNamen" :key="kategorieName"
              @click="toggleSort(gruppenkategorieSortKey(kategorieName))"
              class="px-4 py-1.5 text-left cursor-pointer hover:text-indigo-600 transition">{{ kategorieName }} <ArrowUpDownIcon class="w-3 h-3 inline ml-0.5"/></th>
          <th @click="toggleSort('email')" class="px-4 py-1.5 text-left cursor-pointer hover:text-indigo-600 transition">E-Mail <ArrowUpDownIcon class="w-3 h-3 inline ml-0.5"/></th>
          <th @click="toggleSort('hatEchtesPasswort')" class="px-4 py-1.5 text-left cursor-pointer hover:text-indigo-600 transition">Status <ArrowUpDownIcon class="w-3 h-3 inline ml-0.5"/></th>
        </tr>
        </thead>
        <tbody class="bg-white divide-y divide-gray-200">
        <tr v-for="n in displayStatus" :key="n.loginName"
            :class="!n.hatEchtesPasswort ? 'bg-amber-50/60' : ''"
            class="hover:bg-gray-50 transition-colors">
          <td class="px-4 py-2 whitespace-nowrap text-xs text-gray-900 font-medium">{{ n.loginName }}</td>
          <td class="px-4 py-2 whitespace-nowrap text-xs text-gray-600">{{ n.role }}</td>
          <td v-for="kategorieName in gruppenkategorieNamen" :key="kategorieName"
              class="px-4 py-2 whitespace-nowrap text-xs text-gray-600">
            {{ getGruppenkategorieWerte(n, kategorieName).join(', ') || '—' }}
          </td>
          <td class="px-4 py-2 whitespace-nowrap text-xs text-gray-600">{{ n.email || '—' }}</td>
          <td class="px-4 py-2 whitespace-nowrap">
              <span :class="n.hatEchtesPasswort ? 'bg-green-100 text-green-700' : 'bg-amber-100 text-amber-700'"
                    class="px-2 py-0.5 rounded-full text-[10px] font-bold uppercase">
                {{ n.hatEchtesPasswort ? 'Echtes Passwort' : 'Noch kein echtes Passwort' }}
              </span>
          </td>
        </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import api from '../../../api/axios';
import { extractErrorMessage } from '../../../utils/errorMessage';
import { RefreshCw as RefreshCwIcon, ArrowUpDown as ArrowUpDownIcon } from '@lucide/vue';
import {
  getGruppenkategorieSortValue,
  getGruppenkategorieWerte,
  gruppenkategorieNameFromSortKey,
  gruppenkategorieSortKey,
  isGruppenkategorieSortKey,
} from '../../../composables/useGruppenkategorieColumns';

const status = ref([]);
const loading = ref(false);
const error = ref('');
const nurOhneEchtesPasswort = ref(true);
const filterText = ref('');
const sortConfig = reactive({ key: 'loginName', dir: 'asc' });

const ohneEchtesPasswort = computed(() => status.value.filter(n => !n.hatEchtesPasswort));

// Gruppenkategorie-Namen (#690), über alle Nutzer hinweg, für die dynamischen Spalten.
const gruppenkategorieNamen = computed(() => {
  const namen = new Set();
  status.value.forEach(n => Object.keys(n.gruppenwerteByKategorie || {}).forEach(name => namen.add(name)));
  return Array.from(namen).sort();
});

// gruppenSearchText als vorberechnetes, rein für die Volltextsuche genutztes Feld, da die
// Gruppenkategorie-Werte verschachtelt unter gruppenwerteByKategorie liegen statt als flaches
// Array/String.
const angereichert = computed(() => status.value.map(n => ({
  ...n,
  gruppenSearchText: Object.values(n.gruppenwerteByKategorie || {}).flat().join(', '),
})));

const getSortValue = (item, key) => {
  if (isGruppenkategorieSortKey(key)) {
    return getGruppenkategorieSortValue(item, gruppenkategorieNameFromSortKey(key));
  }
  return item[key];
};

const displayStatus = computed(() => {
  let list = nurOhneEchtesPasswort.value
    ? angereichert.value.filter(n => !n.hatEchtesPasswort)
    : angereichert.value;

  if (filterText.value) {
    const f = filterText.value.toLowerCase();
    list = list.filter(n => Object.values(n).some(v => String(v ?? '').toLowerCase().includes(f)));
  }

  return [...list].sort((a, b) => {
    const valA = getSortValue(a, sortConfig.key);
    const valB = getSortValue(b, sortConfig.key);
    if (typeof valA === 'boolean' && typeof valB === 'boolean') {
      return sortConfig.dir === 'asc' ? Number(valA) - Number(valB) : Number(valB) - Number(valA);
    }
    const cmp = String(valA ?? '').localeCompare(String(valB ?? ''));
    return sortConfig.dir === 'asc' ? cmp : -cmp;
  });
});

const toggleSort = (field) => {
  if (sortConfig.key === field) {
    sortConfig.dir = sortConfig.dir === 'asc' ? 'desc' : 'asc';
  } else {
    sortConfig.key = field;
    sortConfig.dir = 'asc';
  }
};

const load = async () => {
  loading.value = true;
  error.value = '';
  try {
    const res = await api.get('/api/organisator/onboarding-status');
    status.value = res.data;
  } catch (e) {
    error.value = 'Fehler beim Laden des Onboarding-Status: ' + extractErrorMessage(e);
  } finally {
    loading.value = false;
  }
};

onMounted(load);
</script>
