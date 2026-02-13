<template>
  <div class="max-w-7xl mx-auto space-y-6 pb-20">

    <!-- Page Header & Global Export -->
    <div class="flex flex-col md:flex-row md:items-center justify-between gap-4 bg-white p-6 rounded-xl shadow-sm border border-gray-100">
      <div>
        <h1 class="text-2xl font-bold text-gray-900">Admin-Bereich</h1>
        <p class="text-sm text-gray-500">Verwalten Sie Teilnehmer, Vorträge und exportieren Sie die Ergebnisse.</p>
      </div>
      <button @click="downloadExport" class="flex items-center justify-center gap-2 bg-gray-800 text-white px-6 py-2 rounded-lg hover:bg-gray-700 transition shadow-md">
        <DownloadIcon class="w-5 h-5" />
        Prioritäten Export (CSV)
      </button>
    </div>

    <!-- Tab-Navigation -->
    <div class="border-b border-gray-200">
      <nav class="-mb-px flex space-x-8 overflow-x-auto">
        <button v-for="tab in ['participants', 'talks', 'stats']" :key="tab"
                @click="activeTab = tab"
                :class="[activeTab === tab ? 'border-indigo-500 text-indigo-600' : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300', 'whitespace-nowrap py-4 px-1 border-b-2 font-medium text-sm capitalize']">
          {{ tab === 'participants' ? 'Teilnehmer' : tab === 'talks' ? 'Vorträge' : 'Statistiken' }}
        </button>
      </nav>
    </div>

    <!-- TAB: TEILNEHMER -->
    <section v-if="activeTab === 'participants'" class="space-y-4 animate-fade-in">
      <div class="flex flex-col sm:flex-row justify-between items-center gap-4">
        <h2 class="text-xl font-bold text-gray-800">Teilnehmer-Verwaltung</h2>
        <div class="flex gap-2 w-full sm:w-auto">
          <!-- Hidden File Input for CSV -->
          <input type="file" ref="csvInput" class="hidden" @change="handleCsvUpload" accept=".csv" />
          <button @click="$refs.csvInput.click()" class="flex-1 sm:flex-none bg-indigo-50 text-indigo-700 px-4 py-2 rounded-lg text-sm font-bold flex items-center justify-center gap-2 border border-indigo-100 hover:bg-indigo-100">
            <UploadIcon class="w-4 h-4" /> CSV Import
          </button>
          <button @click="openUserModal(null)" class="flex-1 sm:flex-none bg-indigo-600 text-white px-4 py-2 rounded-lg text-sm font-bold hover:bg-indigo-700 transition">
            + Neu
          </button>
        </div>
      </div>

      <div class="bg-white shadow rounded-xl overflow-hidden border border-gray-100">
        <table class="min-w-full divide-y divide-gray-200">
          <thead class="bg-gray-50 text-xs uppercase font-medium text-gray-500">
          <tr>
            <th class="px-6 py-3 text-left">Name / Organisation</th>
            <th class="px-6 py-3 text-left">Email</th>
            <th class="px-6 py-3 text-center">Status</th>
            <th class="px-6 py-3 text-right">Aktionen</th>
          </tr>
          </thead>
          <tbody class="divide-y divide-gray-100 text-sm">
          <tr v-for="p in participants" :key="p.id" class="hover:bg-gray-50 transition">
            <td class="px-6 py-4">
              <div class="font-bold text-gray-900">{{ p.lastName }}, {{ p.firstName }}</div>
              <div class="text-gray-500 text-xs">{{ p.organization }}</div>
            </td>
            <td class="px-6 py-4 text-gray-600">{{ p.email }}</td>
            <td class="px-6 py-4 text-center">
              <button @click="toggleUserStatus(p)" :class="[p.isActive ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700', 'px-3 py-1 rounded-full text-xs font-bold']">
                {{ p.isActive ? 'Aktiv' : 'Inaktiv' }}
              </button>
            </td>
            <td class="px-6 py-4 text-right space-x-3">
              <button @click="deleteUser(p.id)" class="text-red-600 hover:text-red-900"><Trash2Icon class="w-4 h-4" /></button>
            </td>
          </tr>
          </tbody>
        </table>
      </div>
    </section>

    <!-- TAB: VORTRÄGE -->
    <section v-if="activeTab === 'talks'" class="space-y-4 animate-fade-in">
      <div class="flex justify-between items-center">
        <h2 class="text-xl font-bold text-gray-800">Vortrags-Management</h2>
        <button @click="openEditor(null)" class="bg-indigo-600 text-white px-4 py-2 rounded-lg text-sm font-bold">
          + Neuer Vortrag
        </button>
      </div>

      <div class="bg-white shadow rounded-xl overflow-hidden border border-gray-100">
        <table class="min-w-full divide-y divide-gray-200">
          <thead class="bg-gray-50 text-xs uppercase font-medium text-gray-500">
          <tr>
            <th class="px-6 py-3 text-left">Vortrag</th>
            <th class="px-6 py-3 text-left">Referent</th>
            <th class="px-6 py-3 text-center">Version</th>
            <th class="px-6 py-3 text-right">Aktionen</th>
          </tr>
          </thead>
          <tbody class="divide-y divide-gray-100 text-sm">
          <tr v-for="talk in talks" :key="talk.id" class="hover:bg-gray-50 transition">
            <td class="px-6 py-4">
              <div class="font-bold text-gray-900">{{ talk.title }}</div>
              <div class="text-gray-400 text-xs">{{ talk.targetAudience }}</div>
            </td>
            <td class="px-6 py-4 text-gray-600">
              {{ talk.speaker?.lastName }}, {{ talk.speaker?.firstName }}
            </td>
            <td class="px-6 py-4 text-center">
              <span class="bg-gray-100 text-gray-500 px-2 py-1 rounded text-xs font-mono">v{{ talk.version }}</span>
            </td>
            <td class="px-6 py-4 text-right">
              <button @click="openEditor(talk)" class="text-indigo-600 hover:text-indigo-900 font-bold">Bearbeiten</button>
            </td>
          </tr>
          </tbody>
        </table>
      </div>
    </section>

    <!-- TAB: STATISTIKEN -->
    <section v-if="activeTab === 'stats'" class="space-y-6 animate-fade-in">
      <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
        <div v-for="stat in stats" :key="stat.title" class="bg-white p-6 rounded-xl shadow-sm border border-gray-100">
          <h3 class="font-bold text-gray-900 mb-4">{{ stat.title }}</h3>
          <div class="space-y-2">
            <div class="flex justify-between text-xs font-medium text-gray-500 uppercase">
              <span>Prio 1 Stimmen</span>
              <span>{{ stat.countPrio1 }}</span>
            </div>
            <div class="w-full bg-gray-100 rounded-full h-2">
              <div class="bg-indigo-600 h-2 rounded-full" :style="{ width: (stat.countPrio1 * 5) + '%' }"></div>
            </div>
            <div class="flex justify-between text-xs font-medium text-gray-500 uppercase mt-4">
              <span>Top 3 Nennungen</span>
              <span>{{ stat.countTop3 }}</span>
            </div>
            <div class="w-full bg-gray-100 rounded-full h-2">
              <div class="bg-green-500 h-2 rounded-full" :style="{ width: (stat.countTop3 * 5) + '%' }"></div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- Modals -->
    <AdminTalkEditorModal
        :isVisible="showEditModal"
        :talk="selectedTalk"
        :speakers="speakers"
        @close="showEditModal = false"
        @save="handleSaveTalk"
    />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import api from '../api/axios';
