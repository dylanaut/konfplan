<template>
  <div class="max-w-5xl mx-auto space-y-8 pb-20">
    <!-- App Logo -->
    <div class="flex justify-center py-4">
      <img src="/logo/konfplan-light.svg" alt="Konfplan Logo" class="h-16" />
    </div>

    <!-- Sektion: Mein Profil -->
    <section class="bg-white p-6 rounded-xl shadow-sm border border-gray-100">
      <div class="flex items-center justify-between mb-6 text-indigo-600">
        <div class="flex items-center gap-2">
          <UserIcon class="w-6 h-6" />
          <h2 class="text-xl font-bold">Mein Profil</h2>
        </div>
      </div>
      <div v-if="profile.id" class="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div class="md:col-span-2">
          <label class="block text-sm font-medium text-gray-700">Anmeldename (nicht änderbar)</label>
          <input :value="profile.loginName" type="text" class="input-field" disabled />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700">Vorname</label>
          <input v-model="profile.firstName" type="text" class="input-field" disabled />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700">Nachname</label>
          <input v-model="profile.lastName" type="text" class="input-field" disabled />
        </div>
        <div class="md:col-span-2">
          <label class="block text-sm font-medium text-gray-700">E-Mail-Adresse</label>
          <div class="flex items-center gap-2">
            <input :value="profile.email || '(keine hinterlegt)'" type="text" class="input-field flex-1" disabled />
          </div>
          <p class="mt-1 text-xs text-gray-500">
            Änderungen an E-Mail-Adresse und Passwort erfolgen über das Keycloak-Benutzerkonto.
          </p>
        </div>
        <div class="md:col-span-2">
          <label class="block text-sm font-medium text-gray-700">Gruppen</label>
          <input :value="profile.gruppen.join(', ')" type="text" class="input-field" disabled />
        </div>
        <div class="md:col-span-2">
          <label class="block text-sm font-medium text-gray-700 mb-2">Meine Neigungen</label>
          <div class="grid grid-cols-2 md:grid-cols-3 gap-2">
            <div v-for="neigung in neigungStore.neigungen" :key="neigung.name" class="flex items-center gap-2 bg-white p-2 rounded-md border" :title="neigung.beschreibung">
              <input :id="`profile-neigung-${neigung.name}`" type="checkbox" :value="neigung.name" v-model="profile.neigungen" class="h-4 w-4 rounded text-indigo-600 focus:ring-indigo-500 border-gray-300">
              <label :for="`profile-neigung-${neigung.name}`" class="text-sm font-medium text-gray-700">{{ neigung.bezeichnung }}</label>
            </div>
          </div>
          <p class="text-xs text-gray-500 mt-2">Deine Neigungen können bei der Zuordnung zu weiteren Vorträgen berücksichtigt werden.</p>
          <div class="flex justify-end mt-4">
            <button @click="saveNeigungen" class="btn-primary">Speichern</button>
          </div>
        </div>
      </div>
       <div v-else class="text-center text-gray-500 py-8">
        <p>Profildaten werden geladen...</p>
      </div>
    </section>

    <!-- Sektion: Meine Veranstaltungen -->
    <section class="bg-white p-6 rounded-xl shadow-sm border border-gray-100">
      <div class="flex items-center justify-between mb-6 text-indigo-600">
        <div class="flex items-center gap-2">
          <CalendarCheckIcon class="w-6 h-6" />
          <h2 class="text-xl font-bold">Meine Veranstaltungen</h2>
        </div>
      </div>
      <div class="space-y-4">
        <div v-if="events.length === 0" class="text-center text-gray-500 py-8">
          <p>Sie sind für keine Veranstaltungen angemeldet.</p>
        </div>
        <div v-for="event in events" :key="event.id" class="border border-gray-200 rounded-lg overflow-hidden">
          <div class="p-4 flex justify-between items-center bg-gray-50">
            <div class="flex items-center gap-3">
              <a v-if="event.logo && event.logo_link" :href="event.logo_link" target="_blank" rel="noopener noreferrer">
                <EventLogo :src="event.logo" :alt="event.name" img-class="h-10 w-auto object-contain" />
              </a>
              <EventLogo v-else-if="event.logo" :src="event.logo" :alt="event.name" img-class="h-10 w-auto object-contain" />
              <div>
                <h3 class="font-bold text-lg text-gray-800">{{ event.name }}</h3>
                <p class="text-xs text-gray-600">{{ formatDate(event.beginntAm) }} - {{ formatDate(event.endetAm) }}</p>
                <p v-if="event.organisatoren?.length" class="text-xs text-gray-500">
                  <template v-for="(organisator, index) in event.organisatoren" :key="organisator.id">
                    <a :href="mailtoLink(organisator.email, event.name)" class="underline inline-flex items-center gap-1"><MailIcon class="w-3 h-3"/>{{ organisator.name }}</a><span v-if="index < event.organisatoren.length - 1">, </span>
                  </template>
                </p>
                <p v-if="event.deadlineTeilnehmer" :class="['text-[10px] font-bold mt-1', isDeadlinePassed(event.deadlineTeilnehmer) ? 'text-red-600' : 'text-orange-600']">
                  Deadline für Änderungen: {{ formatDateTime(event.deadlineTeilnehmer) }}
                </p>
              </div>
            </div>
             <div class="flex gap-2">
               <button v-if="event.planErstellt" @click="downloadKalender(event.id)" class="btn-secondary">
                 <CalendarPlus class="w-4 h-4 mr-2" /> ICS
               </button>
              <button v-if="event.planErstellt" @click="viewMySchedule(event.id)" class="btn-primary">
                <PrinterIcon class="w-4 h-4 mr-2" /> Laufzettel anzeigen
              </button>
            </div>
          </div>

          <div class="flex flex-col">
            <!-- Verfügbarkeiten Section (nur wenn vom Organisator freigeschaltet) -->
            <template v-if="event.teilnehmerAendernVerfuegbarkeit">
              <button @click="toggleAvailability(event.id)" class="w-full flex items-center justify-between p-3 text-sm font-bold text-gray-700 border-t border-gray-200 hover:bg-gray-100 transition">
                <span class="flex items-center gap-2"><CalendarIcon class="w-4 h-4"/>Meine Verfügbarkeit</span>
                <ChevronDownIcon v-if="activeAvailabilityEventId !== event.id" class="w-5 h-5"/>
                <ChevronUpIcon v-else class="w-5 h-5"/>
              </button>
              <div v-if="activeAvailabilityEventId === event.id" class="p-4 border-t border-gray-200 bg-white animate-fade-in">
                <div class="flex justify-end mb-4">
                  <button @click="saveAvailabilities" :disabled="isDeadlinePassed(event.deadlineTeilnehmer) || !hasAvailabilityChanges" class="btn-save-all">
                    <SaveAllIcon class="w-3.5 h-3.5"/>
                    Verfügbarkeit speichern
                  </button>
                </div>
                <div class="flex space-x-4 overflow-x-auto pb-4">
                  <div v-for="(daySlots, day) in groupedSlots" :key="day" class="bg-gray-50 p-3 rounded-lg">
                    <h4 class="font-bold text-sm mb-3 text-center">{{ day }}</h4>
                    <table class="text-xs">
                      <tbody>
                        <tr>
                          <td v-for="slot in daySlots" :key="slot.id" class="px-2 py-1 font-bold text-center">{{ formatTime(slot.startTime) }}</td>
                        </tr>
                        <tr>
                          <td v-for="slot in daySlots" :key="slot.id"
                              :class="['px-2 py-1 text-center', pflichtSlotIds.has(slot.id) ? 'bg-gray-200 rounded' : '']"
                              :title="pflichtSlotIds.has(slot.id) ? 'Pflichtvortrag der eigenen Gruppe - Teilnahme verpflichtend' : ''">
                            <input type="checkbox" v-model="availabilities[slot.id]"
                                   :disabled="pflichtSlotIds.has(slot.id)"
                                   class="h-4 w-4 text-indigo-600 focus:ring-indigo-500 border-gray-300 rounded disabled:opacity-60 disabled:cursor-not-allowed">
                          </td>
                        </tr>
                      </tbody>
                    </table>
                  </div>
                </div>
              </div>
            </template>

            <!-- Prioritäten Section -->
            <button @click="togglePriorities(event.id)" class="w-full flex items-center justify-between p-3 text-sm font-bold text-gray-700 border-t border-gray-200 hover:bg-gray-100 transition">
               <span class="flex items-center gap-2"><StarIcon class="w-4 h-4"/>Vorträge & Prioritäten</span>
               <ChevronDownIcon v-if="activeEventId !== event.id" class="w-5 h-5"/>
               <ChevronUpIcon v-else class="w-5 h-5"/>
            </button>
            <div v-if="activeEventId === event.id" class="p-4 border-t border-gray-200 bg-white animate-fade-in">
              <div v-if="vortraege.length > 0">
                <div class="mb-6 overflow-x-auto">
                  <table class="text-xs border-collapse">
                    <thead>
                      <tr>
                        <th class="px-2 py-1 text-right sticky left-0 bg-white">Nr.</th>
                        <th class="px-2 py-1 text-left sticky left-0 bg-white">Vortrag</th>
                        <th v-for="neigung in neigungStore.neigungen" :key="neigung.name"
                            class="px-1 py-1 text-center align-bottom cursor-pointer select-none rounded-t"
                            :class="highlightedNeigungen.includes(neigung.name) ? 'bg-indigo-100' : 'hover:bg-gray-100'"
                            :title="`${neigung.beschreibung} (klicken zum Hervorheben, Mehrfachauswahl möglich)`"
                            @click="toggleNeigungHighlight(neigung.name)">
                          <span class="inline-block whitespace-nowrap text-[9px] font-semibold text-indigo-700"
                                style="writing-mode: vertical-rl; transform: rotate(180deg);">
                            {{ neigung.bezeichnung }}
                          </span>
                        </th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr v-for="talk in nummeredVortraege" :key="talk.id"
                          class="border-t border-gray-100"
                          :class="isTalkHighlighted(talk) ? 'bg-yellow-100' : ''">
                        <td class="px-2 py-1 text-right font-black text-indigo-600">{{ talk.nummer }}</td>
                        <td class="px-2 py-1">
                          <span class="font-semibold">{{ talk.titel }}</span> von {{ talk.referentName }}
                          <span v-if="talk.istPflicht" class="ml-1 text-white bg-blue-500 text-[9px] font-bold px-1.5 py-0.5 rounded-full">Pflicht</span>
                        </td>
                        <td v-for="neigung in neigungStore.neigungen" :key="neigung.name" class="px-1 py-1 text-center text-gray-500">
                          {{ (talk.neigungen || []).includes(neigung.name) ? 'X' : '' }}
                        </td>
                      </tr>
                    </tbody>
                  </table>
                </div>

                <h4 class="font-bold text-sm mb-2">Meine Prioritäten</h4>
                <p class="text-sm font-bold text-red-600 mb-2">Nummern entsprechen der Legende oben. 10 = höchste Priorität, 1 = niedrigste, leer/0 = kein Interesse.</p>
                <div class="flex justify-end items-center gap-3 mb-4">
                  <p v-if="event.maxPrioritaeten" class="text-xs font-bold"
                     :class="assignedPrioritaetenCount > event.maxPrioritaeten ? 'text-red-600' : 'text-gray-500'">
                    {{ assignedPrioritaetenCount }} / {{ event.maxPrioritaeten }} Prioritäten vergeben
                  </p>
                  <button @click="savePriorities()"
                          :disabled="isDeadlinePassed(event.deadlineTeilnehmer) || changedPriorities.size === 0 || (event.maxPrioritaeten && assignedPrioritaetenCount > event.maxPrioritaeten)"
                          class="btn-save-all">
                    <SaveAllIcon class="w-3.5 h-3.5"/>
                    Meine Prioritäten speichern
                  </button>
                </div>
                <div v-for="(chunk, chunkIndex) in prioritaetenChunks" :key="chunkIndex" class="mb-4 overflow-x-auto">
                  <table class="text-xs border-collapse">
                    <thead class="text-[9px] uppercase font-bold text-gray-500 bg-gray-50">
                      <tr>
                        <th class="py-2 px-2 text-left sticky left-0 bg-gray-50"></th>
                        <th v-for="talk in chunk" :key="talk.id"
                            class="py-2 px-1 text-center w-10 min-w-[40px] rounded"
                            :class="isTalkHighlighted(talk) ? 'bg-yellow-100' : ''"
                            :title="talk.titel">
                          {{ talk.nummer }}
                        </th>
                      </tr>
                    </thead>
                    <tbody class="bg-white">
                      <tr>
                        <td class="px-2 py-1 text-left font-bold text-gray-600 sticky left-0 bg-white">Gewählt</td>
                        <td v-for="talk in chunk" :key="'gewaehlt-'+talk.id" class="px-1 py-1 text-center">
                          <CheckCircleIcon v-if="getPriority(talk.id).prioWert > 0" class="w-3.5 h-3.5 text-green-500 mx-auto" />
                        </td>
                      </tr>
                      <tr>
                        <td class="px-2 py-1 text-left font-bold text-gray-600 sticky left-0 bg-white">Zugeteilt</td>
                        <td v-for="talk in chunk" :key="'zugeteilt-'+talk.id" class="px-1 py-1 text-center">
                          <CheckCircleIcon v-if="isAssigned(talk.id)" class="w-3.5 h-3.5 text-green-500 mx-auto" />
                        </td>
                      </tr>
                      <tr>
                        <td class="px-2 py-1 text-left font-bold text-gray-600 sticky left-0 bg-white">Priorität</td>
                        <td v-for="talk in chunk" :key="'prio-'+talk.id" class="px-1 py-1 text-center">
                          <input type="number" min="0" max="10"
                                 v-model.number="getPriority(talk.id).prioWert"
                                 @input="markPrioChanged(talk.id)"
                                 :disabled="isDeadlinePassed(event.deadlineTeilnehmer) || talk.istPflicht"
                                 class="prio-input w-10 text-center border rounded py-0.5 text-[10px] focus:ring-indigo-500 focus:border-indigo-500 border-gray-300 disabled:bg-gray-200"/>
                        </td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </div>
              <div v-else class="text-center text-gray-500 py-4">
                <p>Für diese Veranstaltung sind noch keine Vorträge verfügbar.</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue';
