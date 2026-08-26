<template>
  <div class="max-w-5xl mx-auto space-y-8 pb-20">
    <!-- App Logo -->
    <div class="flex justify-center py-4">
      <img src="/logo/konfplan-light.svg" alt="Konfplan Logo" class="h-16" />
    </div>

    <!-- Sektion 1: Profil & Organisation -->
    <section class="bg-white p-6 rounded-xl shadow-sm border border-gray-100">
      <div class="flex items-center justify-between mb-6 text-indigo-600">
        <div class="flex items-center gap-2">
          <UserIcon class="w-6 h-6" />
          <h2 class="text-xl font-bold">Persönliches Profil</h2>
        </div>
        <button v-if="isProfileDirty" @click="saveProfile" class="btn-primary-sm">
          <SaveIcon class="w-4 h-4 mr-1" /> Speichern
        </button>
      </div>
      <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div class="md:col-span-2">
          <label class="block text-sm font-medium text-gray-700">Anmeldename (nicht änderbar)</label>
          <input :value="referent.loginName" type="text" class="input-field" disabled />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700">Vorname</label>
          <input v-model="referent.firstName" type="text" class="input-field" :disabled="isAnyDeadlinePassed" />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700">Nachname</label>
          <input v-model="referent.lastName" type="text" class="input-field" :disabled="isAnyDeadlinePassed" />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700">Organisation</label>
          <input v-model="referent.organisation" type="text" class="input-field" :disabled="isAnyDeadlinePassed" />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700">Rolle / Position</label>
          <input v-model="referent.jobRole" type="text" class="input-field" :disabled="isAnyDeadlinePassed" />
        </div>
        <div class="md:col-span-2">
          <label class="block text-sm font-medium text-gray-700">E-Mail Adresse (optional)</label>
          <input v-model="referent.email" type="email" class="input-field" :disabled="isAnyDeadlinePassed" />
        </div>
      </div>
    </section>

    <!-- NEUE Sektion: Mein Zeitplan -->
    <section v-if="events.length > 0" class="bg-white p-6 rounded-xl shadow-sm border border-gray-100">
      <div class="flex items-center justify-between mb-6 text-indigo-600">
        <div class="flex items-center gap-2">
          <CalendarCheckIcon class="w-6 h-6" />
          <h2 class="text-xl font-bold">Mein Zeitplan</h2>
        </div>
      </div>
      <div class="space-y-4">
        <div v-for="event in events" :key="'schedule-' + event.id" class="border border-gray-200 rounded-lg p-4 flex justify-between items-center">
          <div>
            <h3 class="font-bold text-lg text-gray-800">{{ event.name }}</h3>
            <p class="text-xs text-gray-600">{{ formatDate(event.beginntAm) }} - {{ formatDate(event.endetAm) }}</p>
          </div>
          <div v-if="event.planErstellt" class="flex gap-2">
            <button @click="downloadIcs(event.id)" class="btn-secondary">
              <CalendarPlus class="w-4 h-4 mr-2" /> ICS
            </button>
            <button @click="viewMySchedule(event.id)" class="btn-primary">
              <PrinterIcon class="w-4 h-4 mr-2" /> Laufzettel anzeigen
            </button>
          </div>
          <div v-else>
            <span class="text-xs text-gray-400 italic">Plan noch nicht verfügbar</span>
          </div>
        </div>
      </div>
    </section>

    <!-- NEUE Sektion: Meine Verfügbarkeit (pro Veranstaltung) -->
    <section v-if="events.length > 0" class="bg-white p-6 rounded-xl shadow-sm border border-gray-100">
      <div class="flex items-center gap-2 mb-6 text-indigo-600">
        <CalendarIcon class="w-6 h-6" />
        <h2 class="text-xl font-bold">Meine Verfügbarkeit pro Veranstaltung</h2>
      </div>

      <div class="space-y-6">
        <div v-for="event in events" :key="event.id" class="border border-gray-200 rounded-lg p-4">
          <div class="flex justify-between items-start mb-4">
             <div>
               <h3 class="font-bold text-lg text-gray-800">{{ event.name }}</h3>
               <p class="text-xs text-gray-600">{{ formatDate(event.beginntAm) }} - {{ formatDate(event.endetAm) }}</p>
               <p v-if="event.deadlineReferenten" :class="['text-[10px] font-bold mt-1', isDeadlinePassed(event.deadlineReferenten) ? 'text-red-600' : 'text-orange-600']">
                  Deadline für Referenten: {{ formatDateTime(event.deadlineReferenten) }}
               </p>
             </div>
          </div>

          <!-- Slots für diese Veranstaltung -->
          <div v-if="getSlotsForEvent(event.id).length === 0" class="text-xs text-gray-500 italic">
             Noch keine Zeit-Slots für diese Veranstaltung angelegt.
          </div>
          <div v-else class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-2">
             <button
                v-for="slot in getSlotsForEvent(event.id)" :key="slot.id"
                @click="toggleEventAvailability(event, slot.id)"
                :disabled="isDeadlinePassed(event.deadlineReferenten)"
                :class="['p-2 rounded-lg border text-[10px] transition-all text-center',
                       isUserAvailable(event.id, slot.id) ? 'bg-indigo-600 text-white border-indigo-600 font-bold' : 'bg-gray-50 text-gray-400 border-gray-200',
                       isDeadlinePassed(event.deadlineReferenten) ? 'opacity-80 cursor-not-allowed' : 'hover:border-indigo-400']"
             >
                {{ formatSlotTime(slot.startTime, slot.endTime) }}
             </button>
          </div>
        </div>
      </div>
    </section>

    <!-- Sektion: Meine Vorträge -->
    <section class="bg-white p-6 rounded-xl shadow-sm border border-gray-100">
      <div class="flex items-center justify-between mb-6 text-indigo-600">
        <div class="flex items-center gap-2">
          <FileTextIcon class="w-6 h-6" />
          <h2 class="text-xl font-bold">Meine Vorträge</h2>
        </div>
        <button v-if="!isAnyDeadlinePassed && vortraege.length > 0" @click="addNewTalk" class="btn-primary">
          <PlusIcon class="w-5 h-5 mr-1" /> Neuer Vortrag
        </button>
      </div>

      <div v-if="vortraege.length === 0 && !isEditingNewTalk" class="text-center text-gray-500 py-8">
        <p>Sie haben noch keine Vorträge angelegt.</p>
        <button v-if="!isAnyDeadlinePassed" @click="addNewTalk" class="mt-4 btn-primary">
          <PlusIcon class="w-5 h-5 mr-1" /> Jetzt einen Vortrag hinzufügen
        </button>
      </div>

      <div v-else class="space-y-4">
        <div v-for="t in vortraege" :key="t.id"
             :class="['flex items-center justify-between p-4 border rounded-lg',
                      selectedTalk && selectedTalk.id === t.id ? 'bg-indigo-50 border-indigo-300' : 'bg-gray-50 border-gray-200']">
          <div class="flex flex-col">
            <span class="font-medium text-gray-800">{{ t.titel || 'Unbenannter Vortrag' }}</span>
            <div class="flex flex-col gap-1">
              <span class="text-xs text-gray-500">{{ t.veranstaltungName }}</span>
              <span v-if="getDeadlineForTalk(t)" :class="['text-[10px] font-bold', isDeadlinePassed(getDeadlineForTalk(t)) ? 'text-red-600' : 'text-orange-600']">
                 Deadline: {{ formatDateTime(getDeadlineForTalk(t)) }}
              </span>
            </div>
          </div>
          <div class="space-x-2">
            <button @click="selectTalk(t)" class="btn-secondary-sm">
              <EditIcon class="w-4 h-4" /> {{ isDeadlinePassedForTalk(t) ? 'Ansehen' : 'Bearbeiten' }}
            </button>
            <button v-if="!isDeadlinePassedForTalk(t)" @click="deleteTalk(t)" class="btn-danger-sm">
              <Trash2Icon class="w-4 h-4" /> Löschen
            </button>
          </div>
        </div>
      </div>
    </section>

    <!-- Sektion: Veranstaltungsanmeldungen -->
    <section class="bg-white p-6 rounded-xl shadow-sm border border-gray-100">
      <div class="flex items-center gap-2 mb-6 text-indigo-600">
        <ListChecksIcon class="w-6 h-6" />
        <h2 class="text-xl font-bold">Meine Veranstaltungsanmeldungen</h2>
      </div>

      <div v-if="events.length === 0" class="text-center text-gray-500 py-8">
        <p>Es sind keine Veranstaltungen verfügbar oder Sie haben sich noch nicht angemeldet.</p>
      </div>

      <div v-else class="space-y-6">
        <div v-for="event in events" :key="event.id" class="border border-gray-200 rounded-lg p-4">
          <div class="flex justify-between items-start mb-4">
             <div class="flex items-center gap-3">
               <a v-if="event.logo && event.logo_link" :href="event.logo_link" target="_blank" rel="noopener noreferrer">
                 <EventLogo :src="event.logo" :alt="event.name" img-class="h-16 w-auto object-contain" />
               </a>
               <EventLogo v-else-if="event.logo" :src="event.logo" :alt="event.name" img-class="h-16 w-auto object-contain" />
               <div>
                 <h3 class="font-bold text-lg text-gray-800">{{ event.name }}</h3>
                 <p class="text-sm text-gray-600">{{ formatDate(event.beginntAm) }} - {{ formatDate(event.endetAm) }}</p>
                 <p v-if="event.organisatoren?.length" class="text-xs text-gray-500">
                   <template v-for="(organisator, index) in event.organisatoren" :key="organisator.id">
                     <a :href="mailtoLink(organisator.email, event.name)" class="underline inline-flex items-center gap-1"><MailIcon class="w-3 h-3"/>{{ organisator.name }}</a><span v-if="index < event.organisatoren.length - 1">, </span>
                   </template>
                 </p>
                 <p v-if="event.deadlineReferenten" :class="['text-[10px] font-bold mt-1', isDeadlinePassed(event.deadlineReferenten) ? 'text-red-600' : 'text-orange-600']">
                    Deadline für Referenten: {{ formatDateTime(event.deadlineReferenten) }}
                 </p>
               </div>
             </div>
             <span v-if="isDeadlinePassed(event.deadlineReferenten)" class="text-xs bg-red-100 text-red-700 px-2 py-1 rounded">Abgelaufen</span>
             <span v-else-if="!isFutureEvent(event)" class="text-xs bg-gray-100 text-gray-500 px-2 py-1 rounded">Vergangen</span>
             <span v-else class="text-xs bg-green-100 text-green-700 px-2 py-1 rounded">Offen</span>
          </div>

          <div class="space-y-3">
            <!-- Schon angemeldete Vorträge -->
            <div v-for="talkItem in vortraege.filter(t => t.veranstaltungId === event.id)" :key="talkItem.id"
                 class="flex items-center justify-between p-3 bg-indigo-50 border border-indigo-100 rounded-md">
              <span class="text-sm font-medium text-indigo-900">{{ talkItem.titel }}</span>
              <button v-if="!isDeadlinePassed(event.deadlineReferenten) && isFutureEvent(event)" @click="deregisterTalkFromEvent(event.id, talkItem.id)" class="btn-danger-sm">
                <XIcon class="w-4 h-4 mr-1" /> Zurückziehen
              </button>
            </div>

            <!-- Klon-Optionen (Vorträge aus anderen Veranstaltungen) -->
            <template v-if="!isDeadlinePassed(event.deadlineReferenten) && isFutureEvent(event)">
               <div v-for="talkItem in getTalksFromOtherEvents(event.id)" :key="'other-'+talkItem.id"
                   class="flex items-center justify-between p-3 bg-gray-50 rounded-md opacity-75">
                <span class="text-sm text-gray-600 italic">{{ talkItem.titel }} (von {{ talkItem.veranstaltungName }})</span>
                <button @click="registerTalkForEvent(event.id, talkItem.id)" class="btn-primary-sm">
                  <PlusIcon class="w-4 h-4 mr-1" /> In dieses Event kopieren
                </button>
              </div>
            </template>

            <div v-if="vortraege.length === 0" class="text-gray-500 text-sm italic">
               Sie haben noch keine Vorträge angelegt.
            </div>
          </div>
        </div>
      </div>
    </section>

    <ReferentVortragEditorModal :is-visible="showTalkModal" :talk="selectedTalk" :events="events"
                                @close="closeTalkModal" @save="handleSaveTalk" />
  </div>
