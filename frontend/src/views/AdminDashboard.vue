<template>
  <div class="max-w-7xl mx-auto space-y-6 pb-20">

    <!-- Page Header & Global Export -->
    <div
        class="flex flex-col md:flex-row md:items-center justify-between gap-4 bg-white p-6 rounded-xl shadow-sm border border-gray-100">
      <div>
        <h1 class="text-2xl font-bold text-gray-900">Admin-Bereich</h1>
        <p class="text-sm text-gray-500">Verwalten Sie Veranstaltungen, Räume, Personen und Vorträge.</p>
      </div>
      <button @click="downloadExport"
              class="flex items-center justify-center gap-2 bg-gray-800 text-white px-6 py-2 rounded-lg hover:bg-gray-700 transition shadow-md">
        <DownloadIcon class="w-5 h-5"/>
        Prioritäten Export (CSV)
      </button>
    </div>

    <!-- Tab-Navigation -->
    <div class="border-b border-gray-200">
      <nav class="-mb-px flex space-x-8 overflow-x-auto">
        <button v-for="tab in ['veranstaltungen', 'räume', 'users', 'vorträge', 'stats']" :key="tab"
                @click="activeTab = tab"
                :class="[activeTab === tab ? 'border-indigo-500 text-indigo-600' : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300', 'whitespace-nowrap py-4 px-1 border-b-2 font-medium text-sm capitalize']">
          {{ tab === 'veranstaltungen' ? 'Veranstaltungen' : tab === 'räume' ? 'Räume' : tab === 'users' ? 'Personen' : tab === 'vorträge' ? 'Vorträge' : 'Statistiken' }}
        </button>
      </nav>
    </div>

    <!-- TAB: VERANSTALTUNGEN -->
    <section v-if="activeTab === 'veranstaltungen'" class="space-y-4 animate-fade-in">
      <div class="flex justify-between items-center">
        <h2 class="text-xl font-bold text-gray-800">Veranstaltungs-Management</h2>
        <button @click="openVeranstaltungEditor(null)" class="bg-indigo-600 text-white px-4 py-2 rounded-lg text-sm font-bold">
          + Neue Veranstaltung
        </button>
      </div>

      <div class="bg-white shadow rounded-xl overflow-hidden border border-gray-100">
        <table class="min-w-full divide-y divide-gray-200">
          <thead class="bg-gray-50 text-xs uppercase font-medium text-gray-500">
          <tr>
            <th class="px-6 py-3 text-left">Veranstaltung</th>
            <th class="px-6 py-3 text-left">Zeitraum</th>
            <th class="px-6 py-3 text-left">Ort</th>
            <th class="px-6 py-3 text-left">Organisator</th>
            <th class="px-6 py-3 text-right">Aktionen</th>
          </tr>
          </thead>
          <tbody class="divide-y divide-gray-100 text-sm">
          <tr v-for="v in veranstaltungen" :key="v.id" class="hover:bg-gray-50 transition">
            <td class="px-6 py-4 flex items-center gap-3">
              <img v-if="v.logo" :src="v.logo" class="w-8 h-8 rounded object-contain border" />
              <div class="font-bold text-gray-900">{{ v.name }}</div>
            </td>
            <td class="px-6 py-4 text-gray-600">
              {{ formatDate(v.beginntAm) }}
              <span v-if="v.endetAm"> - {{ formatDate(v.endetAm) }}</span>
            </td>
            <td class="px-6 py-4 text-gray-600">{{ v.ort }}</td>
            <td class="px-6 py-4 text-gray-500 text-xs">
              {{ v.organisator?.lastName }}, {{ v.organisator?.firstName }}
            </td>
            <td class="px-6 py-4 text-right space-x-3">
              <button @click="openVeranstaltungEditor(v)" class="text-indigo-600 hover:text-indigo-900">Bearbeiten</button>
              <button @click="deleteVeranstaltung(v.id)" class="text-red-600 hover:text-red-900">
                <Trash2Icon class="w-4 h-4 inline"/>
              </button>
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
        <button @click="openRaumEditor(null)" class="bg-indigo-600 text-white px-4 py-2 rounded-lg text-sm font-bold">
          + Neuer Raum
        </button>
      </div>

      <div class="bg-white shadow rounded-xl overflow-hidden border border-gray-100">
        <table class="min-w-full divide-y divide-gray-200">
          <thead class="bg-gray-50 text-xs uppercase font-medium text-gray-500">
          <tr>
            <th class="px-6 py-3 text-left">Raum / Etage</th>
            <th class="px-6 py-3 text-center">Kapazität</th>
            <th class="px-6 py-3 text-left">Verfügbare Slots</th>
            <th class="px-6 py-3 text-right">Aktionen</th>
          </tr>
          </thead>
          <tbody class="divide-y divide-gray-100 text-sm">
          <tr v-for="r in raeume" :key="r.id" class="hover:bg-gray-50 transition">
            <td class="px-6 py-4">
              <div class="font-bold text-gray-900">{{ r.name }}</div>
              <div class="text-gray-400 text-xs">{{ r.etage || 'keine Etage' }}</div>
            </td>
            <td class="px-6 py-4 text-center">
              <span class="bg-gray-100 text-gray-700 px-3 py-1 rounded-full text-xs font-bold">{{ r.kapazitaet }}</span>
            </td>
            <td class="px-6 py-4 text-gray-600">
              <div class="flex flex-wrap gap-1">
                <span v-for="slot in r.verfuegbareSlots" :key="slot.id" class="bg-indigo-50 text-indigo-600 px-2 py-0.5 rounded text-[10px] font-medium border border-indigo-100">
                  {{ slot.description }}
                </span>
              </div>
            </td>
            <td class="px-6 py-4 text-right space-x-3">
              <button @click="openRaumEditor(r)" class="text-indigo-600 hover:text-indigo-900">Bearbeiten</button>
              <button @click="deleteRaum(r.id)" class="text-red-600 hover:text-red-900">
                <Trash2Icon class="w-4 h-4 inline"/>
              </button>
            </td>
          </tr>
          </tbody>
        </table>
      </div>
    </section>

    <!-- TAB: BENUTZER -->
    <section v-if="activeTab === 'users'" class="space-y-4 animate-fade-in">
      <div class="flex flex-col sm:flex-row justify-between items-center gap-4">
        <h2 class="text-xl font-bold text-gray-800">Benutzer-Verwaltung</h2>
        <div class="flex gap-2 w-full sm:w-auto">
          <input type="file" ref="csvInput" class="hidden" @change="handleCsvUpload" accept=".csv"/>
          <button @click="$refs.csvInput.click()"
                  class="flex-1 sm:flex-none bg-indigo-50 text-indigo-700 px-4 py-2 rounded-lg text-sm font-bold flex items-center justify-center gap-2 border border-indigo-100 hover:bg-indigo-100">
            <UploadIcon class="w-4 h-4"/>
            CSV Import
          </button>
          <button @click="openUserModal(null)"
                  class="flex-1 sm:flex-none bg-indigo-600 text-white px-4 py-2 rounded-lg text-sm font-bold hover:bg-indigo-700 transition">
            + Neu
          </button>
        </div>
      </div>

      <div class="bg-white shadow rounded-xl overflow-hidden border border-gray-100">
        <table class="min-w-full divide-y divide-gray-200">
          <thead class="bg-gray-50 text-xs uppercase font-medium text-gray-500">
          <tr>
            <th class="px-6 py-3 text-left">Name / Organisation</th>
            <th class="px-6 py-3 text-left">Email / Rolle</th>
            <th class="px-6 py-3 text-center">Status</th>
            <th class="px-6 py-3 text-right">Aktionen</th>
          </tr>
          </thead>
          <tbody class="divide-y divide-gray-100 text-sm">
          <tr v-for="u in users" :key="u.id" class="hover:bg-gray-50 transition">
            <td class="px-6 py-4">
              <div class="font-bold text-gray-900">{{ u.lastName }}, {{ u.firstName }}</div>
              <div class="text-gray-500 text-xs">{{ u.organization }}</div>
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
        <button @click="openVortragEditor(null)" class="bg-indigo-600 text-white px-4 py-2 rounded-lg text-sm font-bold">
          + Neuer Vortrag
        </button>
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
    <VeranstaltungEditorModal
        :isVisible="showVeranstaltungModal"
        :veranstaltung="selectedVeranstaltung"
        :admins="admins"
        @close="showVeranstaltungModal = false"
        @save="handleSaveVeranstaltung"
    />

    <RaumEditorModal
        :isVisible="showRaumModal"
        :raum="selectedRaum"
        :slots="eventSlots"
        @close="showRaumModal = false"
        @save="handleSaveRaum"
    />

    <UserEditorModal
        :isVisible="showUserModal"
        :user="selectedUser"
        @close="showUserModal = false"
        @save="handleSaveUser"
    />

    <AdminVortragEditorModal
        :isVisible="showVortragModal"
        :vortrag="selectedVortrag"
        :referenten="referenten"
        @close="showVortragModal = false"
        @save="handleSaveVortrag"
    />
  </div>
