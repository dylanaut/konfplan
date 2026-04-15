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
      <div v-if="selectedVid" class="flex gap-2">
        <button @click="downloadTuerschilder" class="flex items-center gap-2 bg-indigo-600 text-white px-4 py-2 rounded-lg hover:bg-indigo-700 transition shadow-md text-sm font-bold">
          <FileTextIcon class="w-4 h-4"/> Türschilder (PDF)
        </button>
        <button @click="downloadExport" class="flex items-center gap-2 bg-gray-800 text-white px-4 py-2 rounded-lg hover:bg-gray-700 transition shadow-md text-sm font-bold">
          <DownloadIcon class="w-4 h-4"/> CSV Export
        </button>
      </div>
    </div>

    <!-- Tab-Navigation -->
    <div class="border-b border-gray-200">
      <nav class="-mb-px flex space-x-8 overflow-x-auto">
        <button v-for="tab in visibleTabs" :key="tab"
                @click="activeTab = tab"
                :class="[activeTab === tab ? 'border-indigo-500 text-indigo-600' : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300', 'whitespace-nowrap py-4 px-1 border-b-2 font-medium text-sm capitalize']">
          {{ tab === 'planung' ? 'Optimierung' : tab === 'ergebnisse' ? 'Ergebnisse' : tab === 'administratoren' ? 'Veranstalter' : tab }}
        </button>
      </nav>
    </div>

    <!-- START-ZUSTAND (Empty State) -->
    <div v-if="!selectedVid && !['veranstaltungen', 'gebäude', 'administratoren'].includes(activeTab)" class="bg-indigo-50 p-10 rounded-2xl text-center border-2 border-dashed border-indigo-200 animate-fade-in">
      <div class="text-indigo-400 mb-4 flex justify-center"><CalendarIcon class="w-12 h-12"/></div>
      <h2 class="text-xl font-bold text-indigo-900">Keine Veranstaltung ausgewählt</h2>
      <p class="text-indigo-600 mt-2 mb-6">Bitte wählen Sie oben eine Veranstaltung aus oder legen Sie eine neue an.</p>
      <div class="flex justify-center gap-4">
        <button @click="activeTab = 'veranstaltungen'" class="bg-white text-indigo-700 px-6 py-3 rounded-xl font-bold border border-indigo-200 shadow-sm">Zu den Veranstaltungen</button>
        <button @click="openVeranstaltungEditor(null)" class="btn-primary flex items-center gap-2">
          <PlusCircleIcon class="w-5 h-5"/> Neue Veranstaltung anlegen
        </button>
      </div>
    </div>

    <!-- TABS CONTENT -->

    <!-- TAB: ERGEBNISSE -->
    <section v-if="activeTab === 'ergebnisse' && selectedVid" class="space-y-6 animate-fade-in">
      <div class="grid grid-cols-1 md:grid-cols-4 gap-4">
        <div class="bg-white p-4 rounded-xl shadow-sm border border-gray-100">
          <div class="text-[10px] text-gray-500 uppercase font-bold">Ø Priorität</div>
          <div class="text-2xl font-black text-indigo-600">{{ qualitaet.durchschnittsPrio?.toFixed(2) || '0.00' }}</div>
        </div>
        <!-- ... weitere Metriken ... -->
      </div>
      <!-- Belegungsplan Tabelle -->
      <div class="bg-white shadow rounded-xl overflow-hidden border border-gray-100">
        <table class="min-w-full divide-y divide-gray-200">
          <thead class="bg-gray-50 text-[10px] uppercase font-bold text-gray-500">
          <tr><th class="px-6 py-3 text-left">Vortrag</th><th class="px-6 py-3 text-left">Zeit/Raum</th><th class="px-6 py-3 text-center">Belegung</th><th class="px-6 py-3 text-left">Teilnehmer</th></tr>
          </thead>
          <tbody class="divide-y divide-gray-100 text-sm">
          <tr v-for="b in belegungsPlan" :key="b.vortragTitel + b.slotZeit" class="hover:bg-gray-50 transition">
            <td class="px-6 py-4 font-bold">{{ b.vortragTitel }}</td>
            <td class="px-6 py-4">{{ b.slotZeit }} | {{ b.raumName }}</td>
            <td class="px-6 py-4 text-center">{{ b.teilnehmerNamen.length }} / {{ b.kapazitaet }}</td>
            <td class="px-6 py-4 text-[10px] text-gray-500">{{ b.teilnehmerNamen.join(', ') }}</td>
          </tr>
          </tbody>
        </table>
      </div>
    </section>

    <!-- TAB: OPTIMIERUNG (Planung) -->
    <section v-if="activeTab === 'planung' && selectedVid" class="space-y-6 animate-fade-in">
      <div class="bg-indigo-900 text-white p-8 rounded-2xl shadow-xl flex flex-col md:flex-row items-center justify-between gap-8">
        <div class="space-y-4">
          <h2 class="text-3xl font-black">Planung & Optimierung</h2>
          <div class="grid grid-cols-1 sm:grid-cols-2 gap-4 bg-white/10 p-4 rounded-xl">
            <select v-model="solverConfig.solver" class="bg-indigo-800 border-none rounded text-white text-sm">
              <option value="OR-tools">OR-Tools</option><option value="Gecode">Gecode</option>
            </select>
            <input v-model.number="solverConfig.timeout" type="number" class="bg-indigo-800 border-none rounded text-white text-sm" />
          </div>
        </div>
        <button @click="startOptimization" :disabled="isOptimizing" class="bg-green-500 px-10 py-5 rounded-2xl font-black text-xl flex items-center gap-3">
          <ZapIcon v-if="!isOptimizing"/><LoaderIcon v-else class="animate-spin"/>
          {{ isOptimizing ? 'Optimierung läuft...' : 'Jetzt Optimieren' }}
        </button>
      </div>
    </section>

    <!-- TAB: VERANSTALTUNGEN -->
    <section v-if="activeTab === 'veranstaltungen'" class="space-y-4">
      <div class="flex justify-between items-center bg-white p-4 rounded-xl border border-gray-100 shadow-sm">
        <h2 class="text-xl font-bold text-gray-800">Veranstaltungen</h2>
        <div class="flex gap-2">
          <button @click="triggerUpload('/api/veranstaltungen/import')" class="btn-secondary flex items-center gap-2">
            <UploadIcon class="w-4 h-4"/> CSV Import
          </button>
          <button @click="openVeranstaltungEditor(null)" class="btn-primary">+ Neu</button>
        </div>
      </div>
      <!-- Tabelle ... -->
      <div class="bg-white shadow rounded-xl overflow-hidden">
        <table class="min-w-full divide-y divide-gray-200">
          <tbody class="text-sm">
          <tr v-for="v in veranstaltungen" :key="v.id" :class="selectedVid === v.id ? 'bg-indigo-50' : ''">
            <td class="px-6 py-4 font-bold">{{ v.name }}</td>
            <td class="px-6 py-4">{{ formatDate(v.beginntAm) }}</td>
            <td class="px-6 py-4 text-right space-x-2">
              <button @click="selectedVid = v.id; loadData()" class="text-indigo-600 font-bold">Auswählen</button>
              <button @click="openVeranstaltungEditor(v)" class="text-gray-600">Bearbeiten</button>
              <button @click="deleteVeranstaltung(v.id)" class="text-red-600"><Trash2Icon class="w-4 h-4 inline"/></button>
            </td>
          </tr>
          </tbody>
        </table>
      </div>
    </section>

    <!-- TAB: GEBÄUDE -->
    <section v-if="activeTab === 'gebäude'" class="space-y-4">
      <div class="flex justify-between items-center bg-white p-4 rounded-xl border border-gray-100 shadow-sm">
        <h2 class="text-xl font-bold text-gray-800">Gebäude</h2>
        <div class="flex gap-2">
          <button @click="triggerUpload('/api/gebaeude/import')" class="btn-secondary flex items-center gap-2">
            <UploadIcon class="w-4 h-4"/> CSV Import
          </button>
          <button @click="openGebaeudeEditor(null)" class="btn-primary">+ Neu</button>
        </div>
      </div>
      <div class="bg-white shadow rounded-xl overflow-hidden">
        <table class="min-w-full divide-y divide-gray-200">
          <tbody class="text-sm">
          <tr v-for="g in gebaeude" :key="g.id">
            <td class="px-6 py-4 font-bold">{{ g.name }}</td>
            <td class="px-6 py-4">{{ g.strasse }} {{ g.hausnummer }}, {{ g.ort }}</td>
            <td class="px-6 py-4 text-right space-x-2">
              <button @click="openGebaeudeEditor(g)" class="text-indigo-600">Bearbeiten</button>
              <button @click="deleteGebaeude(g.id)" class="text-red-600 ml-2"><Trash2Icon class="w-4 h-4 inline"/></button>
            </td>
          </tr>
          </tbody>
        </table>
      </div>
    </section>

    <!-- TAB: ADMINISTRATOREN -->
    <section v-if="activeTab === 'administratoren'" class="space-y-4 animate-fade-in">
      <div class="flex justify-between items-center bg-white p-4 rounded-xl border border-gray-100 shadow-sm">
        <h2 class="text-xl font-bold text-gray-800">Globale Veranstalter</h2>
        <div class="flex gap-2">
          <button @click="triggerUpload('/api/admin/admins/import')" class="btn-secondary flex items-center gap-2">
            <UploadIcon class="w-4 h-4"/> CSV Import
          </button>
          <button @click="openUserModal({role: 'ADMIN'})" class="btn-primary">+ Neu</button>
        </div>
      </div>
      <div class="bg-white shadow rounded-xl overflow-hidden border border-gray-100">
        <table class="min-w-full divide-y divide-gray-200">
          <tbody class="text-sm">
          <tr v-for="a in admins" :key="a.id" class="hover:bg-gray-50 transition">
            <td class="px-6 py-4 font-bold">{{ a.lastName }}, {{ a.firstName }}</td>
            <td class="px-6 py-4 text-gray-600">{{ a.email }}</td>
            <td class="px-6 py-4 text-right space-x-3">
              <button @click="openUserModal(a)" class="text-indigo-600 hover:underline">Bearbeiten</button>
              <button @click="deleteUser(a.id)" class="text-red-600"><Trash2Icon class="w-4 h-4 inline"/></button>
            </td>
          </tr>
          </tbody>
        </table>
      </div>
    </section>

    <!-- KONTEXT-TABS (Nur wenn Vid ausgewählt) -->
    <template v-if="selectedVid">
      <!-- BENUTZER -->
      <section v-if="activeTab === 'benutzer'" class="space-y-4 animate-fade-in">
        <div class="flex justify-between items-center bg-white p-4 rounded-xl border border-gray-100 shadow-sm">
          <h2 class="text-xl font-bold text-gray-800">Personen (Referenten & Teilnehmer)</h2>
          <div class="flex gap-2">
            <button @click="triggerUpload(`/api/veranstaltungen/${selectedVid}/teilnehmer/import`)" class="btn-secondary">Teilnehmer CSV</button>
            <button @click="triggerUpload(`/api/veranstaltungen/${selectedVid}/referenten/import`)" class="btn-secondary">Referenten CSV</button>
            <button @click="openUserModal(null)" class="btn-primary">+ Neu</button>
          </div>
        </div>
        <div class="bg-white shadow rounded-xl overflow-hidden border border-gray-100">
          <table class="min-w-full divide-y divide-gray-200 text-sm">
            <tbody class="divide-y divide-gray-100">
            <tr v-for="u in filteredUsers" :key="u.id">
              <td class="px-6 py-4 font-bold">{{ u.lastName }}, {{ u.firstName }}</td>
              <td class="px-6 py-4">{{ u.email }} | <span class="px-2 py-0.5 rounded text-[10px] font-bold uppercase text-indigo-600">{{ u.role }}</span></td>
              <td class="px-6 py-4 text-right">
                <button @click="openUserModal(u)" class="text-indigo-600">Bearbeiten</button>
                <button @click="deleteUser(u.id)" class="text-red-600 ml-3"><Trash2Icon class="w-4 h-4 inline"/></button>
              </td>
            </tr>
            </tbody>
          </table>
        </div>
      </section>

      <!-- VORTRÄGE -->
      <section v-if="activeTab === 'vorträge'" class="space-y-4 animate-fade-in">
        <div class="flex justify-between items-center bg-white p-4 rounded-xl border border-gray-100 shadow-sm">
          <h2 class="text-xl font-bold text-gray-800">Vorträge</h2>
          <div class="flex gap-2">
            <button @click="triggerUpload(`/api/veranstaltungen/${selectedVid}/vortraege/import`)" class="btn-secondary">CSV Import</button>
            <button @click="openVortragEditor(null)" class="btn-primary">+ Neu</button>
          </div>
        </div>
        <div class="bg-white shadow rounded-xl overflow-hidden border border-gray-100">
          <table class="min-w-full divide-y divide-gray-200 text-sm">
            <tbody class="divide-y divide-gray-100">
            <tr v-for="v in vortraege" :key="v.id">
              <td class="px-6 py-4 font-bold">{{ v.titel }} <span v-if="v.istPflicht" class="text-[10px] text-red-600 ml-2">PFLICHT</span></td>
              <td class="px-6 py-4">{{ v.referent?.lastName }}</td>
              <td class="px-6 py-4 text-right">
                <button @click="openVortragEditor(v)" class="text-indigo-600">Bearbeiten</button>
                <button @click="deleteVortrag(v.id)" class="text-red-600 ml-3"><Trash2Icon class="w-4 h-4 inline"/></button>
              </td>
            </tr>
            </tbody>
          </table>
        </div>
      </section>

      <!-- SLOTS -->
      <section v-if="activeTab === 'slots'" class="space-y-4 animate-fade-in">
        <div class="flex justify-between items-center bg-white p-4 rounded-xl border border-gray-100 shadow-sm">
          <h2 class="text-xl font-bold text-gray-800">Zeit-Slots</h2>
          <button @click="openSlotEditor(null)" class="btn-primary">+ Neu</button>
        </div>
        <div class="bg-white shadow rounded-xl overflow-hidden p-6 border border-gray-100">
          <ul class="divide-y divide-gray-100">
            <li v-for="s in eventSlots" :key="s.id" class="py-2 flex justify-between items-center text-sm">
              <span>{{ s.description }} ({{ formatTime(s.startTime) }} - {{ formatTime(s.endTime) }})</span>
              <div class="space-x-2">
                <button @click="openSlotEditor(s)" class="text-indigo-600 text-sm">Bearbeiten</button>
                <button @click="deleteSlot(s.id)" class="text-red-600"><Trash2Icon class="w-4 h-4 inline"/></button>
              </div>
            </li>
          </ul>
        </div>
      </section>

      <!-- RÄUME -->
      <section v-if="activeTab === 'räume'" class="space-y-4 animate-fade-in">
        <div class="flex justify-between items-center bg-white p-4 rounded-xl border border-gray-100 shadow-sm">
          <h2 class="text-xl font-bold text-gray-800">Räume</h2>
          <button @click="openRaumEditor(null)" class="btn-primary">+ Neu</button>
        </div>
        <div class="bg-white shadow rounded-xl overflow-hidden border border-gray-100">
          <table class="min-w-full divide-y divide-gray-200 text-sm">
            <tbody class="divide-y divide-gray-100">
            <tr v-for="r in raeume" :key="r.id">
              <td class="px-6 py-4 font-bold">{{ r.name }} ({{ r.gebaeude?.name }})</td>
              <td class="px-6 py-4 text-center">{{ r.kapazitaet }} Plätze</td>
              <td class="px-6 py-4 text-right">
                <button @click="openRaumEditor(r)" class="text-indigo-600">Bearbeiten</button>
                <button @click="deleteRaum(r)" class="text-red-600 ml-3"><Trash2Icon class="w-4 h-4 inline"/></button>
              </td>
            </tr>
            </tbody>
          </table>
        </div>
      </section>

      <!-- STATS -->
      <section v-if="activeTab === 'stats'" class="space-y-6 animate-fade-in">
        <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          <div class="bg-white p-6 rounded-xl shadow-sm border border-gray-100">
            <h3 class="font-bold text-gray-900 mb-4 line-clamp-2 h-12">Statistik</h3>
            <div class="space-y-2">
              <div class="flex justify-between text-[10px] font-bold text-gray-500 uppercase"><span>Ø Priorität</span><span>{{ qualitaet.durchschnittsPrio?.toFixed(2) || '0.00' }}</span></div>
              <div class="flex justify-between text-[10px] font-bold text-gray-500 uppercase mt-4"><span>Gesamt Zuweisungen</span><span>{{ qualitaet.gesamtZuweisungen || 0 }}</span></div>
            </div>
          </div>
        </div>
      </section>

      <!-- ERGEBNISSE -->
      <section v-if="activeTab === 'ergebnisse'" class="space-y-6 animate-fade-in">
        <div class="grid grid-cols-1 md:grid-cols-4 gap-4">
          <div class="bg-white p-4 rounded-xl shadow-sm border border-gray-100">
            <div class="text-[10px] text-gray-500 uppercase font-bold">Ø Priorität</div>
            <div class="text-2xl font-black text-indigo-600">{{ qualitaet.durchschnittsPrio?.toFixed(2) || '0.00' }}</div>
          </div>
          <div class="bg-white p-4 rounded-xl shadow-sm border border-gray-100">
            <div class="text-[10px] text-gray-500 uppercase font-bold">Zuweisungen</div>
            <div class="text-2xl font-black text-gray-900">{{ qualitaet.gesamtZuweisungen || 0 }}</div>
          </div>
        </div>

        <div class="bg-white shadow rounded-xl overflow-hidden border border-gray-100">
          <table class="min-w-full divide-y divide-gray-200 text-sm">
            <thead class="bg-gray-50 text-[10px] uppercase font-bold text-gray-500">
            <tr><th class="px-6 py-3 text-left">Vortrag</th><th class="px-6 py-3 text-left">Zeit/Raum</th><th class="px-6 py-3 text-center">Belegung</th><th class="px-6 py-3 text-left">Teilnehmer</th></tr>
            </thead>
            <tbody class="divide-y divide-gray-100">
            <tr v-for="b in belegungsPlan" :key="b.vortragTitel + b.slotZeit" class="hover:bg-gray-50 transition">
              <td class="px-6 py-4 font-bold">{{ b.vortragTitel }}</td>
              <td class="px-6 py-4">{{ b.slotZeit }} | {{ b.raumName }}</td>
              <td class="px-6 py-4 text-center">{{ b.teilnehmerNamen.length }} / {{ b.kapazitaet }}</td>
              <td class="px-6 py-4 text-[10px] text-gray-500">{{ b.teilnehmerNamen.join(', ') }}</td>
            </tr>
            </tbody>
          </table>
        </div>
      </section>

      <!-- PLANUNG -->
      <section v-if="activeTab === 'planung'" class="space-y-6 animate-fade-in">
        <div class="bg-indigo-900 text-white p-8 rounded-2xl shadow-xl flex flex-col md:flex-row items-center justify-between gap-8">
          <div class="space-y-4 flex-1">
            <h2 class="text-3xl font-black">Planung & Optimierung</h2>
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-4 bg-white/10 p-4 rounded-xl border border-white/10">
              <div>
                <label class="block text-[10px] uppercase font-bold text-indigo-300 mb-1">MiniZinc Solver</label>
                <select v-model="solverConfig.solver" class="w-full bg-indigo-800 border-none rounded text-sm text-white focus:ring-2 focus:ring-green-400">
                  <option value="OR-tools">Google OR-Tools</option><option value="Gecode">Gecode</option><option value="COIN-BC">COIN-BC</option>
                </select>
              </div>
              <div>
                <label class="block text-[10px] uppercase font-bold text-indigo-300 mb-1">Timeout (Sek.)</label>
                <input v-model.number="solverConfig.timeout" type="number" class="w-full bg-indigo-800 border-none rounded text-sm text-white focus:ring-2 focus:ring-green-400" />
              </div>
            </div>
          </div>
          <button @click="startOptimization" :disabled="isOptimizing" class="bg-green-500 hover:bg-green-400 disabled:bg-gray-600 text-white px-10 py-5 rounded-2xl font-black text-xl shadow-2xl transition-all transform hover:scale-105 flex items-center gap-3">
            <ZapIcon v-if="!isOptimizing"/><LoaderIcon v-else class="animate-spin"/>
            {{ isOptimizing ? 'Optimierung läuft...' : 'Jetzt Optimieren' }}
          </button>
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

    <!-- CSV Import Feedback Modal -->
    <div v-if="showCsvFeedbackModal" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4">
      <div class="w-full max-w-md rounded-xl bg-white p-6 shadow-2xl">
        <h3 class="text-lg font-bold text-gray-900 mb-4">CSV Import Ergebnis</h3>
        <p class="mb-2">Erfolgreich importiert: <span class="font-bold text-green-600">{{ csvFeedback.successCount }}</span></p>
        <p v-if="csvFeedback.errorCount > 0" class="mb-4">Fehlerhafte Zeilen: <span class="font-bold text-red-600">{{ csvFeedback.errorCount }}</span></p>
        <p v-if="csvFeedback.errorMessage" class="text-red-500 text-sm mb-4">{{ csvFeedback.errorMessage }}</p>
        <button @click="showCsvFeedbackModal = false" class="btn-primary w-full">Schließen</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, reactive } from 'vue';
