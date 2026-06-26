<template>
  <div class="max-w-7xl mx-auto space-y-6 pb-20 text-sm">

    <!-- Page Header & Veranstaltungsauswahl -->
    <div
        class="flex flex-col md:flex-row md:items-center justify-between gap-4 bg-white p-4 rounded-xl shadow-sm border border-gray-100">
      <div class="flex-1">
        <img class="h-12 w-auto mb-2" src="/logo/konfplan-light.svg" alt="Konfplan Logo"/>
        <h1 class="text-xl font-bold text-gray-900">Admin-Bereich</h1>
        <div class="mt-2 flex items-center gap-3">
          <label class="text-[10px] font-bold text-gray-500 uppercase tracking-wider">Aktive Veranstaltung:</label>
          <select v-model="selectedVid" @change="handleVeranstaltungChange"
                  class="input-field max-w-md border-indigo-200 focus:ring-indigo-500 py-1 text-xs pr-12">
            <option :value="null">-- Bitte wählen / Keine Auswahl --</option>
            <option v-for="v in veranstaltungen" :key="v.id" :value="v.id">
              {{ v.name }} ({{ formatDate(v.beginntAm) }})
            </option>
          </select>
        </div>
      </div>
    </div>

    <!-- Tab-Navigation -->
    <div class="border-b border-gray-200">
      <nav class="-mb-px flex space-x-6 overflow-x-auto">
        <button v-for="tab in visibleTabs" :key="tab"
                @click="handleTabClick(tab)"
                :class="[activeTab === tab ? 'border-indigo-500 text-indigo-600' : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300', 'whitespace-nowrap py-3 px-1 border-b-2 font-medium text-xs capitalize']">
          {{ tabLabels[tab] || tab }}
        </button>
      </nav>
    </div>

    <!-- START-ZUSTAND (Empty State) -->
    <div v-if="!selectedVid && !['veranstaltungen', 'gebaeude', 'administratoren', 'protokoll'].includes(activeTab)"
         class="bg-indigo-50 p-8 rounded-2xl text-center border-2 border-dashed border-indigo-200 animate-fade-in">
      <div class="text-indigo-400 mb-3 flex justify-center">
        <CalendarIcon class="w-10 h-10"/>
      </div>
      <h2 class="text-lg font-bold text-indigo-900">Keine Veranstaltung ausgewählt</h2>
      <p class="text-xs text-indigo-600 mt-1 mb-5">Bitte wählen Sie oben eine Veranstaltung aus oder legen Sie eine neue an.</p>
      <div class="flex justify-center gap-3">
        <button @click="activeTab = 'veranstaltungen'"
                class="bg-white text-indigo-700 px-4 py-2 rounded-xl font-bold border border-indigo-200 shadow-sm text-xs">Zu
          den Veranstaltungen
        </button>
        <button @click="openVeranstaltungEditor(null)" class="btn-primary flex items-center gap-2 text-xs">
          <PlusCircleIcon class="w-4 h-4"/>
          Neu anlegen
        </button>
      </div>
    </div>

    <!-- TABS CONTENT -->
    <ErgebnisseTab v-if="activeTab === 'ergebnisse' && selectedVid"
                   :belegungsPlan="belegungsplan"
                   :qualitaet="qualitaet"
                   :eventSlots="eventSlots"
                   :raeume="filteredRaeume"
                   @downloadStundenplan="downloadStundenplan"
                   @downloadRaumschilder="downloadRaumschilder"
    />

    <VeranstaltungenTab v-if="activeTab === 'veranstaltungen'"
                        :veranstaltungen="veranstaltungen"
                        :selectedVid="selectedVid"
                        :vortraege="vortraege"
                        :teilnehmer="teilnehmer"
                        :pageSize="pageSize"
                        :canImportVeranstaltung="canImportVeranstaltung"
                        @triggerUpload="triggerUpload"
                        @openVeranstaltungEditor="openVeranstaltungEditor"
                        @deleteVeranstaltung="deleteVeranstaltung"
                        @selectVeranstaltung="handleVeranstaltungSelection"
    />

    <GebaeudeTab v-if="activeTab === 'gebaeude'"
                 :gebaeude="gebaeude"
                 :pageSize="pageSize"
                 @triggerUpload="triggerUpload"
                 @openGebaeudeEditor="openGebaeudeEditor"
                 @deleteGebaeude="deleteGebaeude"
                 @openRaumEditor="openRaumEditor"
                 @deleteRaum="deleteRaum"
    />

    <AdministratorenTab v-if="activeTab === 'administratoren'"
                        :admins="admins"
                        :pageSize="pageSize"
                        @triggerUpload="triggerUpload"
                        @openUserModal="openUserModal"
                        @deleteUser="deleteUser"
    />

    <TeilnehmerTab v-if="activeTab === 'teilnehmer' && selectedVid"
                   :teilnehmer="teilnehmer"
                   :selectedVid="selectedVid"
                   :pageSize="pageSize"
                   :sortedSlots="sortedSlots"
                   :isEventFinished="isEventFinished"
                   :wahlvortraege="wahlvortraege"
                   :participantPriorities="participantPriorities"
                   :changedPriorities="changedPriorities"
                   @triggerUpload="triggerUpload"
                   @openUserModal="openUserModal"
                   @deleteUser="deleteUser"
                   @toggleParticipantActive="toggleParticipantActive"
                   @batchDeactivateParticipants="batchDeactivateParticipants"
                   @batchDeleteParticipants="batchDeleteParticipants"
                   @batchEmailParticipants="batchEmailParticipants"
                   @openInviteModal="openInviteModal"
                   @saveParticipantPriorities="saveParticipantPriorities"
                   @saveAllParticipantPriorities="saveAllParticipantPriorities"
    />

    <ReferentenTab v-if="activeTab === 'referenten' && selectedVid"
                   :referenten="referenten"
                   :selectedVid="selectedVid"
                   :pageSize="pageSize"
                   :sortedSlots="sortedSlots"
                   :isEventFinished="isEventFinished"
                   @triggerUpload="triggerUpload"
                   @openUserModal="openUserModal"
                   @deleteUser="deleteUser"
                   @openInviteModal="openInviteModal"
    />

    <VortraegeTab v-if="activeTab === 'vortraege' && selectedVid"
                  :vortraege="vortraege"
                  :selectedVid="selectedVid"
                  :pageSize="pageSize"
                  :canImportVortraege="canImportVortraege"
                  @triggerUpload="triggerUpload"
                  @openVortragEditor="openVortragEditor"
                  @deleteVortrag="deleteVortrag"
    />

    <SlotsTab v-if="activeTab === 'slots' && selectedVid"
              :eventSlots="eventSlots"
              :selectedVid="selectedVid"
              :pageSize="pageSize"
              @triggerUpload="triggerUpload"
              @openSlotEditor="openSlotEditor"
              @deleteSlot="deleteSlot"
    />

    <PlanungTab v-if="activeTab === 'planung' && selectedVid"
                :isPlanning="isOptimizing"
                :veranstaltung="eventContext.selectedEvent"
                :organisatoren="organisatoren"
                :eventSlotsCount="eventSlots.length"
                :gebaeudeDetails="gebaeudeDetails"
                :referentenDetails="filteredReferenten"
                :teilnehmerCount="filteredTeilnehmer.length"
                :wahlvortraegeCount="wahlvortraegeCount"
                :pflichtvortraegeCount="pflichtvortraegeCount"
                :teilnehmerMitPrioritaetenCount="teilnehmerMitPrioritaetenCount"
                @startOptimization="startPlanning"
    />

    <ProtokollTab v-if="activeTab === 'protokoll'"
                  :protokolle="protokolle"
    />

    <!-- Global File Input -->
    <input type="file" ref="fileInput" class="hidden" @change="handleGlobalUpload" accept=".csv"/>

    <!-- Modals -->
    <VeranstaltungEditorModal :isVisible="showVeranstaltungModal" :veranstaltung="selectedVeranstaltung"
                              :admins="admins" :allGebaeude="gebaeude" @close="showVeranstaltungModal = false"
                              @save="handleSaveVeranstaltung"/>
    <GebaeudeEditorModal :isVisible="showGebaeudeModal" :gebaeude="selectedGebaeude" @close="showGebaeudeModal = false"
                         @save="handleSaveGebaeude"/>
    <RaumEditorModal :isVisible="showRaumModal" :raum="selectedRaum" :slots="eventSlots" :gebaeude="gebaeude"
                     @close="showRaumModal = false" @save="handleSaveRaum"/>
    <UserEditorModal :isVisible="showUserModal" :nutzer="selectedUser" :eventSlots="eventSlots"
                     @close="showUserModal = false" @save="handleSaveUser"/>
    <AdminVortragEditorModal :isVisible="showVortragModal" :vortrag="selectedVortrag" :referenten="referenten"
                             :raeume="raeume" :slots="eventSlots" :participantGroups="teilnehmerGruppen"
                             :error="vortragModalError" @close="closeVortragModal" @save="handleSaveVortrag"/>
    <EventSlotEditorModal :isVisible="showSlotModal" :slot="selectedSlot" @close="showSlotModal = false"
                          @save="handleSaveSlot"/>
    <InviteUserModal :isVisible="showInviteModal" :user="selectedUserForInvite" :futureEvents="futureEvents"
                     @close="showInviteModal = false" @invite="handleInviteUser"/>

    <!-- CSV Import Feedback Modal -->
    <div v-if="showCsvFeedbackModal" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4">
      <div class="w-full max-w-sm rounded-xl bg-white p-5 shadow-2xl">
        <h3 class="text-base font-bold text-gray-900 mb-3">CSV Import Ergebnis</h3>
        <p class="text-sm mb-1">Erfolgreich: <span class="font-bold text-green-600">{{
            csvFeedback.successCount
          }}</span></p>
        <p v-if="csvFeedback.errorCount > 0" class="text-sm mb-3">Fehler: <span
            class="font-bold text-red-600">{{ csvFeedback.errorCount }}</span></p>
        <p v-if="csvFeedback.errorMessage" class="text-red-500 text-xs mb-3">{{ csvFeedback.errorMessage }}</p>
        <button @click="showCsvFeedbackModal = false" class="btn-primary w-full py-1.5 text-xs">Schließen</button>
      </div>
    </div>

    <!-- Global Loading Spinner -->
    <div v-if="isGlobalLoading"
         class="fixed inset-0 z-[100] flex items-center justify-center bg-black/30 backdrop-blur-[2px]">
      <div class="bg-white p-6 rounded-2xl shadow-2xl flex flex-col items-center gap-3 animate-fade-in">
        <div class="relative">
          <LoaderIcon class="w-10 h-10 text-indigo-600 animate-spin"/>
        </div>
        <p class="text-indigo-900 font-bold text-sm">CSV Import läuft...</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import {computed, onMounted, reactive, ref} from 'vue';