import { useRouter } from 'vue-router';
import api from '../api/axios';
import { extractErrorMessage } from '../utils/errorMessage';
import { useNeigungStore } from '../stores/neigung';
import EventLogo from '../components/EventLogo.vue';
import {
  User as UserIcon,
  CalendarCheck as CalendarCheckIcon,
  Printer as PrinterIcon,
  Download as DownloadIcon,
  SaveAll as SaveAllIcon,
  Mail as MailIcon,
  CheckCircleIcon,
  ChevronDownIcon,
  ChevronUpIcon,
  CalendarIcon,
  StarIcon,
  CalendarPlus,
} from '@lucide/vue';

const router = useRouter();
const events = ref([]);
const activeEventId = ref(null);
const activeAvailabilityEventId = ref(null);
const vortraege = ref([]);
const priorities = ref({});
const changedPriorities = ref(new Set());
const availabilities = ref({});
const initialAvailabilities = ref({});
const eventSlots = ref([]);
const schedule = ref([]);
const profile = ref({
  id: null,
  loginName: '',
  firstName: '',
  lastName: '',
  email: '',
  gruppen: [],
  neigungen: [],
  version: 0
});
const neigungStore = useNeigungStore();
neigungStore.fetchNeigungen();

const hasAvailabilityChanges = computed(() => {
  return JSON.stringify(availabilities.value) !== JSON.stringify(initialAvailabilities.value);
});

