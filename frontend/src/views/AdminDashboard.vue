<template>
  <div class="max-w-7xl mx-auto space-y-6 pb-20">

    <!-- Page Header & Veranstaltungsauswahl -->
    <div class="flex flex-col md:flex-row md:items-center justify-between gap-4 bg-white p-6 rounded-xl shadow-sm border border-gray-100">
      <div class="flex-1">
        <h1 class="text-2xl font-bold text-gray-900">Admin-Bereich</h1>
        <div class="mt-4 flex items-center gap-3">
          <label class="text-sm font-bold text-gray-500 uppercase tracking-wider">Aktive Veranstaltung:</label>
          <select v-model="selectedVid" @change="loadData" class="input-field max-w-md border-indigo-200 focus:ring-indigo-500">
            <option :value="null">-- Bitte wählen / Keine Auswahl --</option>
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

    <!-- Tab-Navigation: 'veranstaltungen' und 'gebäude' sind IMMER sichtbar -->
    <div class="border-b border-gray-200">
      <nav class="-mb-px flex space-x-8 overflow-x-auto">
        <button v-for="tab in visibleTabs" :key="tab"
                @click="activeTab = tab"
                :class="[activeTab === tab ? 'border-indigo-500 text-indigo-600' : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300', 'whitespace-nowrap py-4 px-1 border-b-2 font-medium text-sm capitalize']">
          {{ tab === 'planung' ? 'Optimierung' : tab === 'ergebnisse' ? 'Ergebnisse' : tab }}
        </button>
      </nav>
    </div>

    <!-- START-ZUSTAND -->
    <div v-if="!selectedVid && !['veranstaltungen', 'gebäude'].includes(activeTab)" class="bg-indigo-50 p-10 rounded-2xl text-center border-2 border-dashed border-indigo-200 animate-fade-in">
      <div class="text-indigo-400 mb-4 flex justify-center"><CalendarIcon class="w-12 h-12"/></div>
      <h2 class="text-xl font-bold text-indigo-900">Keine Veranstaltung ausgewählt</h2>
      <p class="text-indigo-600 mt-2 mb-6">Bitte wählen Sie oben eine Veranstaltung aus oder legen Sie eine neue an.</p>
      <button @click="activeTab = 'veranstaltungen'; openVeranstaltungEditor(null)" class="btn-primary inline-flex items-center gap-2">
        <PlusCircleIcon class="w-5 h-5"/> Erste Veranstaltung anlegen
      </button>
    </div>

    <!-- TAB: VERANSTALTUNGEN -->
    <section v-if="activeTab === 'veranstaltungen'" class="space-y-4 animate-fade-in">
      <div class="flex justify-between items-center bg-white p-4 rounded-xl border border-gray-100 shadow-sm">
        <h2 class="text-xl font-bold text-gray-800">Veranstaltungs-Management</h2>
        <div class="flex gap-2">
          <button @click="triggerUpload('/api/veranstaltungen/import')" class="btn-secondary flex items-center gap-2">
            <UploadIcon class="w-4 h-4"/> CSV Import
          </button>
          <button @click="openVeranstaltungEditor(null)" class="btn-primary flex items-center gap-2">
            <PlusCircleIcon class="w-4 h-4"/> + Neue Veranstaltung
          </button>
        </div>
      </div>
      <!-- Tabelle... -->
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
          <tr v-for="v in veranstaltungen" :key="v.id" :class="[selectedVid === v.id ? 'bg-indigo-50' : '', 'hover:bg-gray-50 transition']">
            <td class="px-6 py-4 flex items-center gap-3">
              <img v-if="v.logo" :src="v.logo" class="w-8 h-8 rounded object-contain border" />
              <div class="font-bold text-gray-900">{{ v.name }}</div>
            </td>
            <td class="px-6 py-4 text-gray-600">{{ formatDate(v.beginntAm) }}</td>
            <td class="px-6 py-4 text-gray-600 text-xs">
              <div class="flex flex-wrap gap-1">
                <span v-for="g in v.gebaeude" :key="g.id" class="bg-white px-2 py-0.5 rounded border text-[10px]">{{ g.name }}</span>
              </div>
            </td>
            <td class="px-6 py-4 text-right space-x-3">
              <button v-if="selectedVid !== v.id" @click="selectedVid = v.id; loadData()" class="text-indigo-600 font-bold hover:underline">Auswählen</button>
              <button @click="openVeranstaltungEditor(v)" class="text-gray-600 hover:text-gray-900">Bearbeiten</button>
              <button @click="deleteVeranstaltung(v.id)" class="text-red-600 hover:text-red-900"><Trash2Icon class="w-4 h-4 inline"/></button>
            </td>
          </tr>
          </tbody>
        </table>
      </div>
    </section>

    <!-- TAB: GEBÄUDE -->
    <section v-if="activeTab === 'gebäude'" class="space-y-4 animate-fade-in">
      <div class="flex justify-between items-center bg-white p-4 rounded-xl border border-gray-100 shadow-sm">
        <h2 class="text-xl font-bold text-gray-800">Gebäudeverwaltung</h2>
        <div class="flex gap-2">
          <button @click="triggerUpload('/api/gebaeude/import')" class="btn-secondary flex items-center gap-2">
            <UploadIcon class="w-4 h-4"/> CSV Import
          </button>
          <button @click="openGebaeudeEditor(null)" class="btn-primary flex items-center gap-2">
            <PlusCircleIcon class="w-4 h-4"/> + Neues Gebäude
          </button>
        </div>
      </div>
      <!-- Tabelle... -->
      <div class="bg-white shadow rounded-xl overflow-hidden border border-gray-100">
        <table class="min-w-full divide-y divide-gray-200">
          <thead class="bg-gray-50 text-xs uppercase font-medium text-gray-500">
          <tr>
            <th class="px-6 py-3 text-left">Gebäude</th>
            <th class="px-6 py-3 text-left">Adresse</th>
            <th class="px-6 py-3 text-right">Aktionen</th>
          </tr>
          </thead>
          <tbody class="divide-y divide-gray-100 text-sm">
          <tr v-for="g in gebaeude" :key="g.id" class="hover:bg-gray-50 transition">
            <td class="px-6 py-4">
              <div class="font-bold text-gray-900">{{ g.name }}</div>
              <div class="text-gray-500 text-[10px] uppercase font-bold">{{ g.typ }}</div>
            </td>
            <td class="px-6 py-4 text-gray-600">{{ g.strasse }} {{ g.hausnummer }}, {{ g.postleitzahl }} {{ g.ort }}</td>
            <td class="px-6 py-4 text-right space-x-3">
              <button @click="openGebaeudeEditor(g)" class="text-indigo-600 hover:underline">Bearbeiten</button>
              <button @click="deleteGebaeude(g.id)" class="text-red-600 hover:text-red-900"><Trash2Icon class="w-4 h-4 inline"/></button>
            </td>
          </tr>
          </tbody>
        </table>
      </div>
    </section>

    <template v-if="selectedVid">
      <!-- TAB: BENUTZER -->
      <section v-if="activeTab === 'benutzer'" class="space-y-4 animate-fade-in">
        <div class="flex flex-col sm:flex-row justify-between items-center gap-4">
          <h2 class="text-xl font-bold text-gray-800">Benutzerverwaltung</h2>
          <div class="flex flex-wrap gap-2">
            <button @click="triggerUpload('/api/admin/teilnehmer/import')" class="btn-secondary text-xs">Teilnehmer CSV</button>
            <button @click="triggerUpload('/api/admin/referenten/import')" class="btn-secondary text-xs">Referenten CSV</button>
            <button @click="triggerUpload('/api/admin/admins/import')" class="btn-secondary text-xs">Admins CSV</button>
            <button @click="openUserModal(null)" class="btn-primary text-sm">+ Neu</button>
          </div>
        </div>
        <div class="bg-white shadow rounded-xl overflow-hidden border border-gray-100">
          <table class="min-w-full divide-y divide-gray-200">
            <thead class="bg-gray-50 text-xs uppercase font-medium text-gray-500">
            <tr>
              <th class="px-6 py-3 text-left">Name / Gruppe</th>
              <th class="px-6 py-3 text-left">Email / Rolle</th>
              <th class="px-6 py-3 text-center">Status</th>
              <th class="px-6 py-3 text-right">Aktionen</th>
            </tr>
            </thead>
            <tbody class="divide-y divide-gray-100 text-sm">
            <tr v-for="u in users" :key="u.id" class="hover:bg-gray-50 transition">
              <td class="px-6 py-4">
                <div class="font-bold text-gray-900">{{ u.lastName }}, {{ u.firstName }}</div>
                <div class="text-gray-500 text-xs">{{ u.gruppe || u.jobRole || '' }}</div>
              </td>
              <td class="px-6 py-4">
                <div class="text-gray-600">{{ u.email }}</div>
                <span class="inline-block mt-1 px-2 py-0.5 rounded text-[10px] font-bold tracking-wider"
                      :class="[u.role === 'ADMIN' ? 'bg-purple-100 text-purple-700' : u.role === 'REFERENT' ? 'bg-blue-100 text-blue-700' : 'bg-gray-100 text-gray-600']">
                {{ u.role }}
              </span>
              </td>
              <td class="px-6 py-4 text-center">
                <button @click="toggleUserStatus(u)"
                        :class="[u.isActive ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700', 'px-3 py-1 rounded-full text-xs font-bold']">
                  {{ u.isActive ? 'Aktiv' : 'Inaktiv' }}
                </button>
              </td>
              <td class="px-6 py-4 text-right space-x-3">
                <button @click="openUserModal(u)" class="text-indigo-600 hover:text-indigo-900">Bearbeiten</button>
                <button @click="deleteUser(u.id)" class="text-red-600 hover:text-red-900">
                  <Trash2Icon class="w-4 h-4 inline"/>
                </button>
              </td>
            </tr>
            </tbody>
          </table>
        </div>
      </section>

      <!-- TAB: VORTRÄGE -->
      <section v-if="activeTab === 'vorträge'" class="space-y-4 animate-fade-in">
        <div class="flex justify-between items-center">
          <h2 class="text-xl font-bold text-gray-800">Vortrags-Management</h2>
          <div class="flex flex-wrap gap-2">
            <button @click="triggerUpload('/api/admin/vortraege/import')" class="btn-secondary text-xs">CSV Import</button>
            <button @click="openVortragEditor(null)" class="btn-primary text-sm">+ Neu</button>
          </div>
        </div>
        <div class="bg-white shadow rounded-xl overflow-hidden border border-gray-100">
          <table class="min-w-full divide-y divide-gray-200">
            <thead class="bg-gray-50 text-xs uppercase font-medium text-gray-500">
            <tr>
              <th class="px-6 py-3 text-left">Vortrag</th>
              <th class="px-6 py-3 text-left">Referent</th>
              <th class="px-6 py-3 text-center">Info</th>
              <th class="px-6 py-3 text-right">Aktionen</th>
            </tr>
            </thead>
            <tbody class="divide-y divide-gray-100 text-sm">
            <tr v-for="v in vortraege" :key="v.id" class="hover:bg-gray-50 transition">
              <td class="px-6 py-4">
                <div class="flex items-center gap-2">
                  <div class="font-bold text-gray-900">{{ v.title }}</div>
                  <span v-if="v.istPflicht" class="bg-red-100 text-red-700 px-2 py-0.5 rounded text-[10px] font-black uppercase tracking-tighter border border-red-200">Pflicht</span>
                </div>
                <div class="text-gray-400 text-xs">{{ v.targetAudience }}</div>
              </td>
              <td class="px-6 py-4 text-gray-600">
                {{ v.referent?.lastName }}, {{ v.referent?.firstName }}
              </td>
              <td class="px-6 py-4 text-center space-y-1">
                <span v-if="v.readyToRepeat" class="block bg-blue-50 text-blue-600 px-2 py-0.5 rounded text-[10px] font-medium border border-blue-100">Wiederholbar</span>
                <span class="block text-[10px] text-gray-400">v{{ v.version }}</span>
              </td>
              <td class="px-6 py-4 text-right">
                <button @click="openVortragEditor(v)" class="text-indigo-600 hover:text-indigo-900 font-bold">Bearbeiten</button>
              </td>
            </tr>
            </tbody>
          </table>
        </div>
      </section>

      <!-- TAB: SLOTS -->
      <section v-if="activeTab === 'slots'" class="space-y-4 animate-fade-in">
        <div class="flex justify-between items-center">
          <h2 class="text-xl font-bold text-gray-800">Zeit-Slots</h2>
          <div class="flex flex-wrap gap-2">
            <button @click="triggerUpload('/api/admin/slots/import')" class="btn-secondary text-xs">CSV Import</button>
            <button @click="openSlotEditor(null)" class="btn-primary text-sm">+ Neu</button>
          </div>
        </div>
        <div class="bg-white shadow rounded-xl p-6 border border-gray-100">
          <ul class="divide-y divide-gray-100">
            <li v-for="s in eventSlots" :key="s.id" class="py-2 flex justify-between items-center">
              <span>{{ s.description }}</span>
              <span class="text-gray-500 text-sm">{{ formatTime(s.startTime) }} - {{ formatTime(s.endTime) }}</span>
            </li>
          </ul>
        </div>
      </section>
      <!-- TAB: STATISTIKEN -->
      <section v-if="activeTab === 'stats'" class="space-y-6 animate-fade-in">
        <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          <div v-for="stat in stats" :key="stat.titel" class="bg-white p-6 rounded-xl shadow-sm border border-gray-100 flex flex-col">
            <h3 class="font-bold text-gray-900 mb-4 line-clamp-2 h-12">{{ stat.titel }}</h3>

            <div class="space-y-4 flex-1">
              <!-- Prio 1 -->
              <div>
                <div class="flex justify-between text-[10px] font-bold text-gray-500 uppercase">
                  <span>Prio 1 Stimmen</span>
                  <span class="text-indigo-600">{{ stat.countPrio1 }}</span>
                </div>
                <div class="w-full bg-gray-100 rounded-full h-1.5 mt-1">
                  <div class="bg-indigo-600 h-1.5 rounded-full" :style="{ width: Math.min(stat.countPrio1 * 5, 100) + '%' }"></div>
                </div>
              </div>

              <!-- Prio 2 & 3 im Vergleich -->
              <div class="grid grid-cols-2 gap-4">
                <div>
                  <div class="flex justify-between text-[9px] font-bold text-gray-400 uppercase">
                    <span>Prio 2</span>
                    <span>{{ stat.countPrio2 }}</span>
                  </div>
                  <div class="w-full bg-gray-100 rounded-full h-1 mt-1">
                    <div class="bg-blue-400 h-1 rounded-full" :style="{ width: Math.min(stat.countPrio2 * 5, 100) + '%' }"></div>
                  </div>
                </div>
                <div>
                  <div class="flex justify-between text-[9px] font-bold text-gray-400 uppercase">
                    <span>Prio 3</span>
                    <span>{{ stat.countPrio3 }}</span>
                  </div>
                  <div class="w-full bg-gray-100 rounded-full h-1 mt-1">
                    <div class="bg-blue-300 h-1 rounded-full" :style="{ width: Math.min(stat.countPrio3 * 5, 100) + '%' }"></div>
                  </div>
                </div>
              </div>

              <!-- Top 3 Aggregation -->
              <div class="pt-4 border-t border-gray-50">
                <div class="flex justify-between text-[10px] font-bold text-gray-500 uppercase">
                  <span>Gesamt Top 3</span>
                  <span class="text-green-600">{{ stat.countTop3 }}</span>
                </div>
                <div class="w-full bg-gray-100 rounded-full h-2 mt-1">
                  <div class="bg-green-500 h-2 rounded-full" :style="{ width: Math.min(stat.countTop3 * 5, 100) + '%' }"></div>
                </div>
              </div>
            </div>

            <div class="mt-4 text-right">
              <span class="text-[10px] text-gray-400 font-medium">Gesamtstimmen: {{ stat.totalVotes }}</span>
            </div>
          </div>
        </div>
      </section>
    </template>

    <!-- Global File Input -->
    <input type="file" ref="fileInput" class="hidden" @change="handleGlobalUpload" accept=".csv" />

    <!-- Modals -->
    <VeranstaltungEditorModal :isVisible="showVeranstaltungModal" :veranstaltung="selectedVeranstaltung" :admins="admins" :allGebaeude="gebaeude" @close="showVeranstaltungModal = false" @save="handleSaveVeranstaltung" />
    <GebaeudeEditorModal :isVisible="showGebaeudeModal" :gebaeude="selectedGebaeude" @close="showGebaeudeModal = false" @save="handleSaveGebaeude" />
    <RaumEditorModal :isVisible="showRaumModal" :raum="selectedRaum" :slots="eventSlots" :gebaeude="gebaeude" @close="showRaumModal = false" @save="handleSaveRaum" />
    <UserEditorModal :isVisible="showUserModal" :user="selectedUser" :eventSlots="eventSlots" @close="showUserModal = false" @save="handleSaveUser" />
    <AdminVortragEditorModal :isVisible="showVortragModal" :vortrag="selectedVortrag" :referenten="referenten" :raeume="raeume" :slots="eventSlots" @close="showVortragModal = false" @save="handleSaveVortrag" />
    <EventSlotEditorModal :isVisible="showSlotModal" :slot="selectedSlot" @close="showSlotModal = false" @save="handleSaveSlot" />
  </div>
