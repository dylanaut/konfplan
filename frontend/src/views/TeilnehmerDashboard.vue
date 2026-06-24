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
        <button @click="saveProfile" class="btn-primary">
          <SaveIcon class="w-4 h-4 mr-2" /> Profil speichern
        </button>
      </div>
      <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div>
          <label class="block text-sm font-medium text-gray-700">Vorname</label>
          <input v-model="profile.firstName" type="text" class="input-field" />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700">Nachname</label>
          <input v-model="profile.lastName" type="text" class="input-field" />
        </div>
        <div class="md:col-span-2">
          <label class="block text-sm font-medium text-gray-700">E-Mail Adresse</label>
          <input v-model="profile.email" type="email" class="input-field" />
        </div>
        <div class="md:col-span-2">
          <label class="block text-sm font-medium text-gray-700">Gruppen</label>
          <input :value="profile.gruppen.join(', ')" type="text" class="input-field" disabled />
        </div>
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
        <div v-for="event in events" :key="event.id" class="border border-gray-200 rounded-lg">
          <div class="p-4 flex justify-between items-center">
            <div>
              <h3 class="font-bold text-lg text-gray-800">{{ event.name }} {{event}}</h3>
              <p class="text-xs text-gray-600">{{ formatDate(event.beginntAm) }} - {{ formatDate(event.endetAm) }}</p>
              <p v-if="event.deadlineTeilnehmer" :class="['text-[10px] font-bold mt-1', isDeadlinePassed(event.deadlineTeilnehmer) ? 'text-red-600' : 'text-orange-600']">
                Deadline für Prioritätenwahl: {{ formatDateTime(event.deadlineTeilnehmer) }}
              </p>
            </div>
            <div class="flex gap-2">
              <button v-if="event.planErstellt" @click="viewMySchedule(event.id)" class="btn-secondary">
                <PrinterIcon class="w-4 h-4 mr-2" /> Laufzettel
              </button>
              <button v-if="event.planErstellt" @click="downloadMySchedule(event.id)" class="btn-primary">
                <DownloadIcon class="w-4 h-4 mr-2" /> PDF
              </button>
              <button v-if="!event.planErstellt" @click="togglePriorities(event.id)" class="btn-primary">
                {{ activeEventId === event.id ? 'Einklappen' : 'Prioritäten wählen' }}
              </button>
            </div>
          </div>

          <!-- Prioritäten Sektion (ausklappbar) -->
          <div v-if="activeEventId === event.id" class="p-4 border-t border-gray-200 bg-gray-50/50 animate-fade-in">
            <div v-if="vortraege.length > 0">
              <div class="flex justify-end mb-4">
                <button @click="savePriorities()" :disabled="isDeadlinePassed(event.deadlineTeilnehmer) || changedPriorities.size === 0" class="btn-save-all">
                  <SaveAllIcon class="w-3.5 h-3.5"/>
                  Meine Prioritäten speichern
                </button>
              </div>
              <table class="min-w-full text-xs">
                <thead class="text-[9px] uppercase font-bold text-gray-500">
                  <tr>
                    <th class="py-2 px-4 text-left">Vortrag</th>
                    <th class="py-2 px-4 text-left">Referent</th>
                    <th class="py-2 px-4 text-center w-24">Meine Priorität (1-10)</th>
                  </tr>
                </thead>
                <tbody class="bg-white">
                  <tr v-for="talk in vortraege" :key="talk.id" class="border-b border-gray-100">
                    <td class="px-4 py-3 font-bold">{{ talk.titel }}</td>
                    <td class="px-4 py-3 text-gray-600">{{ talk.referentName }}</td>
                    <td class="px-4 py-3 text-center">
                      <input type="number" min="0" max="10"
                             v-model.number="getPriority(talk.id).prioWert"
                             @input="markPrioChanged(talk.id)"
                             :disabled="isDeadlinePassed(event.deadlineTeilnehmer)"
                             class="w-20 text-center border rounded py-1 text-sm focus:ring-indigo-500 focus:border-indigo-500 border-gray-300"/>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div v-else class="text-center text-gray-500 py-4">
              <p>Für diese Veranstaltung sind noch keine Wahlvorträge verfügbar.</p>
            </div>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import api from '../api/axios';
import { User as UserIcon, CalendarCheck as CalendarCheckIcon, Printer as PrinterIcon, Download as DownloadIcon, Save as SaveIcon, SaveAll as SaveAllIcon } from '@lucide/vue';

const events = ref([]);
const activeEventId = ref(null);
const vortraege = ref([]);
const priorities = ref({});
const changedPriorities = ref(new Set());
const profile = ref({
  id: null,
  firstName: '',
  lastName: '',
  email: '',
  gruppen: [],
  version: 0
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
    const [talksRes, priosRes] = await Promise.all([
      api.get(`/api/veranstaltungen/${eventId}/vortraege`),
      api.get(`/api/teilnehmer/prios?vid=${eventId}`)
    ]);
    vortraege.value = talksRes.data;
    priorities.value = priosRes.data.reduce((acc, prio) => {
      acc[prio.vortrag.id] = prio;
      return acc;
    }, {});
    changedPriorities.value.clear();
    activeEventId.value = eventId;
  } catch (error) {
    console.error("Fehler beim Laden der Prioritäten-Daten:", error);
  }
};

const getPriority = (talkId) => {
  if (!priorities.value[talkId]) {
    priorities.value[talkId] = { vortrag: { id: talkId }, prioWert: 0 };
  }
  return priorities.value[talkId];
};

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
    await api.put('/api/teilnehmer/profile', profile.value);
    alert('Profil erfolgreich gespeichert!');
    await fetchTeilnehmerProfile(); // Refresh data
  } catch (error) {
    console.error('Fehler beim Speichern des Profils:', error);
    alert('Fehler: ' + (error.response?.data?.message || error.message));
  }
};

const viewMySchedule = async (vid) => {
  try {
    const response = await api.get(`/api/teilnehmer/veranstaltungen/${vid}/laufzettel`, { responseType: 'blob' });
    const html = await response.data.text();
    const newWindow = window.open('', '_blank');
    if (newWindow) {
      newWindow.document.write(html);
      newWindow.document.close();
    }
  } catch (error) {
    console.error('Fehler beim Anzeigen des Laufzettels:', error);
  }
};

const downloadMySchedule = async (vid) => {
  try {
    const res = await api.get(`/api/teilnehmer/veranstaltungen/${vid}/laufzettel-pdf`, { responseType: 'blob' });
    const url = window.URL.createObjectURL(new Blob([res.data], { type: 'application/pdf' }));
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', 'mein-laufzettel.pdf');
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  } catch (e) {
    console.error('Fehler beim Download des Laufzettels:', e);
  }
};

const isDeadlinePassed = (deadline) => {
    if (!deadline) return false;
    return new Date(deadline) < new Date();
};

const formatDate = (d) => new Date(d).toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' });
const formatDateTime = (d) => new Date(d).toLocaleString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });
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