</template>

<script setup>
import { ref, onMounted, computed, reactive } from 'vue';
import { useRouter } from 'vue-router';
import api from '../api/axios';
import { extractErrorMessage } from '../utils/errorMessage';
import { useAuthStore } from '../stores/auth';
import { useNeigungStore } from '../stores/neigung';
import ReferentVortragEditorModal from '../components/ReferentVortragEditorModal.vue';
import EventLogo from '../components/EventLogo.vue';
import { User as UserIcon, FileText as FileTextIcon, Calendar as CalendarIcon, Save as SaveIcon, Plus as PlusIcon, Edit as EditIcon, Trash2 as Trash2Icon, ListChecks as ListChecksIcon, Check as CheckIcon, X as XIcon, CalendarCheck as CalendarCheckIcon, Printer as PrinterIcon, Download as DownloadIcon, CalendarPlus, Mail as MailIcon } from '@lucide/vue';

const router = useRouter();
const authStore = useAuthStore();
const neigungStore = useNeigungStore();
neigungStore.fetchNeigungen();

const referent = ref({
  id: null,
  loginName: '',
  firstName: '',
  lastName: '',
  organisation: '',
  jobRole: '',
  email: '',
});
const originalReferent = ref(null);
const allSlots = ref([]);
const vortraege = ref([]);
const selectedTalk = ref(null);
const isEditingNewTalk = ref(false);
const showTalkModal = ref(false);
const events = ref([]);