</template>

<script setup>
import {computed, onMounted, ref} from 'vue';
import api from '../api/axios';
import {Download as DownloadIcon, Trash2 as Trash2Icon, Upload as UploadIcon} from 'lucide-vue-next';
import AdminVortragEditorModal from '../components/AdminVortragEditorModal.vue';
import UserEditorModal from '../components/UserEditorModal.vue';
import VeranstaltungEditorModal from '../components/VeranstaltungEditorModal.vue';
import RaumEditorModal from '../components/RaumEditorModal.vue';

// State
const activeTab = ref('veranstaltungen');
const veranstaltungen = ref([]);
const raeume = ref([]);
const users = ref([]);
const vortraege = ref([]);
const referenten = ref([]);
const eventSlots = ref([]);
const stats = ref([]);

const showVeranstaltungModal = ref(false);
const selectedVeranstaltung = ref(null);
const showRaumModal = ref(false);
const selectedRaum = ref(null);
const showUserModal = ref(false);
const selectedUser = ref(null);
const showVortragModal = ref(false);
const selectedVortrag = ref(null);
const csvInput = ref(null);

// Getters
const admins = computed(() => users.value.filter(u => u.role === 'ADMIN'));

// Initial Load
onMounted(() => loadData());

const loadData = async () => {
  try {
    const [vRes, rRes, uRes, tRes, sRes, slRes, stRes] = await Promise.all([
      api.get('/api/veranstaltung'),
      api.get('/api/raum'),
      api.get('/api/admin/users'),
      api.get('/api/admin/vortraege'),
      api.get('/api/admin/referenten'),
      api.get('/api/admin/slots'),
      api.get('/api/admin/stats')
    ]);
    veranstaltungen.value = vRes.data;
    raeume.value = rRes.data;
    users.value = uRes.data;
    vortraege.value = tRes.data;
    referenten.value = sRes.data;
    eventSlots.value = slRes.data;
    stats.value = stRes.data;
  } catch (err) {
    console.error("Datenladefehler:", err);
  }
};

