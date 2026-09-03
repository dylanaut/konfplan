<template>
  <div class="bg-white p-6 rounded-xl shadow-sm border border-gray-100 space-y-4">
    <div class="flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
      <div>
        <h2 class="text-lg font-bold text-gray-900">Onboarding-Status</h2>
        <p class="text-xs text-gray-500 mt-0.5">Nutzer, die noch kein echtes eigenes Passwort vergeben haben (weder selbst per "Passwort vergessen" noch nach einem Admin-Reset).</p>
      </div>
      <div class="flex items-center gap-3">
        <label class="flex items-center gap-1.5 text-xs text-gray-600">
          <input type="checkbox" v-model="nurOhneEchtesPasswort" class="rounded text-indigo-600 focus:ring-indigo-500 h-3.5 w-3.5"/>
          Nur ohne echtes Passwort
        </label>
        <button @click="load" :disabled="loading" class="btn-secondary flex items-center gap-2 text-xs py-1 px-3">
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
      <p class="text-gray-500">{{ status.length === 0 ? 'Keine Nutzer gefunden.' : 'Alle Nutzer haben bereits ein echtes Passwort vergeben.' }}</p>
    </div>

    <div v-else-if="!loading" class="overflow-x-auto border border-gray-100 rounded-lg shadow-sm">
      <table class="min-w-full divide-y divide-gray-200">
        <thead class="bg-gray-50">
        <tr>
          <th class="px-4 py-3 text-left text-xs font-bold text-gray-500 uppercase tracking-wider">LoginName</th>
          <th class="px-4 py-3 text-left text-xs font-bold text-gray-500 uppercase tracking-wider">Rolle</th>
          <th class="px-4 py-3 text-left text-xs font-bold text-gray-500 uppercase tracking-wider">E-Mail</th>
          <th class="px-4 py-3 text-left text-xs font-bold text-gray-500 uppercase tracking-wider">Status</th>
        </tr>
        </thead>
        <tbody class="bg-white divide-y divide-gray-200">
        <tr v-for="n in displayStatus" :key="n.loginName"
            :class="!n.hatEchtesPasswort ? 'bg-amber-50/60' : ''"
            class="hover:bg-gray-50 transition-colors">
          <td class="px-4 py-2 whitespace-nowrap text-xs text-gray-900 font-medium">{{ n.loginName }}</td>
          <td class="px-4 py-2 whitespace-nowrap text-xs text-gray-600">{{ n.role }}</td>
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
import { computed, onMounted, ref } from 'vue';
import api from '../../../api/axios';
import { extractErrorMessage } from '../../../utils/errorMessage';
import { RefreshCw as RefreshCwIcon } from '@lucide/vue';

const status = ref([]);
const loading = ref(false);
const error = ref('');
const nurOhneEchtesPasswort = ref(true);

const ohneEchtesPasswort = computed(() => status.value.filter(n => !n.hatEchtesPasswort));
const displayStatus = computed(() => nurOhneEchtesPasswort.value ? ohneEchtesPasswort.value : status.value);

const load = async () => {
  loading.value = true;
  error.value = '';
  try {
    const res = await api.get('/api/admin/onboarding-status');
    status.value = res.data;
  } catch (e) {
    error.value = 'Fehler beim Laden des Onboarding-Status: ' + extractErrorMessage(e);
  } finally {
    loading.value = false;
  }
};

onMounted(load);
</script>
