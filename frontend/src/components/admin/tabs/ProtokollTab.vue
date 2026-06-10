<template>
  <div class="bg-white p-6 rounded-xl shadow-sm border border-gray-100 space-y-4">
    <div class="flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
      <h2 class="text-lg font-bold text-gray-900">Protokolleinträge</h2>

      <!-- CSV Export Bereich -->
      <div class="flex flex-wrap items-center gap-2 bg-gray-50 p-3 rounded-lg border border-gray-200">
        <div class="flex items-center gap-2">
          <label class="text-[10px] font-bold text-gray-500 uppercase">Von:</label>
          <input type="date" v-model="exportStart" class="input-field-sm" />
        </div>
        <div class="flex items-center gap-2">
          <label class="text-[10px] font-bold text-gray-500 uppercase">Bis:</label>
          <input type="date" v-model="exportEnd" class="input-field-sm" />
        </div>
        <button @click="exportCSV" class="btn-secondary flex items-center gap-2 text-xs py-1">
          <DownloadIcon class="w-3.5 h-3.5" />
          Export CSV
        </button>
      </div>
    </div>

    <!-- Filter Bereich -->
    <div class="grid grid-cols-1 md:grid-cols-3 gap-4 bg-indigo-50/50 p-4 rounded-xl border border-indigo-100">
      <div class="space-y-1">
        <label class="text-[10px] font-bold text-indigo-900 uppercase tracking-wider">Akteur filtern</label>
        <input v-model="filterAkteur" placeholder="Name oder E-Mail..." class="input-field w-full text-xs" />
      </div>
      <div class="space-y-1">
        <label class="text-[10px] font-bold text-indigo-900 uppercase tracking-wider">Kategorie filtern</label>
        <select v-model="filterKategorie" class="input-field w-full text-xs">
          <option value="">Alle Kategorien</option>
          <option v-for="kat in kategorien" :key="kat" :value="kat">{{ kat }}</option>
        </select>
      </div>
      <div class="space-y-1">
        <label class="text-[10px] font-bold text-indigo-900 uppercase tracking-wider">Ereignis filtern</label>
        <input v-model="filterEreignis" placeholder="Stichwort..." class="input-field w-full text-xs" />
      </div>
    </div>

    <div v-if="displayProtokolle.length === 0" class="text-center py-12 bg-gray-50 rounded-xl border-2 border-dashed border-gray-200">
      <p class="text-gray-500">Keine Einträge gefunden, die den Filtern entsprechen.</p>
    </div>

    <div v-else class="overflow-x-auto border border-gray-100 rounded-lg shadow-sm">
      <table class="min-w-full divide-y divide-gray-200">
        <thead class="bg-gray-50">
          <tr>
            <th @click="toggleSort('zeitpunkt')" class="sortable-header">
              Zeitpunkt
              <component :is="getSortIcon('zeitpunkt')" class="w-3 h-3 ml-1" />
            </th>
            <th @click="toggleSort('akteur')" class="sortable-header">
              Akteur
              <component :is="getSortIcon('akteur')" class="w-3 h-3 ml-1" />
            </th>
            <th @click="toggleSort('kategorie')" class="sortable-header">
              Kategorie
              <component :is="getSortIcon('kategorie')" class="w-3 h-3 ml-1" />
            </th>
            <th class="px-4 py-3 text-left text-xs font-bold text-gray-500 uppercase tracking-wider">
              Ereignis
            </th>
            <th class="px-4 py-3 text-left text-xs font-bold text-gray-500 uppercase tracking-wider">
              Details
            </th>
            <th class="px-4 py-3 text-left text-xs font-bold text-gray-500 uppercase tracking-wider">
              Ref-ID
            </th>
          </tr>
        </thead>
        <tbody class="bg-white divide-y divide-gray-200">
          <tr v-for="entry in displayProtokolle" :key="entry.id" class="hover:bg-gray-50 transition-colors">
            <td class="px-4 py-2 whitespace-nowrap text-xs text-gray-600 font-mono">{{ formatDateTime(entry.zeitpunkt) }}</td>
            <td class="px-4 py-2 whitespace-nowrap text-xs font-medium text-gray-900">{{ entry.akteur }}</td>
            <td class="px-4 py-2 whitespace-nowrap">
              <span :class="getKategorieClass(entry.kategorie)" class="px-2 py-0.5 rounded-full text-[10px] font-bold uppercase">
                {{ entry.kategorie }}
              </span>
            </td>
            <td class="px-4 py-2 text-xs text-gray-900 font-medium">{{ entry.ereignis }}</td>
            <td class="px-4 py-2 text-xs text-gray-500 max-w-xs truncate" :title="entry.details">{{ entry.details || '-' }}</td>
            <td class="px-4 py-2 whitespace-nowrap text-xs text-gray-400">#{{ entry.referenzId || '-' }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue';
import { Download as DownloadIcon, ArrowUpDown, ChevronUp, ChevronDown } from '@lucide/vue';