const sortedVortraege = computed(() => {
  return [...vortraege.value].sort((a, b) => a.titel.localeCompare(b.titel));
});

const nummeredVortraege = computed(() => {
  return sortedVortraege.value.map((v, index) => ({ ...v, nummer: index + 1 }));
});

// Klick auf eine oder mehrere Neigungs-Spaltenüberschriften in der Vortrags-Legende hebt alle
// Vorträge hervor, die mindestens eine der ausgewählten Neigungen adressieren (erneuter Klick auf
// eine bereits ausgewählte Spalte nimmt nur diese wieder aus der Auswahl).
const highlightedNeigungen = ref([]);
const toggleNeigungHighlight = (neigungName) => {
  const index = highlightedNeigungen.value.indexOf(neigungName);
  if (index === -1) {
    highlightedNeigungen.value.push(neigungName);
  } else {
    highlightedNeigungen.value.splice(index, 1);
  }
};
const isTalkHighlighted = (talk) => {
  return highlightedNeigungen.value.length > 0
      && (talk.neigungen || []).some((n) => highlightedNeigungen.value.includes(n));
};

// Bei vielen Vorträgen wird die Prioritäten-Tabelle in mehrere schmalere Tabellen
// untereinander aufgeteilt, damit sie nicht horizontal zu breit wird.
const PRIORITAETEN_SPALTEN_PRO_TABELLE = 15;
const prioritaetenChunks = computed(() => {
  const chunks = [];
  for (let i = 0; i < nummeredVortraege.value.length; i += PRIORITAETEN_SPALTEN_PRO_TABELLE) {
    chunks.push(nummeredVortraege.value.slice(i, i + PRIORITAETEN_SPALTEN_PRO_TABELLE));
  }
  return chunks;
});