import api from '../api/axios';
import { Download as DownloadIcon, Trash2 as Trash2Icon, Upload as UploadIcon, Calendar as CalendarIcon, PlusCircle as PlusCircleIcon, Zap as ZapIcon, Loader as LoaderIcon, FileText as FileTextIcon } from 'lucide-vue-next';
import AdminVortragEditorModal from '../components/AdminVortragEditorModal.vue';
import UserEditorModal from '../components/UserEditorModal.vue';
import VeranstaltungEditorModal from '../components/VeranstaltungEditorModal.vue';
import RaumEditorModal from '../components/RaumEditorModal.vue';
import EventSlotEditorModal from '../components/EventSlotEditorModal.vue';
import GebaeudeEditorModal from '../components/GebaeudeEditorModal.vue';

// State
const activeTab = ref('ergebnisse');
const selectedVid = ref(null);
const veranstaltungen = ref([]);
const gebaeude = ref([]);
const raeume = ref([]);
const users = ref([]);
const vortraege = ref([]);
const referenten = ref([]);
const eventSlots = ref([]);
const stats = ref([]);
const belegungsPlan = ref([]);
const qualitaet = ref({});

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

const isOptimizing = ref(false);
const solverConfig = reactive({ solver: 'OR-tools', timeout: 120 });
const fileInput = ref(null);
const currentUploadEndpoint = ref('');

