<template>
  <div class="max-w-7xl mx-auto space-y-6 pb-20">

    <!-- Page Header & Veranstaltungsauswahl -->
    <div class="flex flex-col md:flex-row md:items-center justify-between gap-4 bg-white p-6 rounded-xl shadow-sm border border-gray-100">
      <div class="flex-1">
        <h1 class="text-2xl font-bold text-gray-900">Admin-Bereich</h1>
        <div class="mt-4 flex items-center gap-3">
          <label class="text-sm font-bold text-gray-500 uppercase tracking-wider">Aktive Veranstaltung:</label>
          <select v-model="selectedVid" @change="loadData" class="input-field max-w-md border-indigo-200 focus:ring-indigo-500">
            <option :value="null">Bitte wählen...</option>
            <option v-for="v in veranstaltungen" :key="v.id" :value="v.id">
              {{ v.name }} ({{ formatDate(v.beginntAm) }})
            </option>
          </select>
        </div>
      </div>
      <button v-if="selectedVid" @click="downloadExport" class="flex items-center justify-center gap-2 bg-gray-800 text-white px-6 py-2 rounded-lg hover:bg-gray-700 transition shadow-md">
        <DownloadIcon class="w-5 h-5"/> Prioritäten Export (CSV)
      </button>
    </div>

    <!-- Tab-Navigation -->
    <div v-if="selectedVid" class="border-b border-gray-200">
      <nav class="-mb-px flex space-x-8 overflow-x-auto">
        <button v-for="tab in ['veranstaltungen', 'gebäude', 'räume', 'benutzer', 'vorträge', 'slots', 'planung', 'stats']" :key="tab"
                @click="activeTab = tab"
                :class="[activeTab === tab ? 'border-indigo-500 text-indigo-600' : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300', 'whitespace-nowrap py-4 px-1 border-b-2 font-medium text-sm capitalize']">
          {{ tab === 'planung' ? 'Vortragsplanung' : tab }}
        </button>
      </nav>
    </div>

    <!-- Platzhalter -->
    <div v-if="!selectedVid" class="bg-indigo-50 p-10 rounded-2xl text-center border-2 border-dashed border-indigo-200">
      <div class="text-indigo-400 mb-4 flex justify-center"><CalendarIcon class="w-12 h-12"/></div>
      <h2 class="text-xl font-bold text-indigo-900">Keine Veranstaltung ausgewählt</h2>
      <p class="text-indigo-600 mt-2">Bitte wählen Sie oben eine Veranstaltung aus, um die Details zu verwalten.</p>
    </div>

    <!-- TABS -->
    <template v-if="selectedVid">
      <!-- TAB: VERANSTALTUNGEN -->
      <section v-if="activeTab === 'veranstaltungen'" class="space-y-4 animate-fade-in">
        <div class="flex justify-between items-center">
          <h2 class="text-xl font-bold text-gray-800">Veranstaltungs-Management</h2>
          <div class="flex flex-wrap gap-2">
            <button @click="triggerUpload('/api/veranstaltungen/import')" class="btn-secondary">CSV Import</button>
            <button @click="openVeranstaltungEditor(null)" class="btn-primary">+ Neu</button>
          </div>
        </div>
        <div class="bg-white shadow rounded-xl overflow-hidden border border-gray-100">
          <table class="min-w-full divide-y divide-gray-200">
            <thead class="bg-gray-50 text-xs uppercase font-medium text-gray-500">
            <tr>
              <th class="px-6 py-3 text-left">Veranstaltung</th>
              <th class="px-6 py-3 text-left">Zeitraum</th>
              <th class="px-6 py-3 text-left">Gebäude</th>
              <th class="px-6 py-3 text-right">Aktionen</th>
            </tr>
            </thead>
            <tbody class="divide-y divide-gray-100 text-sm">
            <tr v-for="v in veranstaltungen" :key="v.id" class="hover:bg-gray-50 transition">
              <td class="px-6 py-4 flex items-center gap-3">
                <img v-if="v.logo" :src="v.logo" class="w-8 h-8 rounded object-contain border" />
                <div class="font-bold text-gray-900">{{ v.name }}</div>
              </td>
              <td class="px-6 py-4 text-gray-600">{{ formatDate(v.beginntAm) }}</td>
              <td class="px-6 py-4 text-gray-600 text-xs">
                <div class="flex flex-wrap gap-1">
                  <span v-for="g in v.gebaeude" :key="g.id" class="bg-gray-100 px-2 py-0.5 rounded">{{ g.name }}</span>
                </div>
              </td>
              <td class="px-6 py-4 text-right space-x-3">
                <button @click="openVeranstaltungEditor(v)" class="text-indigo-600 hover:underline">Bearbeiten</button>
                <button @click="deleteVeranstaltung(v.id)" class="text-red-600 hover:text-red-900"><Trash2Icon class="w-4 h-4 inline"/></button>
              </td>
            </tr>
            </tbody>
          </table>
        </div>
      </section>

      <!-- TAB: RÄUME -->
      <section v-if="activeTab === 'räume'" class="space-y-4 animate-fade-in">
        <div class="flex justify-between items-center">
          <h2 class="text-xl font-bold text-gray-800">Raum-Management</h2>
          <button @click="openRaumEditor(null)" class="btn-primary">+ Neuer Raum</button>
        </div>
        <div class="bg-white shadow rounded-xl overflow-hidden border border-gray-100">
          <table class="min-w-full divide-y divide-gray-200">
            <thead class="bg-gray-50 text-xs uppercase font-medium text-gray-500">
            <tr>
              <th class="px-6 py-3 text-left">Raum (Gebäude)</th>
              <th class="px-6 py-3 text-center">Kapazität</th>
              <th class="px-6 py-3 text-right">Aktionen</th>
            </tr>
            </thead>
            <tbody class="divide-y divide-gray-100 text-sm">
            <tr v-for="r in raeume" :key="r.id" class="hover:bg-gray-50 transition">
              <td class="px-6 py-4">
                <div class="font-bold text-gray-900">{{ r.name }}</div>
                <div class="text-gray-400 text-xs">{{ r.gebaeude?.name }} | {{ r.etage || 'k.A.' }}</div>
              </td>
              <td class="px-6 py-4 text-center">{{ r.kapazitaet }}</td>
              <td class="px-6 py-4 text-right space-x-3">
                <button @click="openRaumEditor(r)" class="text-indigo-600">Bearbeiten</button>
                <button @click="deleteRaum(r)" class="text-red-600"><Trash2Icon class="w-4 h-4 inline"/></button>
              </td>
            </tr>
            </tbody>
          </table>
        </div>
      </section>

      <!-- ... (andere Tabs wie bisher) ... -->

    </template>

    <!-- Modals -->
    <VeranstaltungEditorModal :isVisible="showVeranstaltungModal" :veranstaltung="selectedVeranstaltung" :admins="admins" :allGebaeude="gebaeude" @close="showVeranstaltungModal = false" @save="handleSaveVeranstaltung" />
    <RaumEditorModal :isVisible="showRaumModal" :raum="selectedRaum" :slots="eventSlots" :gebaeude="gebaeude" @close="showRaumModal = false" @save="handleSaveRaum" />
    <!-- ... -->
  </div>
