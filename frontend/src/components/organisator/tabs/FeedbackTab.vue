<template>
  <div class="bg-white p-6 rounded-xl shadow-sm border border-gray-100 space-y-4">
    <div class="flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
      <h2 class="text-lg font-bold text-gray-900">Verbesserungsvorschläge</h2>

      <div class="flex items-center gap-2 bg-gray-50 p-3 rounded-lg border border-gray-200">
        <label class="text-[10px] font-bold text-gray-500 uppercase">Status:</label>
        <select v-model="filterStatus" class="input-field-sm">
          <option value="">Alle</option>
          <option value="OFFEN">Offen</option>
          <option value="IN_BEARBEITUNG">In Bearbeitung</option>
          <option value="ERLEDIGT">Erledigt</option>
        </select>
      </div>
    </div>

    <div v-if="displayVorschlaege.length === 0" class="text-center py-12 bg-gray-50 rounded-xl border-2 border-dashed border-gray-200">
      <p class="text-gray-500">Keine Verbesserungsvorschläge gefunden.</p>
    </div>

    <div v-else class="overflow-x-auto border border-gray-100 rounded-lg shadow-sm">
      <table class="min-w-full divide-y divide-gray-200">
        <thead class="bg-gray-50">
          <tr>
            <th v-for="col in columns" :key="col.key"
                class="px-4 py-3 text-left text-xs font-bold text-gray-500 uppercase tracking-wider cursor-pointer select-none hover:text-gray-700"
                @click="sortBy(col.key)">
              {{ col.label }}
              <span v-if="sortKey === col.key">{{ sortDir === 'asc' ? '▲' : '▼' }}</span>
            </th>
            <th class="px-4 py-3 text-left text-xs font-bold text-gray-500 uppercase tracking-wider">Aktionen</th>
          </tr>
        </thead>
        <tbody class="bg-white divide-y divide-gray-200">
          <tr v-for="v in displayVorschlaege" :key="v.id" class="hover:bg-gray-50 transition-colors">
            <td class="px-4 py-2 text-xs text-gray-900 font-medium max-w-sm">
              <div>{{ v.titel }}</div>
              <div class="text-gray-500 mt-0.5 whitespace-pre-wrap">{{ v.beschreibung }}</div>
            </td>
            <td class="px-4 py-2 whitespace-nowrap text-xs text-gray-600">
              {{ v.erstellerName }}
              <span class="text-gray-400">({{ v.erstellerRolle }})</span>
            </td>
            <td class="px-4 py-2 whitespace-nowrap text-xs text-gray-600 font-mono">{{ formatDateTime(v.erstelltAm) }}</td>
            <td class="px-4 py-2 whitespace-nowrap">
              <span :class="dringlichkeitBadgeClass(v.dringlichkeit)"
                    class="px-2 py-0.5 rounded-full text-[10px] font-bold uppercase">
                {{ dringlichkeitLabel(v.dringlichkeit) }}
              </span>
            </td>
            <td class="px-4 py-2 whitespace-nowrap text-xs text-gray-600 font-mono">{{ v.release }}</td>
            <td class="px-4 py-2 whitespace-nowrap">
              <select :value="v.status" class="input-field-sm" @change="$emit('updateStatus', v, $event.target.value)">
                <option value="OFFEN">Offen</option>
                <option value="IN_BEARBEITUNG">In Bearbeitung</option>
                <option value="ERLEDIGT">Erledigt</option>
              </select>
            </td>
            <td class="px-4 py-2 whitespace-nowrap text-xs">
              <button @click="$emit('delete', v)" class="text-red-600 hover:underline">Löschen</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue';

const props = defineProps({
  vorschlaege: {
    type: Array,
    required: true
  }
});

defineEmits(['updateStatus', 'delete']);

const filterStatus = ref('');
const sortKey = ref('erstelltAm');
const sortDir = ref('desc');

const columns = [
  { key: 'titel', label: 'Titel' },
  { key: 'erstellerName', label: 'Von' },
  { key: 'erstelltAm', label: 'Am' },
  { key: 'dringlichkeit', label: 'Dringlichkeit' },
  { key: 'release', label: 'Release' },
  { key: 'status', label: 'Status' }
];

const DRINGLICHKEIT_RANG = { NIEDRIG: 0, MITTEL: 1, HOCH: 2, KRITISCH: 3 };
const STATUS_RANG = { OFFEN: 0, IN_BEARBEITUNG: 1, ERLEDIGT: 2 };

const sortBy = (key) => {
  if (sortKey.value === key) {
    sortDir.value = sortDir.value === 'asc' ? 'desc' : 'asc';
  } else {
    sortKey.value = key;
    sortDir.value = 'asc';
  }
};

const sortValue = (v, key) => {
  if (key === 'dringlichkeit') return DRINGLICHKEIT_RANG[v.dringlichkeit] ?? -1;
  if (key === 'status') return STATUS_RANG[v.status] ?? -1;
  if (key === 'erstelltAm') return new Date(v.erstelltAm).getTime();
  return (v[key] ?? '').toString().toLowerCase();
};

const displayVorschlaege = computed(() => {
  let result = [...props.vorschlaege];
  if (filterStatus.value) {
    result = result.filter(v => v.status === filterStatus.value);
  }
  const dir = sortDir.value === 'asc' ? 1 : -1;
  result.sort((a, b) => {
    const av = sortValue(a, sortKey.value);
    const bv = sortValue(b, sortKey.value);
    if (av < bv) return -1 * dir;
    if (av > bv) return 1 * dir;
    return 0;
  });
  return result;
});

const dringlichkeitLabel = (d) => ({
  NIEDRIG: 'Niedrig',
  MITTEL: 'Mittel',
  HOCH: 'Hoch',
  KRITISCH: 'Kritisch'
}[d] ?? d);

const dringlichkeitBadgeClass = (d) => ({
  NIEDRIG: 'bg-gray-100 text-gray-700',
  MITTEL: 'bg-blue-100 text-blue-700',
  HOCH: 'bg-amber-100 text-amber-700',
  KRITISCH: 'bg-red-100 text-red-700'
}[d] ?? 'bg-gray-100 text-gray-700');

const formatDateTime = (dateTimeString) => {
  if (!dateTimeString) return '';
  const date = new Date(dateTimeString);
  return date.toLocaleString('de-DE', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  });
};
</script>

<style scoped>
@reference "tailwindcss";

.input-field-sm {
  @apply rounded border border-gray-300 px-2 py-1 text-xs text-gray-900 focus:ring-1 focus:ring-indigo-500 bg-white;
}
</style>