// Neue State für Verfügbarkeiten pro Event
const eventverfuegIds = reactive({}); // Key: eventId, Value: Array von slotIds

onMounted(async () => {
  await fetchReferentData();
  await fetchAllSlots();
  await fetchReferentenVortraege();
  await fetchEventsForRegistration();
});

const fetchReferentData = async () => {
  try {
    const userRes = await api.get('/api/referenten/profile');
    referent.value = userRes.data;
    originalReferent.value = { ...userRes.data };
  } catch (error) {
    console.error("Fehler beim Laden des Referentenprofils:", error);
  }
};

const isProfileDirty = computed(() => {
  return originalReferent.value && JSON.stringify(referent.value) !== JSON.stringify(originalReferent.value);
});

const saveProfile = async () => {
  try {
    await api.put('/api/referenten/profile', referent.value);
    originalReferent.value = { ...referent.value };
    alert("Profil gespeichert!");
  } catch (e) {
    console.error("Fehler beim Speichern des Profils:", e);
    alert("Fehler beim Speichern: " + extractErrorMessage(e));
  }
};

const fetchAllSlots = async () => {
  try {
    const slotRes = await api.get('/api/slots');
    allSlots.value = slotRes.data;
  } catch (error) {
    console.error("Fehler beim Laden der Slots:", error);
  }
};

