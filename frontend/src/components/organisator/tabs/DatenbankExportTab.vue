<template>
  <section class="space-y-4 animate-fade-in">
    <div class="bg-white p-3 rounded-xl border border-gray-100 shadow-sm">
      <h2 class="text-lg font-bold text-gray-800">Datenbank-Export</h2>
      <p class="text-xs text-gray-500 mt-0.5">
        Erstellt ein PostgreSQL-Backup (per <code>pg_dump</code>) der konfplan- und der Keycloak-Datenbank und lädt beide
        gebündelt als ZIP herunter. Ein Restore ist aus Sicherheitsgründen nicht über die Oberfläche möglich - dafür steht
        Administratoren <code>deploy/restore_db.sh</code> zur Verfügung (siehe Deployment-Dokumentation).
      </p>
    </div>

    <div v-if="error" class="bg-red-50 border border-red-200 text-red-700 text-xs rounded-xl p-3">{{ error }}</div>

    <div class="bg-white p-3 rounded-xl border border-gray-100 shadow-sm">
      <button @click="exportBackup" :disabled="exporting" class="btn-primary flex items-center gap-2 text-xs py-1.5 px-3">
        <DownloadIcon class="w-3.5 h-3.5" :class="{'animate-pulse': exporting}"/>
        {{ exporting ? 'Backup wird erstellt...' : 'Backup herunterladen' }}
      </button>
    </div>
  </section>
</template>

<script setup>
import { ref } from 'vue';
import { Download as DownloadIcon } from '@lucide/vue';
import api from '../../../api/axios';

const exporting = ref(false);
const error = ref('');

const filenameFromContentDisposition = (headerValue, fallback) => {
  const match = /filename="?([^";]+)"?/.exec(headerValue ?? '');
  return match ? match[1] : fallback;
};

const exportBackup = async () => {
  exporting.value = true;
  error.value = '';
  try {
    const response = await api.get('/api/administrator/backup/export', { responseType: 'blob' });
    const filename = filenameFromContentDisposition(response.headers['content-disposition'], 'konfplan-backup.zip');
    const url = window.URL.createObjectURL(response.data);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    link.click();
    window.URL.revokeObjectURL(url);
  } catch (e) {
    // responseType 'blob' liefert Fehlerantworten ebenfalls als Blob statt geparstem JSON.
    const body = e.response?.data instanceof Blob ? JSON.parse(await e.response.data.text()) : e.response?.data;
    error.value = 'Backup-Export fehlgeschlagen: ' + (body?.error || body?.message || e.message);
  } finally {
    exporting.value = false;
  }
};
</script>

<style scoped>
@reference "tailwindcss";

.btn-primary { @apply rounded-lg bg-indigo-600 px-3 py-1.5 text-white font-bold hover:bg-indigo-700 transition shadow-sm border-none cursor-pointer disabled:opacity-50; }
.animate-fade-in { animation: fadeIn 0.3s ease-in-out; }
@keyframes fadeIn { from { opacity: 0; transform: translateY(5px); } to { opacity: 1; transform: translateY(0); } }
</style>