const props = defineProps({
  protokolle: {
    type: Array,
    required: true
  }
});

// Filter State
const filterAkteur = ref('');
const filterKategorie = ref('');
const filterEreignis = ref('');

// Sort State
const sortKey = ref('zeitpunkt');
const sortOrder = ref('desc'); // 'asc' or 'desc'

// Export Range State (default to last 7 days)
const exportEnd = ref(new Date().toISOString().substr(0, 10));
const exportStart = ref(new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString().substr(0, 10));

const kategorien = computed(() => {
  return [...new Set(props.protokolle.map(p => p.kategorie))].sort();
});

const toggleSort = (key) => {
  if (sortKey.value === key) {
    sortOrder.value = sortOrder.value === 'asc' ? 'desc' : 'asc';
  } else {
    sortKey.value = key;
    sortOrder.value = 'asc';
  }
};

const getSortIcon = (key) => {
  if (sortKey.value !== key) return ArrowUpDown;
  return sortOrder.value === 'asc' ? ChevronUp : ChevronDown;
};

const displayProtokolle = computed(() => {
  let result = [...props.protokolle];

  // Filtering
  if (filterAkteur.value) {
    const search = filterAkteur.value.toLowerCase();
    result = result.filter(p => p.akteur.toLowerCase().includes(search));
  }
  if (filterKategorie.value) {
    result = result.filter(p => p.kategorie === filterKategorie.value);
  }
  if (filterEreignis.value) {
    const search = filterEreignis.value.toLowerCase();
    result = result.filter(p => p.ereignis.toLowerCase().includes(search));
  }

  // Sorting
  result.sort((a, b) => {
    let valA = a[sortKey.value];
    let valB = b[sortKey.value];

    if (sortKey.value === 'zeitpunkt') {
      valA = new Date(valA);
      valB = new Date(valB);
    }

    if (valA < valB) return sortOrder.value === 'asc' ? -1 : 1;
    if (valA > valB) return sortOrder.value === 'asc' ? 1 : -1;
    return 0;
  });

  return result;
});

const exportCSV = () => {
  const start = new Date(exportStart.value);
  start.setHours(0,0,0,0);
  const end = new Date(exportEnd.value);
  end.setHours(23,59,59,999);

  const toExport = props.protokolle.filter(p => {
    const d = new Date(p.zeitpunkt);
    return d >= start && d <= end;
  }).sort((a,b) => new Date(a.zeitpunkt) - new Date(b.zeitpunkt));

  if (toExport.length === 0) {
    alert("Keine Einträge im gewählten Zeitraum gefunden.");
    return;
  }

  const headers = ['Zeitpunkt', 'Akteur', 'Kategorie', 'Ereignis', 'Details', 'ReferenzId'];
  const rows = toExport.map(p => [
    formatDateTime(p.zeitpunkt),
    p.akteur,
    p.kategorie,
    p.ereignis,
    p.details || '',
    p.referenzId || ''
  ]);

  const csvContent = [
    headers.join(';'),
    ...rows.map(r => r.map(field => `"${String(field).replace(/"/g, '""')}"`).join(';'))
  ].join('\n');

  const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
  const link = document.createElement('a');
  const url = URL.createObjectURL(blob);
  link.setAttribute('href', url);
  link.setAttribute('download', `protokoll_export_${exportStart.value}_bis_${exportEnd.value}.csv`);
  link.style.visibility = 'hidden';
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
};

const formatDateTime = (dateTimeString) => {
  if (!dateTimeString) return '';
  const date = new Date(dateTimeString);
  return date.toLocaleString('de-DE', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  });
};

const getKategorieClass = (kat) => {
  const classes = {
    'LOGIN': 'bg-blue-100 text-blue-700',
    'SECURITY': 'bg-red-100 text-red-700',
    'SYSTEM': 'bg-gray-100 text-gray-700',
    'VERANSTALTUNG': 'bg-indigo-100 text-indigo-700',
    'NUTZER': 'bg-green-100 text-green-700',
    'PLANUNG': 'bg-purple-100 text-purple-700'
  };
  return classes[kat] || 'bg-gray-100 text-gray-600';
};
</script>

<style scoped>
.input-field {
  @apply rounded-lg border border-gray-300 px-3 py-2 text-gray-900 focus:ring-2 focus:ring-indigo-500 bg-white;
}
.input-field-sm {
  @apply rounded border border-gray-300 px-2 py-1 text-xs text-gray-900 focus:ring-1 focus:ring-indigo-500 bg-white;
}
.btn-secondary {
  @apply bg-white text-gray-700 px-3 py-1.5 rounded-lg hover:bg-gray-50 font-bold border border-gray-200 transition shadow-sm cursor-pointer;
}
.sortable-header {
  @apply px-4 py-3 text-left text-xs font-bold text-gray-500 uppercase tracking-wider cursor-pointer hover:bg-gray-100 select-none;
}
</style>