</template>

<script setup>
/* ... (Imports und State wie bisher) ... */
import { computed, onMounted, ref } from 'vue';
import api from '../api/axios';
import { Download as DownloadIcon, Trash2 as Trash2Icon, Upload as UploadIcon, Calendar as CalendarIcon } from 'lucide-vue-next';
import AdminVortragEditorModal from '../components/AdminVortragEditorModal.vue';
import UserEditorModal from '../components/UserEditorModal.vue';
import VeranstaltungEditorModal from '../components/VeranstaltungEditorModal.vue';
import RaumEditorModal from '../components/RaumEditorModal.vue';
import EventSlotEditorModal from '../components/EventSlotEditorModal.vue';
import GebaeudeEditorModal from '../components/GebaeudeEditorModal.vue';

// State
const activeTab = ref('benutzer');
const selectedVid = ref(null);
const veranstaltungen = ref([]);
const gebaeude = ref([]);
const raeume = ref([]);
const users = ref([]);
const vortraege = ref([]);
const referenten = ref([]);
const eventSlots = ref([]);
const stats = ref([]);

const showVeranstaltungModal = ref(false);
const selectedVeranstaltung = ref(null);
const showGebaeudeModal = ref(false);
const selectedGebaeude = ref(null);
const showRaumModal = ref(false);
const selectedRaum = ref(null);
const showUserModal = ref(false);
const selectedUser = ref(null);
const showVortragModal = ref(false);
const selectedVortrag = ref(null);
const showSlotModal = ref(false);
const selectedSlot = ref(null);