const assignedPrioritaetenCount = computed(() => {
  return Object.values(priorities.value).filter(p => p.prioWert > 0).length;
});

const pflichtSlotIds = computed(() => {
  return new Set(
    vortraege.value
      .filter(v => v.istPflicht && v.pflichtSlotId)
      .map(v => v.pflichtSlotId)
  );
});

const groupedSlots = computed(() => {
  return eventSlots.value.reduce((acc, slot) => {
    const day = new Date(slot.startTime).toLocaleDateString('de-DE', { weekday: 'long', year: 'numeric', month: '2-digit', day: '2-digit' });
    if (!acc[day]) {
      acc[day] = [];
    }
    acc[day].push(slot);
    return acc;
  }, {});
});

onMounted(async () => {
  await fetchTeilnehmerProfile();
  await fetchTeilnehmerVeranstaltungen();
});

const fetchTeilnehmerProfile = async () => {
  try {
    const response = await api.get('/api/teilnehmer/profile');
    profile.value = response.data;
  } catch (error) {
    console.error("Fehler beim Laden des Profils:", error);
  }
};

const saveNeigungen = async () => {
  try {
    const response = await api.put('/api/teilnehmer/profile', profile.value);
    profile.value = response.data;
    alert('Neigungen erfolgreich gespeichert!');
  } catch (error) {
    alert('Fehler beim Speichern der Neigungen: ' + extractErrorMessage(error));
  }
};