// CSV Feedback Modal State
const showCsvFeedbackModal = ref(false);
const csvFeedback = reactive({
  successCount: 0,
  errorCount: 0,
  errorMessage: ''
});

const visibleTabs = computed(() => {
  const base = ['veranstaltungen', 'gebäude', 'administratoren'];
  if (selectedVid.value) return ['ergebnisse', 'planung', 'benutzer', 'vorträge', 'slots', 'räume', ...base];
  return base;
});

const admins = computed(() => users.value.filter(u => u.role === 'ADMIN'));
const filteredUsers = computed(() => users.value.filter(u => u.role !== 'ADMIN'));

const hasGebaeudeForVeranstaltungImport = computed(() => gebaeude.value.length > 0);

onMounted(async () => {
  await refreshVeranstaltungen();
  await refreshGebaeude();
  await refreshAdmins();
});

const refreshVeranstaltungen = async () => { try { const res = await api.get('/api/veranstaltungen'); veranstaltungen.value = res.data; } catch (e) {console.log('exc ' +e + ' while refreshVeranstaltungen')} };
const refreshGebaeude = async () => { try { const res = await api.get('/api/gebaeude'); gebaeude.value = res.data; updateRaeumeList(); } catch (e) {console.log('exc ' +e + ' while refreshGebaeude')} };
const refreshAdmins = async () => { try { const res = await api.get('/api/admin/users'); users.value = res.data; } catch (e) {console.log('exc ' +e + ' while refreshAdmins')} };