import api from '../api/axios';
import {useEventContextStore} from '../stores/eventContext';
import {useAvailabilityStore} from '../stores/availability';
import { useGroupStore } from '../stores/group';
import {
  Calendar as CalendarIcon,
  FileText as FileTextIcon,
  Loader as LoaderIcon,
  PlusCircle as PlusCircleIcon,
} from '@lucide/vue';

// Import Tab Components
import ErgebnisseTab from '../components/admin/tabs/ErgebnisseTab.vue';
import VeranstaltungenTab from '../components/admin/tabs/VeranstaltungenTab.vue';
import GebaeudeTab from '../components/admin/tabs/GebaeudeTab.vue';
import AdministratorenTab from '../components/admin/tabs/AdministratorenTab.vue';
import TeilnehmerTab from '../components/admin/tabs/TeilnehmerTab.vue';
import ReferentenTab from '../components/admin/tabs/ReferentenTab.vue';
import VortraegeTab from '../components/admin/tabs/VortraegeTab.vue';
import SlotsTab from '../components/admin/tabs/SlotsTab.vue';
import PlanungTab from '../components/admin/tabs/PlanungTab.vue';
import ProtokollTab from '../components/admin/tabs/ProtokollTab.vue';

// Import Modals
import AdminVortragEditorModal from '../components/AdminVortragEditorModal.vue';
import UserEditorModal from '../components/UserEditorModal.vue';
import VeranstaltungEditorModal from '../components/VeranstaltungEditorModal.vue';
import RaumEditorModal from '../components/RaumEditorModal.vue';
import EventSlotEditorModal from '../components/EventSlotEditorModal.vue';
import GebaeudeEditorModal from '../components/GebaeudeEditorModal.vue';
import InviteUserModal from '../components/InviteUserModal.vue';