const fetchReferentenVortraege = async () => {
  try {
    const talksRes = await api.get('/api/referenten/vortraege');
    vortraege.value = talksRes.data;
  } catch (error) {
    console.error("Fehler beim Laden der Vorträge:", error);
  }
};

const fetchEventsForRegistration = async () => {
  try {
    const eventsRes = await api.get('/api/referenten/veranstaltungen');
    events.value = eventsRes.data;

    // Verfügbarkeiten für alle Events laden
    for (const event of events.value) {
       await fetchverfuegIdsForEvent(event.id);
    }
  } catch (error) {
    console.error("Fehler beim Laden der Veranstaltungen:", error);
  }
};

const fetchverfuegIdsForEvent = async (vid) => {
   try {
      const res = await api.get(`/api/referenten/veranstaltungen/${vid}/verfuegbarkeiten`);
      eventverfuegIds[vid] = res.data.verfuegbareSlotIds ?? [];
   } catch (e) {
      console.error(`Fehler beim Laden der Verfügbarkeiten für Event ${vid}:`, e);
   }
};

const getSlotsForEvent = (eventId) => {
   return allSlots.value.filter(s => s.veranstaltungId === eventId);
};

const isUserAvailable = (eventId, slotId) => {
   return eventverfuegIds[eventId] && eventverfuegIds[eventId].includes(slotId);
};

