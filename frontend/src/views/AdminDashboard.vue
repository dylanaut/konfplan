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

    <!-- Tab-Navigation: 'veranstaltungen' ist IMMER sichtbar -->
    <div class="border-b border-gray-200">
      <nav class="-mb-px flex space-x-8 overflow-x-auto">
        <button @click="activeTab = 'veranstaltungen'"
                :class="[activeTab === 'veranstaltungen' ? 'border-indigo-500 text-indigo-600' : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300', 'whitespace-nowrap py-4 px-1 border-b-2 font-medium text-sm capitalize']">
          Veranstaltungen
        </button>
        <!-- Diese Tabs erscheinen nur, wenn eine Vid gewählt ist -->
        <template v-if="selectedVid">
          <button v-for="tab in ['gebäude', 'räume', 'benutzer', 'vorträge', 'slots', 'planung', 'stats']" :key="tab"
                  @click="activeTab = tab"
                  :class="[activeTab === tab ? 'border-indigo-500 text-indigo-600' : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300', 'whitespace-nowrap py-4 px-1 border-b-2 font-medium text-sm capitalize']">
            {{ tab === 'planung' ? 'Vortragsplanung' : tab }}
          </button>
        </template>
      </nav>
    </div>

    <!-- INHALT -->

    <!-- TAB: VERANSTALTUNGEN (Immer verfügbar) -->
    <section v-if="activeTab === 'veranstaltungen'" class="space-y-4 animate-fade-in">
      <div class="flex justify-between items-center">
        <h2 class="text-xl font-bold text-gray-800">Veranstaltungs-Management</h2>
        <div class="flex flex-wrap gap-2">
          <button @click="triggerUpload('/api/veranstaltungen/import')" class="btn-secondary">CSV Import</button>
          <button @click="openVeranstaltungEditor(null)" class="btn-primary">+ Neue Veranstaltung</button>
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
          <tr v-for="v in veranstaltungen" :key="v.id" :class="[selectedVid === v.id ? 'bg-indigo-50' : '', 'hover:bg-gray-50 transition']">
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
              <button v-if="selectedVid !== v.id" @click="selectedVid = v.id; loadData()" class="text-indigo-600 font-bold hover:underline">Auswählen</button>
              <button @click="openVeranstaltungEditor(v)" class="text-gray-600 hover:underline">Bearbeiten</button>
              <button @click="deleteVeranstaltung(v.id)" class="text-red-600 hover:text-red-900"><Trash2Icon class="w-4 h-4 inline"/></button>
            </td>
          </tr>
          <tr v-if="veranstaltungen.length === 0">
            <td colspan="4" class="px-6 py-10 text-center text-gray-500 italic">Keine Veranstaltungen gefunden. Bitte legen Sie eine neue an.</td>
          </tr>
          </tbody>
        </table>
      </div>
    </section>

    <!-- ANDERE TABS: Nur wenn vid gewählt UND nicht im Veranstaltungstab -->
    <template v-if="selectedVid">
       <!-- Hier folgen die Sektionen für Räume, Benutzer, Vorträge etc. (wie bisher) -->
       <section v-if="activeTab === 'gebäude'" class="space-y-4 animate-fade-in">
         <!-- ... (Inhalt Gebäude) -->
       </section>
       <!-- ... -->
    </template>

    <!-- Platzhalter, wenn nichts gewählt und nicht im Veranstaltungstab -->
    <div v-else-if="activeTab !== 'veranstaltungen'" class="bg-indigo-50 p-10 rounded-2xl text-center border-2 border-dashed border-indigo-200">
      <div class="text-indigo-400 mb-4 flex justify-center"><CalendarIcon class="w-12 h-12"/></div>
      <h2 class="text-xl font-bold text-indigo-900">Keine Veranstaltung ausgewählt</h2>
      <p class="text-indigo-600 mt-2">Bitte wählen Sie eine Veranstaltung aus oder wechseln Sie zum Tab "Veranstaltungen".</p>
      <button @click="activeTab = 'veranstaltungen'" class="mt-4 text-indigo-700 font-bold underline">Zu den Veranstaltungen</button>
    </div>

    <!-- Modals (wie bisher) -->
    <VeranstaltungEditorModal :isVisible="showVeranstaltungModal" :veranstaltung="selectedVeranstaltung" :admins="admins" :allGebaeude="gebaeude" @close="showVeranstaltungModal = false" @save="handleSaveVeranstaltung" />
    <!-- ... -->
  </div>
</template>

<script setup>
import { computed, onMounted, ref, reactive } from 'vue';
import api from '../api/axios';
import { Download as DownloadIcon, Trash2 as Trash2Icon, Upload as UploadIcon, Calendar as CalendarIcon } from 'lucide-vue-next';
import AdminVortragEditorModal from '../components/AdminVortragEditorModal.vue';
import UserEditorModal from '../components/UserEditorModal.vue';
import VeranstaltungEditorModal from '../components/VeranstaltungEditorModal.vue';
import RaumEditorModal from '../components/RaumEditorModal.vue';
import EventSlotEditorModal from '../components/EventSlotEditorModal.vue';
import GebaeudeEditorModal from '../components/GebaeudeEditorModal.vue';

// State
const activeTab = ref('veranstaltungen'); // Standardmäßig auf Veranstaltungen
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

onMounted(async () => {
  try {
    const res = await api.get('/api/veranstaltungen');
    veranstaltungen.value = res.data;
    // Falls nur eine existiert, direkt auswählen? (Optional)
    // if (veranstaltungen.value.length === 1) { selectedVid.value = veranstaltungen.value[0].id; loadData(); }
  } catch (err) { console.error(err); }

  // Gebäude auch initial laden, da sie global sind
  try {
    const res = await api.get('/api/gebaeude');
    gebaeude.value = res.data;
  } catch (e) {}
});

// ... (Restliche Methoden handleSaveVeranstaltung, loadData etc. wie bisher)
</script>