const eventContext = useEventContextStore();
const availabilityStore = useAvailabilityStore();
const groupStore = useGroupStore();

const tabLabels = {
  administratoren: 'Organisatoren',
  gebaeude: 'Gebäude',
  veranstaltungen: 'Veranstaltungen',
  teilnehmer: 'Teilnehmer',
  referenten: 'Referenten',
  vortraege: 'Vorträge',
  slots: 'Zeit-Slots',
  planung: 'Planerstellung',
  ergebnisse: 'Ergebnisse',
  protokoll: 'Protokoll'
};

// State
const activeTab = ref('veranstaltungen');
const selectedVid = ref(null);
const veranstaltungen = ref([]);
const gebaeude = ref([]);
const raeume = ref([]); // This will hold ALL rooms
const users = ref([]);
const vortraege = ref([]);
const eventSlots = ref([]);
const belegungsplan = ref([]);
const qualitaet = ref({});
const protokolle = ref([]);
const participantPriorities = ref({});
const originalParticipantPriorities = ref({});
const changedPriorities = ref(new Set());

const isGlobalLoading = ref(false);
const pageSize = 15;

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
const vortragModalError = ref('');
const showSlotModal = ref(false);
const selectedSlot = ref(null);
const showInviteModal = ref(false);
const selectedUserForInvite = ref(null);

const isOptimizing = ref(false);
const fileInput = ref(null);
const currentUploadEndpoint = ref('');

const showCsvFeedbackModal = ref(false);
const csvFeedback = reactive({
  successCount: 0,
  errorCount: 0,
  errorMessage: ''
});