const toggleEventAvailability = async (event, slotId) => {
   if (isDeadlinePassed(event.deadlineReferenten)) return;

   const current = isUserAvailable(event.id, slotId);
   const currentSlotIds = eventverfuegIds[event.id] || [];
   const updatedSlotIds = current
      ? currentSlotIds.filter(id => id !== slotId)
      : [...currentSlotIds, slotId];

   try {
      await api.post(`/api/referenten/veranstaltungen/${event.id}/verfuegbarkeiten`, {
         nutzerId: referent.value.id,
         veranstaltungId: event.id,
         verfuegbareSlotIds: updatedSlotIds
      });

      eventverfuegIds[event.id] = updatedSlotIds;
   } catch (e) {
      alert("Fehler beim Aktualisieren der Verfügbarkeit: " + (e.response?.data || e.message));
   }
};

const selectTalk = (talk) => {
  selectedTalk.value = { ...talk };
  isEditingNewTalk.value = false;
  showTalkModal.value = true;
};

const addNewTalk = () => {
  // Find the first event where deadline is not passed
  const availableEvent = events.value.find(e => !isDeadlinePassed(e.deadlineReferenten));
  if (!availableEvent) {
      alert("Für alle verfügbaren Veranstaltungen ist die Deadline bereits abgelaufen.");
      return;
  }
  selectedTalk.value = {
    titel: '',
    inhalt: '',
    ausstattung: '',
    wiederholbar: false,
    neigungen: [],
    verfuegIds: [],
    veranstaltungId: availableEvent.id
  };
  isEditingNewTalk.value = true;
  showTalkModal.value = true;
};

const closeTalkModal = () => {
  showTalkModal.value = false;
  selectedTalk.value = null;
  isEditingNewTalk.value = false;
};

const handleSaveTalk = async (talk) => {
  try {
    if (isEditingNewTalk.value) {
      await api.post('/api/referenten/vortraege', talk);
    } else {
      await api.put(`/api/referenten/vortraege/${talk.id}`, talk);
    }
    showTalkModal.value = false;
    selectedTalk.value = null;
    isEditingNewTalk.value = false;
    await fetchReferentenVortraege();
    await fetchEventsForRegistration();
  } catch (e) {
    console.error("Fehler beim Speichern des Vortrags:", e);
    alert("Fehler beim Speichern: " + extractErrorMessage(e));
  }
};

const deleteTalk = async (talk) => {
  if (isDeadlinePassedForTalk(talk)) {
      alert("Die Deadline für diesen Vortrag ist bereits abgelaufen.");
      return;
  }
  const event = events.value.find(e => e.id === talk.veranstaltungId);
  if (event && !isFutureEvent(event)) {
      alert("Vorträge für vergangene Veranstaltungen können nicht gelöscht werden.");
      return;
  }
  if (!confirm('Sind Sie sicher?')) return;
  try {
    await api.delete(`/api/referenten/vortraege/${talk.id}`);
    await fetchReferentenVortraege();
    await fetchEventsForRegistration();
  } catch (e) {
    console.error("Fehler beim Löschen:", e);
  }
};

const isFutureEvent = (event) => {
    if (!event || !event.beginntAm) return false;
    return new Date(event.beginntAm) > new Date();
};

const isDeadlinePassed = (deadline) => {
    if (!deadline) return false;
    return new Date(deadline) < new Date();
};

const mailtoLink = (email, subject) => `mailto:${email}?subject=${encodeURIComponent(subject)}`;

const isAnyDeadlinePassed = computed(() => {
    return events.value.some(e => isDeadlinePassed(e.deadlineReferenten));
});

const getDeadlineForTalk = (talk) => {
    const event = events.value.find(e => e.id === talk.veranstaltungId);
    return event ? event.deadlineReferenten : null;
};

const isDeadlinePassedForTalk = (talk) => {
    if (!talk) return false;
    return isDeadlinePassed(getDeadlineForTalk(talk));
};

