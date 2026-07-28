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
        <button @click="saveProfile" :disabled="!hasProfileChanges" class="btn-primary">
          <SaveIcon class="w-4 h-4 mr-2" /> Profil speichern
        </button>
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
          <label class="block text-sm font-medium text-gray-700">E-Mail Adresse (optional)</label>
          <input v-model="profile.email" type="email" class="input-field" />
        </div>
        <div class="md:col-span-2">
          <label class="block text-sm font-medium text-gray-700">Gruppen</label>
          <input :value="profile.gruppen.join(', ')" type="text" class="input-field" disabled />
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
            <div>
              <h3 class="font-bold text-lg text-gray-800">{{ event.name }}</h3>
              <p class="text-xs text-gray-600">{{ formatDate(event.beginntAm) }} - {{ formatDate(event.endetAm) }}</p>
              <p v-if="event.deadlineTeilnehmer" :class="['text-[10px] font-bold mt-1', isDeadlinePassed(event.deadlineTeilnehmer) ? 'text-red-600' : 'text-orange-600']">
                Deadline für Änderungen: {{ formatDateTime(event.deadlineTeilnehmer) }}
              </p>
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
            <!-- Verfügbarkeiten Section -->
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

            <!-- Prioritäten Section -->
            <button @click="togglePriorities(event.id)" class="w-full flex items-center justify-between p-3 text-sm font-bold text-gray-700 border-t border-gray-200 hover:bg-gray-100 transition">
               <span class="flex items-center gap-2"><StarIcon class="w-4 h-4"/>Vorträge & Prioritäten</span>
               <ChevronDownIcon v-if="activeEventId !== event.id" class="w-5 h-5"/>
               <ChevronUpIcon v-else class="w-5 h-5"/>
            </button>
            <div v-if="activeEventId === event.id" class="p-4 border-t border-gray-200 bg-white animate-fade-in">
              <div v-if="vortraege.length > 0">
                <div class="mb-6">
                  <h4 class="font-bold text-sm mb-2">Alle Vorträge</h4>
                  <div class="space-y-2">
                    <div v-for="group in groupedVortraege" :key="group.berufsfeld">
                      <h5 class="font-semibold text-xs text-indigo-700 uppercase mt-2">{{ group.berufsfeld }}</h5>
                      <ol class="list-decimal list-inside space-y-1 text-xs pl-2">
                        <li v-for="talk in group.vortraege" :key="talk.id">
                          <span class="font-semibold">{{ talk.titel }}</span> bei {{ talk.referentName }}
                          <span v-if="talk.istPflicht" class="ml-2 text-white bg-blue-500 text-[9px] font-bold px-1.5 py-0.5 rounded-full">Pflicht</span>
                        </li>
                      </ol>
                    </div>
                  </div>
                </div>

                <h4 class="font-bold text-sm mb-2">Meine Prioritäten</h4>
                 <div class="flex justify-end mb-4">
                  <button @click="savePriorities()" :disabled="isDeadlinePassed(event.deadlineTeilnehmer) || changedPriorities.size === 0" class="btn-save-all">
                    <SaveAllIcon class="w-3.5 h-3.5"/>
                    Meine Prioritäten speichern
                  </button>
                </div>
                <table class="min-w-full text-xs">
                  <thead class="text-[9px] uppercase font-bold text-gray-500 bg-gray-50">
                    <tr>
                      <th class="py-2 px-4 text-left">Vortrag</th>
                      <th class="py-2 px-4 text-left">Referent</th>
                      <th class="py-2 px-4 text-center">Gewählt</th>
                      <th class="py-2 px-4 text-center">Zugeteilt</th>
                      <th class="py-2 px-4 text-center w-24">Meine Priorität (10=höchste, 1=niedrigste)</th>
                    </tr>
                  </thead>
                  <tbody class="bg-white">
                    <tr v-for="talk in vortraege" :key="talk.id" :class="talk.istPflicht ? 'bg-gray-50' : ''">
                      <td class="px-4 py-3 font-bold">
                        {{ talk.titel }}
                        <span v-if="talk.istPflicht" class="ml-2 text-white bg-blue-500 text-[9px] font-bold px-1.5 py-0.5 rounded-full">Pflicht</span>
                      </td>
                      <td class="px-4 py-3 text-gray-600">{{ talk.referentName }}</td>
                      <td class="px-4 py-3 text-center">
                        <CheckCircleIcon v-if="getPriority(talk.id).prioWert > 0" class="w-4 h-4 text-green-500 mx-auto" />
                      </td>
                      <td class="px-4 py-3 text-center">
                        <CheckCircleIcon v-if="isAssigned(talk.id)" class="w-4 h-4 text-green-500 mx-auto" />
                      </td>
                      <td class="px-4 py-3 text-center">
                        <input type="number" min="0" max="10"
                               v-model.number="getPriority(talk.id).prioWert"
                               @input="markPrioChanged(talk.id)"
                               :disabled="isDeadlinePassed(event.deadlineTeilnehmer) || talk.istPflicht"
                               class="w-20 text-center border rounded py-1 text-sm focus:ring-indigo-500 focus:border-indigo-500 border-gray-300 disabled:bg-gray-200"/>
                      </td>
                    </tr>
                  </tbody>
                </table>
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
import {
  User as UserIcon,
  CalendarCheck as CalendarCheckIcon,
  Printer as PrinterIcon,
  Download as DownloadIcon,
  Save as SaveIcon,
  SaveAll as SaveAllIcon,
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
  version: 0
});
const initialProfile = ref({});

const hasAvailabilityChanges = computed(() => {
  return JSON.stringify(availabilities.value) !== JSON.stringify(initialAvailabilities.value);
});

const hasProfileChanges = computed(() => {
  if (!initialProfile.value) return false;
  return profile.value.email !== initialProfile.value.email;
});

const groupedVortraege = computed(() => {
  const grouped = vortraege.value.reduce((acc, vortrag) => {
    const key = vortrag.berufsfeld || 'Sonstige';
    if (!acc[key]) {
      acc[key] = [];
    }
    acc[key].push(vortrag);
    return acc;
  }, {});

  return Object.keys(grouped).sort().map(key => ({
    berufsfeld: key,
    vortraege: grouped[key]
  }));
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
    initialProfile.value = { ...response.data };
  } catch (error) {
    console.error("Fehler beim Laden des Profils:", error);
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
    alert('Fehler beim Laden der Vorträge & Prioritäten: ' + (error.response?.data?.message || error.message));
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
    alert('Fehler: ' + (error.response?.data?.message || error.message));
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
    await api.post('/api/teilnehmer/prios', payload);
    changedPriorities.value.clear();
    alert('Prioritäten erfolgreich gespeichert!');
  } catch (error) {
    console.error('Fehler beim Speichern der Prioritäten:', error);
    alert('Fehler: ' + (error.response?.data?.message || error.message));
  }
};

const saveProfile = async () => {
  try {
    const response = await api.put('/api/teilnehmer/profile', profile.value);
    profile.value = response.data;
    initialProfile.value = { ...response.data };
    alert('Profil erfolgreich gespeichert!');
  } catch (error) {
    console.error('Fehler beim Speichern des Profils:', error);
    alert('Fehler: ' + (error.response?.data?.message || error.message));
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
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(-10px); }
  to { opacity: 1; transform: translateY(0); }
}
</style>