const visibleTabs = computed(() => {
  if (selectedVid.value) return ['administratoren', 'gebaeude',
    'teilnehmer', 'referenten', 'vortraege',
    'veranstaltungen', 'slots',
    'planung', 'ergebnisse',
    'protokoll'];
  return ['administratoren', 'gebaeude',
    'veranstaltungen',
    'protokoll'];
});

const futureEvents = computed(() => {
  const now = new Date();
  return veranstaltungen.value.filter(v => {
    const endDate = v.endetAm ? new Date(v.endetAm) : new Date(v.beginntAm);
    return endDate > now;
  });
});

const isEventFinished = computed(() => {
  if (!selectedVid.value) return false;
  const v = veranstaltungen.value.find(ev => ev.id === selectedVid.value);
  if (!v) return false;
  const endDate = v.endetAm ? new Date(v.endetAm) : new Date(v.beginntAm);
  return endDate < new Date();
});

const admins = computed(() => users.value.filter(u => u.role === 'ADMIN'));
const referenten = computed(() => users.value.filter(u => u.role === 'REFERENT'));
const teilnehmer = computed(() => users.value.filter(u => u.role === 'TEILNEHMER'));

const filteredReferenten = computed(() => {
  if (!selectedVid.value) return [];
  return referenten.value.filter(r => r.veranstaltungIds.includes(selectedVid.value));
});

const filteredTeilnehmer = computed(() => {
  if (!selectedVid.value) return [];
  return teilnehmer.value.filter(t => t.veranstaltungIds.includes(selectedVid.value));
});

const teilnehmerGruppen = computed(() => {
  const groups = new Set(teilnehmer.value.flatMap(t => t.gruppen).filter(Boolean));
  return Array.from(groups).sort();
});

const sortedSlots = computed(() => {
  return [...eventSlots.value].sort((a, b) => new Date(a.startTime) - new Date(b.startTime));
});

const wahlvortraege = computed(() => {
  return vortraege.value.filter(v => !v.istPflicht).sort((a, b) => a.titel.localeCompare(b.titel));
});

const canImportVeranstaltung = computed(() => admins.value.length > 0 && gebaeude.value.length > 0);
const canImportVortraege = computed(() => eventSlots.value.length > 0);

const organisatoren = computed(() => {
  if (!eventContext.selectedEvent || !eventContext.selectedEvent.organisatorIds || users.value.length === 0) return [];
  return eventContext.selectedEvent.organisatorIds
      .map(orgId => users.value.find(u => u.id === orgId))
      .filter(Boolean)
      .map(u => `${u.firstName} ${u.lastName}`);
});

const gebaeudeDetails = computed(() => {
  if (!eventContext.selectedEvent || !eventContext.selectedEvent.gebaeude || gebaeude.value.length === 0) return [];
  return eventContext.selectedEvent.gebaeude.map(eventGebaeude => {
    const fullGebaeude = gebaeude.value.find(g => g.id === eventGebaeude.id);
    if (!fullGebaeude) return null;
    const raumCount = fullGebaeude.raeume ? fullGebaeude.raeume.length : 0;
    const kapazitaetGesamt = fullGebaeude.raeume ? fullGebaeude.raeume.reduce((sum, r) => sum + (r.kapazitaet || 0), 0) : 0;
    return {
      name: fullGebaeude.name,
      raumCount,
      kapazitaetGesamt: kapazitaetGesamt
    };
  }).filter(Boolean);
});

const referentenDetails = computed(() => {
  return referenten.value.map(r => ({
    name: `${r.firstName} ${r.lastName}`,
    organisation: r.organisation || 'N/A'
  }));
});

const wahlvortraegeCount = computed(() => vortraege.value.filter(v => !v.istPflicht).length);
const pflichtvortraegeCount = computed(() => vortraege.value.filter(v => v.istPflicht).length);

const teilnehmerMitPrioritaetenCount = computed(() => {
  return Object.values(participantPriorities.value).filter(prios => Object.values(prios).some(p => p.prio > 0)).length;
});

onMounted(async () => {
  await refreshVeranstaltungen();
  await refreshGebaeude();
  await refreshAdmins();
  if (selectedVid.value) handleVeranstaltungChange();
});

const refreshVeranstaltungen = async () => {
  try {
    const res = await api.get('/api/veranstaltungen');
    veranstaltungen.value = res.data;
  } catch (e) {
    console.error('Fehler beim Laden der Veranstaltungen:', e);
  }
};
const refreshGebaeude = async () => {
  try {
    const res = await api.get('/api/gebaeude');
    gebaeude.value = res.data;
    // raeume.value = gebaeude.value.flatMap(g => g.raeume.map(r => ({...r, gebaeude: {id: g.id, name: g.name}}))); // This line is now handled by filteredRaeume
  } catch (e) {
    console.error('Fehler beim Laden der Gebäude:', e);
  }
};
const refreshAdmins = async () => {
  try {
    const res = await api.get('/api/admin/nutzer');
    users.value = res.data;
  } catch (e) {
    console.error('Fehler beim Laden der Administratoren:', e);
  }
};

