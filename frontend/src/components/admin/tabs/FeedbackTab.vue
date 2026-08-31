<template>
  <div class="bg-white p-6 rounded-xl shadow-sm border border-gray-100 space-y-4">
    <div class="flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
      <h2 class="text-lg font-bold text-gray-900">Verbesserungsvorschläge</h2>

      <div class="flex items-center gap-2 bg-gray-50 p-3 rounded-lg border border-gray-200">
        <label class="text-[10px] font-bold text-gray-500 uppercase">Status:</label>
        <select v-model="filterStatus" class="input-field-sm">
          <option value="">Alle</option>
          <option value="OFFEN">Offen</option>
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
            <th class="px-4 py-3 text-left text-xs font-bold text-gray-500 uppercase tracking-wider">Titel</th>
            <th class="px-4 py-3 text-left text-xs font-bold text-gray-500 uppercase tracking-wider">Von</th>
            <th class="px-4 py-3 text-left text-xs font-bold text-gray-500 uppercase tracking-wider">Am</th>
            <th class="px-4 py-3 text-left text-xs font-bold text-gray-500 uppercase tracking-wider">Status</th>
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
              <span :class="v.status === 'ERLEDIGT' ? 'bg-green-100 text-green-700' : 'bg-amber-100 text-amber-700'"
                    class="px-2 py-0.5 rounded-full text-[10px] font-bold uppercase">
                {{ v.status === 'ERLEDIGT' ? 'Erledigt' : 'Offen' }}
              </span>
            </td>
            <td class="px-4 py-2 whitespace-nowrap text-xs flex gap-3">
              <button @click="$emit('toggleStatus', v)" class="text-indigo-600 hover:underline">
                {{ v.status === 'ERLEDIGT' ? 'Als offen markieren' : 'Als erledigt markieren' }}
              </button>
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

defineEmits(['toggleStatus', 'delete']);

const filterStatus = ref('');

const displayVorschlaege = computed(() => {
  let result = [...props.vorschlaege];
  if (filterStatus.value) {
    result = result.filter(v => v.status === filterStatus.value);
  }
  return result;
});

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