const updateRaeumeList = () => {
  raeume.value = gebaeude.value.flatMap(g => g.raeume.map(r => ({ ...r, gebaeude: { id: g.id, name: g.name } })));
};

const loadData = async () => {
  if (!selectedVid.value) return;
  const base = `/api/veranstaltungen/${selectedVid.value}`;
  try {
    const [uRes, vRes, rRes, sRes, stRes, pRes, qRes] = await Promise.all([
      api.get(`${base}/benutzer`), api.get(`${base}/vortraege`), api.get(`${base}/referenten`),
      api.get(`${base}/slots`), api.get(`${base}/stats`), api.get(`${base}/plan/details`), api.get(`${base}/plan/qualitaet`)
    ]);
    const localUsers = uRes.data;
    const globalAdmins = users.value.filter(u => u.role === 'ADMIN');
    users.value = [...globalAdmins, ...localUsers];

    vortraege.value = vRes.data; referenten.value = rRes.data; eventSlots.value = sRes.data;
    stats.value = stRes.data; belegungsPlan.value = pRes.data; qualitaet.value = qRes.data;
  } catch (err) { console.error(err); }
};

// --- CRUD ACTIONS ---
const openVeranstaltungEditor = (v) => { selectedVeranstaltung.value = v || { name: '', beginntAm: '', endetAm: '', gebaeude: [], organisatorId: admins.value[0]?.id }; showVeranstaltungModal.value = true; };
const handleSaveVeranstaltung = async (v) => {
  try {
    if (v.id) await api.put(`/api/veranstaltungen/${v.id}`, v);
    else { const res = await api.post('/api/veranstaltungen', v); if (veranstaltungen.value.length === 0) { selectedVid.value = res.data.id; activeTab.value = 'ergebnisse'; } }
    showVeranstaltungModal.value = false; await refreshVeranstaltungen();
  } catch (e) { alert("Fehler!"); }
};
const deleteVeranstaltung = async (id) => { if (confirm("Löschen?")) { await api.delete(`/api/veranstaltungen/${id}`); if (selectedVid.value === id) selectedVid.value = null; await refreshVeranstaltungen(); } };