const refreshProtokolle = async () => {
  try {
    const res = await api.get('/api/admin/protokolle');
    protokolle.value = res.data;
  } catch (e) {
    console.error('Fehler beim Laden des Protokolls:', e);
  }
};

// NEU: Gefilterte Räume für die aktuelle Veranstaltung
const filteredRaeume = computed(() => {
  if (!eventContext.selectedEvent || !gebaeude.value.length) return [];

  const eventGebaeudeIds = new Set(eventContext.selectedEvent.gebaeude.map(g => g.id));

  return gebaeude.value
      .filter(g => eventGebaeudeIds.has(g.id)) // Nur Gebäude der aktuellen Veranstaltung
      .flatMap(g => g.raeume.map(r => ({...r, gebaeude: {id: g.id, name: g.name}})));
});


const handleVeranstaltungChange = () => {
  const ev = veranstaltungen.value.find(v => v.id === selectedVid.value);
  if (ev) eventContext.setEvent(ev);
  else eventContext.clearEvent();
  loadData();
};

const handleVeranstaltungSelection = (vid) => {
  selectedVid.value = vid;
  handleVeranstaltungChange();
};

const handleTabClick = (tab) => {
  activeTab.value = tab;
  if (tab === 'protokoll') {
    refreshProtokolle();
  }
};

const loadData = async () => {
  if (!selectedVid.value) return;
  const base = `/api/veranstaltungen/${selectedVid.value}`;
  try {
    const [eventSpecificUsersRes, vRes, sRes, pRes, qRes, allUsersRes] = await Promise.all([
      api.get(`${base}/nutzer`),
      api.get(`${base}/vortraege`),
      api.get(`${base}/slots`),
      api.get(`${base}/plan/details`),
      api.get(`${base}/plan/qualitaet`),
      api.get('/api/admin/nutzer'),
      groupStore.fetchGruppen(selectedVid.value)
    ]);

    await availabilityStore.fetchAvailabilities(selectedVid.value);

    const combinedUsersMap = new Map();
    allUsersRes.data.forEach(user => combinedUsersMap.set(user.id, user));
    eventSpecificUsersRes.data.forEach(user => combinedUsersMap.set(user.id, user));
    users.value = Array.from(combinedUsersMap.values());

    vortraege.value = vRes.data;
    eventSlots.value = sRes.data;
    belegungsplan.value = pRes.data;
    qualitaet.value = qRes.data;

    const prioMap = {};
    const originalPrioMap = {};
    eventSpecificUsersRes.data.filter(u => u.role === 'TEILNEHMER').forEach(u => {
      prioMap[u.id] = {};
      originalPrioMap[u.id] = {};
      u.prioritaeten?.forEach(p => {
        prioMap[u.id][p.vortragId] = {prio: p.prio};
        originalPrioMap[u.id][p.vortragId] = {prio: p.prio};
      });
    });
    participantPriorities.value = prioMap;
    originalParticipantPriorities.value = originalPrioMap;
    changedPriorities.value.clear();

    if (activeTab.value === 'protokoll') refreshProtokolle();

  } catch (err) {
    console.error('Fehler beim Laden der Veranstaltungsdaten:', err);
  }
};

const openVeranstaltungEditor = (v) => {
  selectedVeranstaltung.value = v || {name: '', beginntAm: '', endetAm: '', gebaeude: [], organisatorIds: []};
  showVeranstaltungModal.value = true;
};
const handleSaveVeranstaltung = async (v) => {
  try {
    if (v.id) await api.put(`/api/veranstaltungen/${v.id}`, v);
    else {
      const res = await api.post('/api/veranstaltungen', v);
      if (veranstaltungen.value.length === 0) {
        selectedVid.value = res.data.id;
        handleVeranstaltungChange();
        activeTab.value = 'veranstaltungen';
      }
    }
    showVeranstaltungModal.value = false;
    await refreshVeranstaltungen();
  } catch (e) {
    console.error('Fehler beim Speichern der Veranstaltung:', e);
  }
};
const deleteVeranstaltung = async (id) => {
  if (confirm("Löschen?")) {
    try {
      await api.delete(`/api/veranstaltungen/${id}`);
      if (selectedVid.value === id) {
        selectedVid.value = null;
        eventContext.clearEvent();
      }
      await refreshVeranstaltungen();
    } catch (e) {
      console.error('Fehler beim Löschen der Veranstaltung:', e);
    }
  }
};