</template>

<script setup>
import { computed, onMounted, ref, reactive } from 'vue';
import api from '../api/axios';
import { Download as DownloadIcon, Trash2 as Trash2Icon, Upload as UploadIcon, Calendar as CalendarIcon, PlusCircle as PlusCircleIcon } from 'lucide-vue-next';
import AdminVortragEditorModal from '../components/AdminVortragEditorModal.vue';
import UserEditorModal from '../components/UserEditorModal.vue';
import VeranstaltungEditorModal from '../components/VeranstaltungEditorModal.vue';
import RaumEditorModal from '../components/RaumEditorModal.vue';
import EventSlotEditorModal from '../components/EventSlotEditorModal.vue';
import GebaeudeEditorModal from '../components/GebaeudeEditorModal.vue';

// State
const activeTab = ref('veranstaltungen');
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

const visibleTabs = computed(() => {
  const base = ['veranstaltungen', 'gebäude'];
  if (selectedVid.value) return ['ergebnisse', 'planung', 'benutzer', 'vorträge', 'slots', 'räume', ...base];
  return base;
});

const admins = computed(() => users.value.filter(u => u.role === 'ADMIN'));

onMounted(async () => {
  await refreshVeranstaltungen();
  await refreshGebaeude();
  try {
    const res = await api.get('/api/admin/users');
    users.value = res.data;
  } catch (err) { console.error(err); }
});