const isDeadlinePassedForEventId = (eventId) => {
    const event = events.value.find(e => e.id === eventId);
    return event ? isDeadlinePassed(event.deadlineReferenten) : false;
};

const getTalksFromOtherEvents = (targetEventId) => {
    const existingTitles = vortraege.value
        .filter(t => t.veranstaltungId === targetEventId)
        .map(t => t.titel);

    const otherTalks = vortraege.value.filter(t => t.veranstaltungId !== targetEventId);
    const uniqueOther = [];
    const seenTitles = new Set();

    for (const t of otherTalks) {
        if (!existingTitles.includes(t.titel) && !seenTitles.has(t.titel)) {
            uniqueOther.push(t);
            seenTitles.add(t.titel);
        }
    }
    return uniqueOther;
};

const registerTalkForEvent = async (eventId, talkId) => {
  if (isDeadlinePassedForEventId(eventId)) {
      alert("Die Deadline für diese Veranstaltung ist bereits abgelaufen.");
      return;
  }
  try {
    await api.post(`/api/referenten/veranstaltungen/${eventId}/vortraege/${talkId}/register`);
    await fetchReferentenVortraege();
    await fetchEventsForRegistration();
    alert("Vortrag wurde erfolgreich kopiert.");
  } catch (e) {
    console.error("Fehler beim Anmelden:", e);
  }
};

const deregisterTalkFromEvent = async (eventId, talkId) => {
  if (isDeadlinePassedForEventId(eventId)) {
      alert("Die Deadline für diese Veranstaltung ist bereits abgelaufen.");
      return;
  }
  if (!confirm('Abmelden? Dies zieht den Vortrag für diese Veranstaltung zurück.')) return;
  try {
    await api.delete(`/api/referenten/veranstaltungen/${eventId}/vortraege/${talkId}/deregister`);
    await fetchReferentenVortraege();
    await fetchEventsForRegistration();
  } catch (e) {
    console.error("Fehler beim Abmelden:", e);
  }
};

const viewMySchedule = (vid) => {
  if (referent.value && referent.value.id) {
    router.push({ name: 'LaufzettelReferent', params: { vid, rid: referent.value.id } });
  }
};

const downloadIcs = async (vid) => {
  try {
    const res = await api.get(`/api/kalender/referent/${vid}`, { responseType: 'blob' });
    const url = window.URL.createObjectURL(new Blob([res.data], { type: 'text/calendar' }));
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', `meine_vortraege_${vid}.ics`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  } catch (e) {
    console.error('Fehler beim Download der ICS-Datei:', e);
  }
};

const formatDate = (d) => new Date(d).toLocaleDateString('de-DE', { weekday: 'long', day: '2-digit', month: '2-digit' });
const formatDateTime = (d) => new Date(d).toLocaleString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });
const formatTime = (t) => t.substring(11, 16);

const formatSlotTime = (start, end) => {
  const startDate = new Date(start);
  const endDate = new Date(end);
  const weekdays = ['So', 'Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa'];
  const day = weekdays[startDate.getDay()];
  const startTime = startDate.toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' });
  const endTime = endDate.toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' });
  return `${day} ${startTime} - ${endTime}`;
};
</script>

<style scoped>
.input-field {
  @apply mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 bg-gray-50 p-2 border disabled:bg-gray-100 disabled:text-gray-500;
}
.btn-primary {
  @apply inline-flex items-center px-4 py-2 border border-transparent text-sm font-medium rounded-md shadow-sm text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 disabled:opacity-50;
}
.btn-secondary {
  @apply inline-flex items-center px-4 py-2 border border-gray-300 text-sm font-medium rounded-md text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500;
}
.btn-primary-sm {
  @apply inline-flex items-center px-3 py-1 border border-transparent text-xs font-medium rounded-md shadow-sm text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500;
}
.btn-secondary-sm {
  @apply inline-flex items-center px-3 py-1 border border-gray-300 text-xs font-medium rounded-md text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500;
}
.btn-danger-sm {
  @apply inline-flex items-center px-3 py-1 border border-transparent text-xs font-medium rounded-md text-white bg-red-600 hover:bg-red-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-red-500;
}
</style>