const openGebaeudeEditor = (g) => {
  selectedGebaeude.value = g || {name: '', typ: 'SCHULE', strasse: '', hausnummer: '', postleitzahl: '', ort: ''};
  showGebaeudeModal.value = true;
};
const handleSaveGebaeude = async (g) => {
  try {
    if (g.id) await api.put(`/api/gebaeude/${g.id}`, g); else await api.post('/api/gebaeude', g);
    showGebaeudeModal.value = false;
    await refreshGebaeude();
  } catch (e) {
    console.error('Fehler beim Speichern des Gebäudes:', e);
  }
};
const deleteGebaeude = async (id) => {
  if (confirm("Löschen?")) {
    try {
      await api.delete(`/api/gebaeude/${id}`);
      await refreshGebaeude();
    } catch (e) {
      console.error('Fehler beim Löschen des Gebäudes:', e);
    }
  }
};

const openRaumEditor = (r, buildingId = null) => {
  selectedRaum.value = r || {name: '', kapazitaet: 10, gebaeude: {id: buildingId || gebaeude.value[0]?.id}};
  showRaumModal.value = true;
};
const handleSaveRaum = async (r) => {
  const url = r.id ? `/api/gebaeude/${r.gebaeude.id}/raeume/${r.id}` : `/api/gebaeude/${r.gebaeude.id}/raeume`;
  try {
    if (r.id) await api.put(url, r); else await api.post(url, r);
    showRaumModal.value = false;
    await refreshGebaeude();
  } catch (e) {
    console.error('Fehler beim Speichern des Raumes:', e);
  }
};
const deleteRaum = async (r) => {
  if (confirm("Löschen?")) {
    try {
      await api.delete(`/api/gebaeude/${r.gebaeude.id}/raeume/${r.id}`);
      await refreshGebaeude();
    } catch (e) {
      console.error('Fehler beim Löschen des Raumes:', e);
    }
  }
};

const openUserModal = (u) => {
  selectedUser.value = u?.id ? {...u} : {
    firstName: '',
    lastName: '',
    email: '',
    role: u?.role || 'TEILNEHMER',
    isActive: true,
    gruppen: [],
    veranstaltungIds: selectedVid.value ? [selectedVid.value] : []
  };
  showUserModal.value = true;
};
const handleSaveUser = async (u) => {
  const isGlobalAdmin = u.role === 'ADMIN';
  let endpoint;
  if (isGlobalAdmin) endpoint = `/api/admin/nutzer`;
  else if (selectedVid.value) endpoint = `/api/veranstaltungen/${selectedVid.value}/nutzer`;
  else return;

  try {
    if (u.id) await api.put(`${endpoint}/${u.id}`, u);
    else await api.post(endpoint, u);
    showUserModal.value = false;
    await loadData();
    await refreshAdmins();
  } catch (e) {
    console.error('Fehler beim Speichern des Nutzers:', e);
  }
};
const deleteUser = async (id) => {
  if (confirm("Löschen?")) {
    try {
      const userToDelete = users.value.find(u => u.id === id);
      let endpoint;
      if (userToDelete && userToDelete.role === 'ADMIN') endpoint = `/api/admin/nutzer/${id}`;
      else if (selectedVid.value) endpoint = `/api/veranstaltungen/${selectedVid.value}/nutzer/${id}`;
      else return;

      await api.delete(endpoint);
      await loadData();
      await refreshAdmins();
    } catch (e) {
      console.error('Fehler beim Löschen des Nutzers:', e);
    }
  }
};

const toggleParticipantActive = async (u) => {
  try {
    await api.put(`/api/veranstaltungen/${selectedVid.value}/nutzer/${u.id}`, {...u, isActive: !u.isActive});
    u.isActive = !u.isActive;
  } catch (e) {
    console.error("Fehler beim Umschalten des Status:", e);
  }
};

const batchDeactivateParticipants = async (selectedParticipantIds) => {
  if (!confirm(`${selectedParticipantIds.length} Teilnehmer deaktivieren?`)) return;
  try {
    for (const id of selectedParticipantIds) {
      const u = teilnehmer.value.find(p => p.id === id);
      if (u) await api.put(`/api/veranstaltungen/${selectedVid.value}/nutzer/${u.id}`, {...u, isActive: false});
    }
    await loadData();
    alert("Erfolgreich deaktiviert.");
  } catch (e) {
    console.error("Fehler bei Batch-Deaktivierung:", e);
  }
};

const batchDeleteParticipants = async (selectedParticipantIds) => {
  if (!confirm(`${selectedParticipantIds.length} Teilnehmer endgültig löschen?`)) return;
  try {
    for (const id of selectedParticipantIds) {
      await api.delete(`/api/veranstaltungen/${selectedVid.value}/nutzer/${id}`);
    }
    await loadData();
    alert("Erfolgreich gelöscht.");
  } catch (e) {
    console.error("Fehler bei Batch-Löschung:", e);
  }
};

const batchEmailParticipants = (selectedParticipantIds) => {
  const emails = teilnehmer.value
      .filter(p => selectedParticipantIds.includes(p.id))
      .map(p => p.email)
      .join(',');
  window.location.href = `mailto:?bcc=${emails}`;
};