const openGebaeudeEditor = (g) => { selectedGebaeude.value = g || { name: '', typ: 'SCHULE', strasse: '', hausnummer: '', postleitzahl: '', ort: '' }; showGebaeudeModal.value = true; };
const handleSaveGebaeude = async (g) => { try { if (g.id) await api.put(`/api/gebaeude/${g.id}`, g); else await api.post('/api/gebaeude', g); showGebaeudeModal.value = false; await refreshGebaeude(); } catch (e) {} };
const deleteGebaeude = async (id) => { if (confirm("Löschen?")) { await api.delete(`/api/gebaeude/${id}`); await refreshGebaeude(); } };

const openRaumEditor = (r) => { selectedRaum.value = r || { name: '', kapazitaet: 10, gebaeude: { id: gebaeude.value[0]?.id } }; showRaumModal.value = true; };
const handleSaveRaum = async (r) => {
  const url = r.id ? `/api/gebaeude/${r.gebaeude.id}/raeume/${r.id}` : `/api/gebaeude/${r.gebaeude.id}/raeume`;
  try { if (r.id) await api.put(url, r); else await api.post(url, r); showRaumModal.value = false; await refreshGebaeude(); } catch (e) {}
};
const deleteRaum = async (r) => { if (confirm("Löschen?")) { await api.delete(`/api/gebaeude/${r.gebaeude.id}/raeume/${r.id}`); await refreshGebaeude(); } };