const fileInput = ref(null);
const currentUploadEndpoint = ref('');

const admins = computed(() => users.value.filter(u => u.role === 'ADMIN'));

onMounted(async () => {
  try {
    const [vRes, gRes] = await Promise.all([
      api.get('/api/veranstaltungen'),
      api.get('/api/gebaeude')
    ]);
    veranstaltungen.value = vRes.data;
    gebaeude.value = gRes.data;
    updateRaeumeList();
  } catch (err) { console.error(err); }
});

const updateRaeumeList = () => {
  raeume.value = gebaeude.value.flatMap(g => g.raeume.map(r => ({ ...r, gebaeude: { id: g.id, name: g.name } })));
};

const loadData = async () => {
  if (!selectedVid.value) return;
  try {
    const base = `/api/veranstaltungen/${selectedVid.value}`;
    const [uRes, vRes, rRes, sRes, stRes, gRes] = await Promise.all([
      api.get(`${base}/benutzer`),
      api.get(`${base}/vortraege`),
      api.get(`${base}/referenten`),
      api.get(`${base}/slots`),
      api.get(`${base}/stats`),
      api.get('/api/gebaeude')
    ]);
    users.value = uRes.data;
    vortraege.value = vRes.data;
    referenten.value = rRes.data;
    eventSlots.value = sRes.data;
    stats.value = stRes.data;
    gebaeude.value = gRes.data;
    updateRaeumeList();
  } catch (err) { console.error(err); }
};

const openRaumEditor = (r) => {
  selectedRaum.value = r || { name: '', kapazitaet: 10, etage: '', gebaeude: { id: gebaeude.value[0]?.id }, verfuegbareSlots: [] };
  showRaumModal.value = true;
};

const handleSaveRaum = async (r) => {
  const gid = r.gebaeude.id;
  const url = r.id ? `/api/gebaeude/${gid}/raeume/${r.id}` : `/api/gebaeude/${gid}/raeume`;
  try {
    if (r.id) await api.put(url, r);
    else await api.post(url, r);
    showRaumModal.value = false;
    loadData();
  } catch (e) { alert("Fehler beim Speichern des Raums!"); }
};

const deleteRaum = async (r) => {
  if (confirm(`Raum ${r.name} löschen?`)) {
    try {
      await api.delete(`/api/gebaeude/${r.gebaeude.id}/raeume/${r.id}`);
      loadData();
    } catch (e) { alert("Fehler beim Löschen!"); }
  }
};

const openVeranstaltungEditor = (v) => {
  selectedVeranstaltung.value = v || { name: '', beginntAm: '', endetAm: '', gebaeude: [], organisator: { id: admins.value[0]?.id } };
  showVeranstaltungModal.value = true;
};

const handleSaveVeranstaltung = async (v) => {
  try {
    if (v.id) await api.put(`/api/veranstaltungen/${v.id}`, v);
    else await api.post('/api/veranstaltungen', v);
    showVeranstaltungModal.value = false;
    const res = await api.get('/api/veranstaltungen');
    veranstaltungen.value = res.data;
  } catch (e) { alert("Fehler beim Speichern der Veranstaltung!"); }
};

const formatDate = (dateStr) => {
  if (!dateStr) return '';
  return new Date(dateStr).toLocaleString('de-DE', {
    day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit'
  });
};
</script>