// --- VORTRAG ACTIONS ---
const openVortragEditor = (v) => {
  selectedVortrag.value = v || { title: '', abstractText: '', targetAudience: '', referent: { id: null }, maxRepetitions: 1, readyToRepeat: false, istPflicht: false, version: 0 };
  showVortragModal.value = true;
};

const handleSaveVortrag = async (v) => {
  try {
    await api.put(`/api/admin/vortraege/${v.id || ''}`, v);
    showVortragModal.value = false;
    loadData();
  } catch (err) { alert("Fehler beim Speichern des Vortrags."); }
};

// --- VERANSTALTUNG ACTIONS ---
const openVeranstaltungEditor = (v) => {
  selectedVeranstaltung.value = v || { name: '', beginntAm: '', ort: '', organisator: { id: null } };
  showVeranstaltungModal.value = true;
};

const handleSaveVeranstaltung = async (v) => {
  try {
    if (v.id) await api.put(`/api/veranstaltung/${v.id}`, v);
    else await api.post('/api/veranstaltung', v);
    showVeranstaltungModal.value = false;
    loadData();
  } catch (err) { alert("Fehler beim Speichern der Veranstaltung."); }
};

const deleteVeranstaltung = async (id) => {
  if (confirm("Veranstaltung wirklich löschen?")) {
    await api.delete(`/api/veranstaltung/${id}`);
    loadData();
  }
};

// --- RAUM ACTIONS ---
const openRaumEditor = (r) => {
  selectedRaum.value = r || { name: '', kapazitaet: 10, etage: '', verfuegbareSlots: [] };
  showRaumModal.value = true;
};

const handleSaveRaum = async (r) => {
  try {
    if (r.id) await api.put(`/api/raum/${r.id}`, r);
    else await api.post('/api/raum', r);
    showRaumModal.value = false;
    loadData();
  } catch (err) { alert("Fehler beim Speichern des Raums."); }
};

const deleteRaum = async (id) => {
  if (confirm("Raum wirklich löschen?")) {
    await api.delete(`/api/raum/${id}`);
    loadData();
  }
};

// --- USER ACTIONS ---
const openUserModal = (user) => {
  selectedUser.value = user ? {...user} : { firstName: '', lastName: '', email: '', organization: '', role: 'TEILNEHMER', isActive: true };
  showUserModal.value = true;
};

const handleSaveUser = async (updatedUser) => {
  try {
    if (updatedUser.id) await api.put(`/api/admin/users/${updatedUser.id}`, updatedUser);
    else await api.post('/api/admin/users', updatedUser);
    showUserModal.value = false;
    loadData();
  } catch (error) { alert("Fehler beim Speichern des Benutzers."); }
};

const toggleUserStatus = async (user) => {
  try {
    await api.patch(`/api/admin/users/${user.id}/toggle`);
    user.isActive = !user.isActive;
  } catch (err) { console.error(err); }
};

const deleteUser = async (id) => {
  if (confirm("Benutzer wirklich löschen?")) {
    await api.delete(`/api/admin/users/${id}`);
    loadData();
  }
};

// --- HELPERS ---
const formatDate = (dateStr) => {
  if (!dateStr) return '';
  return new Date(dateStr).toLocaleString('de-DE', {
    day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit'
  });
};

const handleCsvUpload = async (event) => { /* ... */ };
const downloadExport = async () => { /* ... */ };
</script>