const openUserModal = (u) => { selectedUser.value = u || { firstName: '', lastName: '', email: '', role: 'TEILNEHMER', isActive: true, gruppe: '', veranstaltungId: selectedVid.value }; showUserModal.value = true; };
const handleSaveUser = async (u) => {
  const isGlobal = u.role === 'ADMIN';
  const base = isGlobal ? `/api/admin/users` : `/api/veranstaltungen/${selectedVid.value}/benutzer`;
  try { if (u.id) await api.put(`${base}/${u.id}`, u); else await api.post(base, u); showUserModal.value = false; loadData(); refreshAdmins(); } catch (e) {}
};
const deleteUser = async (id) => { if (confirm("Löschen?")) { try { await api.delete(`/api/admin/users/${id}`); loadData(); refreshAdmins(); } catch (e) {} } };

const openVortragEditor = (v) => { selectedVortrag.value = v || { titel: '', inhalt: '', zielgruppe: '', referent: { id: null }, vortrag_typ: 'WAHL' }; showVortragModal.value = true; };
const handleSaveVortrag = async (v) => {
  const base = `/api/veranstaltungen/${selectedVid.value}/vortraege`;
  try { if (v.id) await api.put(`${base}/${v.id}`, v); else await api.post(base, v); showVortragModal.value = false; loadData(); } catch (e) {}
};
const deleteVortrag = async (id) => { if (confirm("Löschen?")) { await api.delete(`/api/veranstaltungen/${selectedVid.value}/vortraege/${id}`); loadData(); } };