const fetchTeilnehmerVeranstaltungen = async () => {
  try {
    const response = await api.get('/api/teilnehmer/veranstaltungen');
    events.value = response.data;
  } catch (error) {
    console.error("Fehler beim Laden der Veranstaltungen:", error);
  }
};

const togglePriorities = async (eventId) => {
  if (activeEventId.value === eventId) {
    activeEventId.value = null;
    return;
  }
  try {
    const [talksRes, priosRes, scheduleRes] = await Promise.all([
      api.get(`/api/teilnehmer/veranstaltungen/${eventId}/vortraege`),
      api.get(`/api/prios/${eventId}`),
      api.get(`/api/teilnehmer/veranstaltungen/${eventId}/zuweisungen`)
    ]);
    vortraege.value = talksRes.data;
    priorities.value = Object.entries(priosRes.data || {}).reduce((acc, [vortragId, prioWert]) => {
      acc[vortragId] = { vortrag: { id: Number(vortragId) }, prioWert };
      return acc;
    }, {});
    schedule.value = scheduleRes.data;
    changedPriorities.value.clear();
    activeEventId.value = eventId;
    activeAvailabilityEventId.value = null; // close other section
  } catch (error) {
    console.error("Fehler beim Laden der Prioritäten-Daten:", error);
    alert('Fehler beim Laden der Vorträge & Prioritäten: ' + extractErrorMessage(error));
  }
};

const toggleAvailability = async (eventId) => {
  if (activeAvailabilityEventId.value === eventId) {
    activeAvailabilityEventId.value = null;
    return;
  }
  try {
    const [slotsRes, availabilityRes, vortraegeRes] = await Promise.all([
        api.get(`/api/veranstaltungen/${eventId}/slots`),
        api.get(`/api/teilnehmer/veranstaltungen/${eventId}/verfuegbarkeiten`),
        api.get(`/api/teilnehmer/veranstaltungen/${eventId}/vortraege`)
    ]);
    eventSlots.value = slotsRes.data.sort((a, b) => new Date(a.startTime) - new Date(b.startTime));
    vortraege.value = vortraegeRes.data;
    const availabilityData = availabilityRes.data;
    const currentAvailabilities = eventSlots.value.reduce((acc, slot) => {
        acc[slot.id] = pflichtSlotIds.value.has(slot.id) || availabilityData.verfuegbareSlotIds.includes(slot.id);
        return acc;
    }, {});
    availabilities.value = {...currentAvailabilities};
    initialAvailabilities.value = {...currentAvailabilities};
    activeAvailabilityEventId.value = eventId;
    activeEventId.value = null; // close other section
  } catch (error) {
    console.error("Fehler beim Laden der Verfügbarkeits-Daten:", error);
  }
};