const openInviteModal = (u) => {
  selectedUserForInvite.value = u;
  showInviteModal.value = true;
};
const handleInviteUser = async ({userId, eventId}) => {
  try {
    await api.post(`/api/admin/nutzer/${userId}/einladen/${eventId}`);
    alert("Einladung erfolgreich versendet!");
    showInviteModal.value = false;
    await loadData();
  } catch (e) {
    alert("Fehler beim Einladen: " + (e.response?.data || e.message));
  }
};

const openVortragEditor = (v) => {
  vortragModalError.value = '';
  selectedVortrag.value = v?.id ? {...v} : {
    titel: '',
    inhalt: '',
    ausstattung: '',
    referent: {id: null},
    vortrag_typ: 'WAHL',
    pflichtgruppe: '',
    pflichtraum: {id: null},
    pflichtslot: {id: null},
  };
  showVortragModal.value = true;
};

const closeVortragModal = () => {
  showVortragModal.value = false;
  vortragModalError.value = '';
};

const handleSaveVortrag = async (v) => {
  const base = `/api/veranstaltungen/${selectedVid.value}/vortraege`;
  try {
    if (v.id) await api.put(`${base}/${v.id}`, v); else await api.post(base, v);
    showVortragModal.value = false;
    vortragModalError.value = '';
    await loadData();
  } catch (e) {
    console.error('Fehler beim Speichern des Vortrags:', e);
    vortragModalError.value = e.response?.data?.message || e.message || 'Unbekannter Fehler beim Speichern des Vortrags.';
  }
};
const deleteVortrag = async (id) => {
  if (confirm("Löschen?")) {
    try {
      await api.delete(`/api/veranstaltungen/${selectedVid.value}/vortraege/${id}`);
      await loadData();
    } catch (e) {
      console.error('Fehler beim Löschen des Vortrags:', e);
      alert("Fehler beim Löschen des Vortrags: " + (e.response?.data?.message || e.message));
    }
  }
};

const openSlotEditor = (s) => {
  selectedSlot.value = s || {description: '', startTime: '', endTime: ''};
  showSlotModal.value = true;
};
const handleSaveSlot = async (s) => {
  const base = `/api/veranstaltungen/${selectedVid.value}/slots`;
  try {
    if (s.id) await api.put(`${base}/${s.id}`, s); else await api.post(base, s);
    showSlotModal.value = false;
    await loadData();
  } catch (e) {
    console.error('Fehler beim Speichern des Slots:', e);
  }
};
const deleteSlot = async (id) => {
  if (confirm("Löschen?")) {
    try {
      await api.delete(`/api/veranstaltungen/${selectedVid.value}/slots/${id}`);
      await loadData();
    } catch (e) {
      console.error('Fehler beim Löschen des Slots:', e);
    }
  }
};

const saveParticipantPriorities = async (userId) => {
  const currentUserPrios = participantPriorities.value[userId];
  const originalUserPrios = originalParticipantPriorities.value[userId] || {};
  const changedPayload = [];

  for (const talkId in currentUserPrios) {
    const currentPrio = currentUserPrios[talkId].prio;
    const originalPrio = originalUserPrios[talkId]?.prio;
    if (currentPrio !== originalPrio) {
      changedPayload.push({vortragId: parseInt(talkId), prio: currentPrio});
    }
  }

  for (const talkId in originalUserPrios) {
    if (!(talkId in currentUserPrios) || currentUserPrios[talkId].prio === 0 && originalUserPrios[talkId].prio !== 0) {
      if (!changedPayload.some(item => item.vortragId === parseInt(talkId))) {
        changedPayload.push({vortragId: parseInt(talkId), prio: 0});
      }
    }
  }

  if (changedPayload.length === 0) {
    changedPriorities.value.delete(userId);
    return;
  }

  try {
    await api.put(`/api/admin/veranstaltungen/${selectedVid.value}/teilnehmer/${userId}/priorities`, changedPayload);
    originalParticipantPriorities.value[userId] = JSON.parse(JSON.stringify(currentUserPrios));
    changedPriorities.value.delete(userId);
  } catch (e) {
    console.error("Fehler beim Speichern der Prioritäten:", e);
    alert("Fehler beim Speichern der Prioritäten.");
  }
};

const saveAllParticipantPriorities = async () => {
  const promises = [];
  for (const userId of changedPriorities.value) {
    promises.push(saveParticipantPriorities(userId));
  }
  try {
    await Promise.all(promises);
    alert('Alle geänderten Prioritäten gespeichert!');
  } catch (e) {
    console.error('Fehler beim Speichern aller Prioritäten:', e);
  }
};