const openSlotEditor = (s) => { selectedSlot.value = s || { description: '', startTime: '', endTime: '' }; showSlotModal.value = true; };
const handleSaveSlot = async (s) => {
  const base = `/api/veranstaltungen/${selectedVid.value}/slots`;
  try { if (s.id) await api.put(`${base}/${s.id}`, s); else await api.post(base, s); showSlotModal.value = false; loadData(); } catch (e) {}
};
const deleteSlot = async (id) => { if (confirm("Löschen?")) { await api.delete(`/api/veranstaltungen/${selectedVid.value}/slots/${id}`); loadData(); } };

const startOptimization = async () => {
  isOptimizing.value = true;
  try { await api.post(`/api/veranstaltungen/${selectedVid.value}/optimierung/start`, solverConfig); await loadData(); activeTab.value = 'ergebnisse'; } catch (e) {} finally { isOptimizing.value = false; }
};

const downloadTuerschilder = async () => {
  try { const res = await api.get(`/api/veranstaltungen/${selectedVid.value}/export/tuerschilder`, { responseType: 'blob' }); const url = window.URL.createObjectURL(new Blob([res.data])); const link = document.createElement('a'); link.href = url; link.setAttribute('download', 'tuerschilder.pdf'); document.body.appendChild(link); link.click(); } catch (e) {}
};
const downloadExport = async () => {
  try { const res = await api.get(`/api/veranstaltungen/${selectedVid.value}/export/csv`, { responseType: 'blob' }); const url = window.URL.createObjectURL(new Blob([res.data])); const link = document.createElement('a'); link.href = url; link.setAttribute('download', 'planung.csv'); document.body.appendChild(link); link.click(); } catch (e) {}
};