const refreshVeranstaltungen = async () => {
  try {
    const res = await api.get('/api/veranstaltungen');
    veranstaltungen.value = res.data;
  } catch (err) { console.error(err); }
};

const refreshGebaeude = async () => {
  try {
    const res = await api.get('/api/gebaeude');
    gebaeude.value = res.data;
  } catch (err) { console.error(err); }
};

const triggerUpload = (endpoint) => {
  currentUploadEndpoint.value = endpoint;
  fileInput.value.click();
};

const handleGlobalUpload = async (event) => {
  const file = event.target.files[0];
  if (!file) return;
  const formData = new FormData();
  formData.append('file', file);
  try {
    const res = await api.post(currentUploadEndpoint.value, formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    });
    alert(res.data);
    await refreshVeranstaltungen();
    await refreshGebaeude();
    if (selectedVid.value) loadData();
  } catch (err) { alert("Upload-Fehler!"); }
  finally { event.target.value = ''; }
};

const openGebaeudeEditor = (g) => {
  selectedGebaeude.value = g || { name: '', typ: 'SCHULE', strasse: '', hausnummer: '', postleitzahl: '', ort: '' };
  showGebaeudeModal.value = true;
};

const handleSaveGebaeude = async (g) => {
  try {
    if (g.id) await api.put(`/api/gebaeude/${g.id}`, g);
    else await api.post('/api/gebaeude', g);
    showGebaeudeModal.value = false;
    await refreshGebaeude();
  } catch (e) { alert("Fehler!"); }
};