import { Download as DownloadIcon, Upload as UploadIcon, Trash2 as Trash2Icon } from 'lucide-vue-next';
import AdminTalkEditorModal from '../components/AdminTalkEditorModal.vue';

// State
const activeTab = ref('participants');
const participants = ref([]);
const talks = ref([]);
const speakers = ref([]);
const stats = ref([]);
const showEditModal = ref(false);
const selectedTalk = ref(null);
const csvInput = ref(null);

// Initial Load
onMounted(() => loadData());

const loadData = async () => {
  try {
    const [pRes, tRes, sRes, stRes] = await Promise.all([
      api.get('/api/admin/participants'),
      api.get('/api/admin/talks'),
      api.get('/api/admin/speakers'),
      api.get('/api/admin/stats')
    ]);
    participants.value = pRes.data;
    talks.value = tRes.data;
    speakers.value = sRes.data;
    stats.value = stRes.data;
  } catch (err) {
    console.error("Datenladefehler:", err);
  }
};

// --- CSV IMPORT ---
const handleCsvUpload = async (event) => {
  const file = event.target.files[0];
  if (!file) return;

  const formData = new FormData();
  formData.append('file', file);

  try {
    const res = await api.post('/api/admin/participants/import', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    });
    alert(res.data);
    loadData();
  } catch (err) {
    alert("Fehler beim Import: " + err.response?.data || err.message);
  } finally {
    event.target.value = ''; // Reset input
  }
};

// --- CSV EXPORT ---
const downloadExport = async () => {
  try {
    const response = await api.get('/api/admin/export/csv', { responseType: 'blob' });
    const url = window.URL.createObjectURL(new Blob([response.data]));
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', `event_prioritaeten_${new Date().toISOString().split('T')[0]}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  } catch (err) {
    alert("Export fehlgeschlagen.");
  }
};

// --- TALK MANAGEMENT (Optimistic Locking) ---
const openEditor = (talk) => {
  selectedTalk.value = talk || { title: '', abstractText: '', targetAudience: '', speaker: { id: null }, version: 0 };
  showEditModal.value = true;
};

const handleSaveTalk = async (updatedTalk) => {
  try {
    await api.put(`/api/admin/talks/${updatedTalk.id || ''}`, updatedTalk);
    showEditModal.value = false;
    loadData();
  } catch (error) {
    if (error.response?.status === 409) {
      alert("Konflikt: Jemand anderes hat diesen Vortrag bearbeitet. Daten werden neu geladen.");
      loadData();
    } else {
      alert("Fehler beim Speichern.");
    }
  }
};

// --- USER ACTIONS ---
const toggleUserStatus = async (user) => {
  try {
    await api.patch(`/api/admin/participants/${user.id}/toggle`);
    user.isActive = !user.isActive;
  } catch (err) { console.error(err); }
};

const deleteUser = async (id) => {
  if (confirm("Benutzer wirklich löschen?")) {
    await api.delete(`/api/admin/participants/${id}`);
    loadData();
  }
};
</script>

<style scoped>
.animate-fade-in {
  animation: fadeIn 0.3s ease-in-out;
}
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(5px); }
  to { opacity: 1; transform: translateY(0); }
}
</style>