const triggerUpload = (endpoint) => { currentUploadEndpoint.value = endpoint; fileInput.value.click(); };
const handleGlobalUpload = async (event) => {
  const file = event.target.files[0]; if (!file) return;
  const formData = new FormData(); formData.append('file', file);

  const endpoint = currentUploadEndpoint.value + (selectedVid.value && currentUploadEndpoint.value.includes('{vid}') ? '' : (selectedVid.value ? `?vid=${selectedVid.value}` : ''));
  const finalEndpoint = endpoint.replace('{vid}', selectedVid.value);

  try {
    const res = await api.post(finalEndpoint, formData, { headers: { 'Content-Type': 'multipart/form-data' } });
    showCsvFeedback(res.data, false); // Erfolgreich
    await loadData(); await refreshVeranstaltungen(); await refreshGebaeude(); await refreshAdmins();
  } catch (e) {
    showCsvFeedback(e.response?.data || e.message, true); // Fehler
  } finally { event.target.value = ''; }
};

const showCsvFeedback = (message, isError) => {
  csvFeedback.errorMessage = '';
  csvFeedback.successCount = 0;
  csvFeedback.errorCount = 0;

  if (isError) {
    csvFeedback.errorMessage = message;
    csvFeedback.errorCount = 1; // Einfachheitshalber 1 Fehler, wenn es ein generischer Fehler ist
  } else {
    const match = message.match(/(\d+)\s.*angelegt/);
    if (match && match[1]) {
      csvFeedback.successCount = parseInt(match[1]);
    } else {
      csvFeedback.successCount = 1; // Fallback
    }
  }
  showCsvFeedbackModal.value = true;

  if (!isError) {
    setTimeout(() => {
      showCsvFeedbackModal.value = false;
    }, 3000);
  }
};

const formatDate = (d) => d ? new Date(d).toLocaleDateString('de-DE') : '';
const formatTime = (t) => t ? new Date(t).toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' }) : '';
</script>

<style scoped>
.btn-primary { @apply rounded-lg bg-indigo-600 px-4 py-2 text-white font-bold hover:bg-indigo-700 transition shadow-sm text-sm border-none cursor-pointer; }
.btn-secondary { @apply bg-white text-gray-700 px-4 py-2 rounded-lg hover:bg-gray-50 font-bold border border-gray-200 transition shadow-sm text-sm cursor-pointer; }
.input-field { @apply rounded-lg border border-gray-300 px-3 py-2 text-gray-900 focus:ring-2 focus:ring-indigo-500 bg-white text-sm; }
.animate-fade-in { animation: fadeIn 0.3s ease-in-out; }
@keyframes fadeIn { from { opacity: 0; transform: translateY(5px); } to { opacity: 1; transform: translateY(0); } }
</style>