const deleteGebaeude = async (id) => {
  if (confirm("Gebäude wirklich löschen? Alle zugehörigen Räume werden ebenfalls gelöscht!")) {
    await api.delete(`/api/gebaeude/${id}`);
    await refreshGebaeude();
  }
};

const openVeranstaltungEditor = (v) => {
  selectedVeranstaltung.value = v || { name: '', beginntAm: '', endetAm: '', gebaeude: [], organisatorId: admins.value[0]?.id };
  showVeranstaltungModal.value = true;
};

const handleSaveVeranstaltung = async (v) => {
  try {
    if (v.id) await api.put(`/api/veranstaltungen/${v.id}`, v);
    else {
      const res = await api.post('/api/veranstaltungen', v);
      if (veranstaltungen.value.length === 0) selectedVid.value = res.data.id;
    }
    showVeranstaltungModal.value = false;
    await refreshVeranstaltungen();
  } catch (e) { alert("Fehler!"); }
};

const deleteVeranstaltung = async (id) => {
  if (confirm("Wirklich löschen?")) {
    await api.delete(`/api/veranstaltungen/${id}`);
    if (selectedVid.value === id) selectedVid.value = null;
    await refreshVeranstaltungen();
  }
};

const loadData = async () => {
  if (!selectedVid.value) return;
  try {
    const base = `/api/veranstaltungen/${selectedVid.value}`;
    const [uRes, vRes, rRes, sRes, stRes] = await Promise.all([
      api.get(`${base}/benutzer`),
      api.get(`${base}/vortraege`),
      api.get(`${base}/referenten`),
      api.get(`${base}/slots`),
      api.get(`${base}/stats`)
    ]);
    users.value = uRes.data;
    vortraege.value = vRes.data;
    referenten.value = rRes.data;
    eventSlots.value = sRes.data;
    stats.value = stRes.data;
  } catch (err) { console.error(err); }
};

const formatDate = (d) => d ? new Date(d).toLocaleDateString('de-DE') : '';
</script>

<style scoped>
.btn-primary { @apply rounded-lg bg-indigo-600 px-4 py-2 text-white font-bold hover:bg-indigo-700 transition shadow-sm text-sm border-none cursor-pointer; }
.btn-secondary { @apply bg-white text-gray-700 px-4 py-2 rounded-lg hover:bg-gray-50 font-bold border border-gray-200 transition shadow-sm text-sm cursor-pointer; }
.input-field { @apply rounded-lg border border-gray-300 px-3 py-2 text-gray-900 focus:ring-2 focus:ring-indigo-500 bg-white text-sm; }
</style>
