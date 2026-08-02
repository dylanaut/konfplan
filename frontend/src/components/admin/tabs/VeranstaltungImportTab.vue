<template>
  <section class="space-y-4 animate-fade-in">
    <div class="flex justify-between items-center bg-white p-3 rounded-xl border border-gray-100 shadow-sm">
      <div>
        <h2 class="text-lg font-bold text-gray-800">Verzeichnis-Import</h2>
        <p class="text-xs text-gray-500 mt-0.5">Importiert einen kompletten CSV-Satz aus einem Server-Verzeichnis und legt dabei eine neue Veranstaltung an.</p>
      </div>
      <button @click="loadDatasets" :disabled="loading" class="btn-secondary flex items-center gap-2 text-xs py-1 px-3">
        <RefreshCwIcon class="w-3.5 h-3.5" :class="{'animate-spin': loading}"/>
        Aktualisieren
      </button>
    </div>

    <div v-if="error" class="bg-red-50 border border-red-200 text-red-700 text-xs rounded-xl p-3">{{ error }}</div>

    <div class="bg-white p-3 rounded-xl border border-gray-100 shadow-sm space-y-2">
      <div>
        <h2 class="text-lg font-bold text-gray-800">ZIP-Upload</h2>
        <p class="text-xs text-gray-500 mt-0.5">Lädt ein ZIP mit dem kompletten CSV-Satz hoch und legt daraus eine neue Veranstaltung an.</p>
      </div>
      <div v-if="uploadError" class="bg-red-50 border border-red-200 text-red-700 text-xs rounded-xl p-3">{{ uploadError }}</div>
      <div class="flex items-center gap-2">
        <input type="file" accept=".zip" @change="onZipSelected" :disabled="uploading"
               class="text-xs file:mr-2 file:py-1.5 file:px-3 file:rounded-lg file:border-0 file:font-bold file:bg-gray-100 file:text-gray-700 hover:file:bg-gray-200 cursor-pointer"/>
        <button @click="uploadZip" :disabled="!zipFile || uploading" class="btn-primary text-xs py-1 px-3 whitespace-nowrap">
          {{ uploading ? 'Importiere...' : 'Hochladen und importieren' }}
        </button>
      </div>
    </div>

    <div v-if="!loading && datasets.length === 0" class="bg-white p-6 rounded-xl border border-gray-100 shadow-sm text-center text-xs text-gray-500">
      Keine Veranstaltungsverzeichnisse gefunden.
    </div>

    <div class="bg-white shadow rounded-xl overflow-hidden border border-gray-100">
      <table class="min-w-full divide-y divide-gray-200">
        <thead class="bg-gray-50 text-[9px] uppercase font-bold text-gray-500">
        <tr>
          <th class="px-4 py-1.5 text-left font-bold">Verzeichnis</th>
          <th class="px-4 py-1.5 text-left font-bold">Dateien</th>
          <th class="px-4 py-1.5 text-right font-bold">Aktion</th>
        </tr>
        </thead>
        <tbody class="text-xs">
        <tr v-for="ds in datasets" :key="ds.name" class="hover:bg-gray-50 transition align-top">
          <td class="px-4 py-2 font-bold">{{ ds.name }}</td>
          <td class="px-4 py-2">
            <div class="flex flex-wrap gap-1">
              <span v-for="f in ds.vorhandeneDateien" :key="f" class="px-1.5 py-0.5 rounded bg-green-50 text-green-700 border border-green-100">{{ f }}</span>
              <span v-for="f in ds.fehlendeDateien" :key="f" class="px-1.5 py-0.5 rounded bg-red-50 text-red-700 border border-red-100" :title="'Fehlt: ' + f">{{ f }}</span>
            </div>
          </td>
          <td class="px-4 py-2 text-right">
            <button @click="doImport(ds.name)"
                    :disabled="!ds.auswaehlbar || importingName !== null"
                    :class="{'opacity-50 cursor-not-allowed': !ds.auswaehlbar || importingName !== null}"
                    class="btn-primary text-xs py-1 px-3">
              {{ importingName === ds.name ? 'Importiere...' : 'Importieren' }}
            </button>
          </td>
        </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { RefreshCw as RefreshCwIcon } from '@lucide/vue';
import api from '../../../api/axios';

const emit = defineEmits(['imported']);

const datasets = ref([]);
const loading = ref(false);
const importingName = ref(null);
const error = ref('');

const zipFile = ref(null);
const uploading = ref(false);
const uploadError = ref('');

const loadDatasets = async () => {
  loading.value = true;
  error.value = '';
  try {
    const res = await api.get('/api/admin/veranstaltung-import/datasets');
    datasets.value = res.data;
  } catch (e) {
    error.value = 'Fehler beim Laden der Verzeichnisse: ' + (e.response?.data || e.message);
  } finally {
    loading.value = false;
  }
};

const doImport = async (name) => {
  importingName.value = name;
  error.value = '';
  try {
    const res = await api.post(`/api/admin/veranstaltung-import/datasets/${encodeURIComponent(name)}/import`);
    emit('imported', res.data);
    await loadDatasets();
  } catch (e) {
    error.value = `Import von '${name}' fehlgeschlagen: ` + (e.response?.data || e.message);
  } finally {
    importingName.value = null;
  }
};

const onZipSelected = (e) => {
  zipFile.value = e.target.files[0] ?? null;
};

const uploadZip = async () => {
  if (!zipFile.value) return;
  uploading.value = true;
  uploadError.value = '';
  try {
    const formData = new FormData();
    formData.append('file', zipFile.value);
    const res = await api.post('/api/admin/veranstaltung-import/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    emit('imported', res.data);
    zipFile.value = null;
    await loadDatasets();
  } catch (e) {
    uploadError.value = 'ZIP-Import fehlgeschlagen: ' + (e.response?.data || e.message);
  } finally {
    uploading.value = false;
  }
};

onMounted(loadDatasets);
</script>

<style scoped>
.btn-primary { @apply rounded-lg bg-indigo-600 px-3 py-1.5 text-white font-bold hover:bg-indigo-700 transition shadow-sm border-none cursor-pointer disabled:opacity-50; }
.btn-secondary { @apply bg-white text-gray-700 px-3 py-1.5 rounded-lg hover:bg-gray-50 font-bold border border-gray-200 transition shadow-sm cursor-pointer disabled:opacity-50; }
.animate-fade-in { animation: fadeIn 0.3s ease-in-out; }
@keyframes fadeIn { from { opacity: 0; transform: translateY(5px); } to { opacity: 1; transform: translateY(0); } }
</style>