const saveAvailabilities = async () => {
  const verfuegbareSlotIds = Object.entries(availabilities.value)
      .filter(([, isAvailable]) => isAvailable)
      .map(([slotId]) => Number(slotId));

  const payload = {
    nutzerId: profile.value.id,
    veranstaltungId: activeAvailabilityEventId.value,
    verfuegbareSlotIds: verfuegbareSlotIds
  };

  try {
    await api.post(`/api/teilnehmer/veranstaltungen/${activeAvailabilityEventId.value}/verfuegbarkeiten`, payload);
    initialAvailabilities.value = {...availabilities.value};
    alert('Verfügbarkeit erfolgreich gespeichert!');
  } catch (error) {
    console.error('Fehler beim Speichern der Verfügbarkeit:', error);
    alert('Fehler: ' + extractErrorMessage(error));
  }
};

const getPriority = (talkId) => {
  if (!priorities.value[talkId]) {
    priorities.value[talkId] = { vortrag: { id: talkId }, prioWert: 0 };
  }
  return priorities.value[talkId];
};

const isAssigned = (talkId) => {
    return schedule.value.some(zuweisung => zuweisung.vortragTitel === vortraege.value.find(v => v.id === talkId)?.titel);
}

const markPrioChanged = (talkId) => {
  changedPriorities.value.add(talkId);
};

const savePriorities = async () => {
  const payload = Array.from(changedPriorities.value).map(talkId => ({
    vortragId: talkId,
    prioWert: priorities.value[talkId].prioWert
  }));
  try {
    await api.post('/api/prios', payload);
    changedPriorities.value.clear();
    alert('Prioritäten erfolgreich gespeichert!');
  } catch (error) {
    console.error('Fehler beim Speichern der Prioritäten:', error);
    alert('Fehler: ' + extractErrorMessage(error));
  }
};

const viewMySchedule = (vid) => {
  if (profile.value && profile.value.id) {
    router.push({ name: 'LaufzettelTeilnehmer', params: { vid, tid: profile.value.id } });
  }
};

const downloadKalender = async (vid) => {
  try {
    const res = await api.get(`/api/kalender/teilnehmer/${vid}`, { responseType: 'blob' });
    const url = window.URL.createObjectURL(new Blob([res.data], { type: 'text/calendar' }));
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', `KonfPlan_${vid}.ics`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  } catch (e) {
    console.error('Fehler beim Download der ICS-Datei:', e);
  }
};

const isDeadlinePassed = (deadline) => {
    if (!deadline) return false;
    return new Date(deadline) < new Date();
};

const mailtoLink = (email, subject) => `mailto:${email}?subject=${encodeURIComponent(subject)}`;

const formatDate = (d) => new Date(d).toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' });
const formatDateTime = (d) => new Date(d).toLocaleString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });
const formatTime = (d) => new Date(d).toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' });
</script>

<style scoped>
.input-field {
  @apply mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 bg-white p-2 border disabled:bg-gray-100 disabled:text-gray-500;
}
.btn-primary {
  @apply inline-flex items-center px-4 py-2 border border-transparent text-sm font-medium rounded-md shadow-sm text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 disabled:opacity-50;
}
.btn-secondary {
  @apply inline-flex items-center px-4 py-2 border border-gray-300 text-sm font-medium rounded-md text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500;
}
.btn-save-all {
  @apply bg-orange-500 text-white text-sm px-4 py-2 rounded-md shadow-sm transition-all flex items-center gap-2 hover:bg-orange-600 disabled:opacity-50;
}
.animate-fade-in {
  animation: fadeIn 0.3s ease-in-out;
}
/* Die schmale Prioritäts-Zelle hat keinen Platz für die nativen Spinner-Pfeile - ohne
   dieses Ausblenden verdrängen sie die eingegebene Zahl vollständig aus der Zelle. */
.prio-input::-webkit-outer-spin-button,
.prio-input::-webkit-inner-spin-button {
  -webkit-appearance: none;
  margin: 0;
}
.prio-input[type=number] {
  -moz-appearance: textfield;
}
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(-10px); }
  to { opacity: 1; transform: translateY(0); }
}
</style>