const startPlanning = async (solverConfig) => {
  isOptimizing.value = true;
  try {
    await api.post(`/api/veranstaltungen/${selectedVid.value}/planungen`, solverConfig);
    await loadData();
    activeTab.value = 'ergebnisse';
  } catch (e) {
    console.error('Fehler bei der Planerstellung:', e);
  } finally {
    isOptimizing.value = false;
  }
};

const downloadStundenplan = async () => {
  try {
    alert("Download des Stundenplans nicht implementiert"); // TODO Download des Stundenplans implementieren
    const res = await api.get(`/api/reports/${selectedVid.value}/raeume-pdf`, {responseType: 'blob'});
    const url = window.URL.createObjectURL(new Blob([res.data]));
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', 'stundenplan.pdf');
    document.body.appendChild(link);
    link.click();
  } catch (e) {
    console.error('Fehler beim Download des Stundenplans:', e);
  }
};

const downloadRaumschilder = async () => {
  try {
    const res = await api.get(`/api/reports/${selectedVid.value}/raeume-pdf`, {responseType: 'blob'});
    const url = window.URL.createObjectURL(new Blob([res.data]));
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', 'raumschilder.pdf');
    document.body.appendChild(link);
    link.click();
  } catch (e) {
    console.error('Fehler beim Download der Raumschilder:', e);
  }
};

const downloadFreieSlotsReferenten = async () => {
  try {
    const res = await api.get(`/api/reports/${selectedVid.value}/freie-slots-referenten-pdf`, {responseType: 'blob'});
    const url = window.URL.createObjectURL(new Blob([res.data]));
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', 'Referenten_freie_Slots.pdf');
    document.body.appendChild(link);
    link.click();
  } catch (e) {
    console.error('Fehler beim Download der freien Slots für Referenten:', e);
  }
};

const downloadFreieSlotsTeilnehmer = async () => {
  try {
    const res = await api.get(`/api/reports/${selectedVid.value}/freie-slots-teilnehmer-pdf`, {responseType: 'blob'});
    const url = window.URL.createObjectURL(new Blob([res.data]));
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', 'Teilnehmer_freie_Slots.pdf');
    document.body.appendChild(link);
    link.click();
  } catch (e) {
    console.error('Fehler beim Download der freien Slots für Teilnehmer:', e);
  }
};

const triggerUpload = (endpoint) => {
  if (endpoint === '/api/veranstaltungen/import' && !canImportVeranstaltung.value) return;
  if (endpoint.includes('/vortraege/import') && !canImportVortraege.value) return;
  currentUploadEndpoint.value = endpoint;
  fileInput.value.click();
};
const handleGlobalUpload = async (event) => {
  const file = event.target.files[0];
  if (!file) return;
  const formData = new FormData();
  formData.append('file', file);

  const endpoint = currentUploadEndpoint.value;
  const finalEndpoint = endpoint.replace('{vid}', selectedVid.value);

  isGlobalLoading.value = true;
  try {
    const res = await api.post(finalEndpoint, formData, {headers: {'Content-Type': 'multipart/form-data'}});
    showCsvFeedback(res.data, false);
    await loadData();
    await refreshVeranstaltungen();
    await refreshGebaeude();
    await refreshAdmins();
  } catch (e) {
    showCsvFeedback(e.response?.data || e.message, true);
  } finally {
    isGlobalLoading.value = false;
    event.target.value = '';
  }
};

const showCsvFeedback = (message, isError) => {
  csvFeedback.errorMessage = '';
  csvFeedback.successCount = 0;
  csvFeedback.errorCount = 0;
  if (isError) {
    csvFeedback.errorMessage = message;
    csvFeedback.errorCount = 1;
  } else {
    const match = message.match(/(\d+)\s.*angelegt/);
    csvFeedback.successCount = (match && match[1]) ? parseInt(match[1]) : 1;
  }
  showCsvFeedbackModal.value = true;
  if (!isError) setTimeout(() => {
    showCsvFeedbackModal.value = false;
  }, 3000);
};

const formatDate = (d) => d ? new Date(d).toLocaleDateString('de-DE') : '';
</script>

<style scoped>
.btn-primary {
  @apply rounded-lg bg-indigo-600 px-3 py-1.5 text-white font-bold hover:bg-indigo-700 transition shadow-sm border-none cursor-pointer disabled:opacity-50;
}

.btn-primary-xs {
  @apply rounded-md bg-indigo-600 px-2 py-0.5 text-white font-bold hover:bg-indigo-700 transition shadow-sm border-none cursor-pointer;
}

.btn-secondary {
  @apply bg-white text-gray-700 px-3 py-1.5 rounded-lg hover:bg-gray-50 font-bold border border-gray-200 transition shadow-sm cursor-pointer disabled:opacity-50;
}

.input-field {
  @apply rounded-lg border border-gray-300 px-2 py-1 text-gray-900 focus:ring-2 focus:ring-indigo-500 bg-white;
}

.animate-fade-in {
  animation: fadeIn 0.3s ease-in-out;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(5px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
