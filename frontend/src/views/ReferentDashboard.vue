<template>
  <div class="max-w-5xl mx-auto space-y-8 pb-20">

    <!-- Sektion 1: Profil & Organisation -->
    <section class="bg-white p-6 rounded-xl shadow-sm border border-gray-100">
      <div class="flex items-center gap-2 mb-6 text-indigo-600">
        <UserIcon class="w-6 h-6" />
        <h2 class="text-xl font-bold">Persönliches Profil</h2>
      </div>
      <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div>
          <label class="block text-sm font-medium text-gray-700">Organisation</label>
          <input v-model="referent.organisation" type="text" class="input-field" />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700">Rolle / Position</label>
          <input v-model="referent.jobRole" type="text" class="input-field" />
        </div>
        <div class="md:col-span-2">
          <label class="block text-sm font-medium text-gray-700">E-Mail Adresse</label>
          <input v-model="referent.email" type="email" class="input-field" />
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
        <button @click="addNewTalk" class="btn-primary">
          <PlusIcon class="w-5 h-5 mr-1" /> Neuer Vortrag
        </button>
      </div>

      <div v-if="vortraege.length === 0 && !isEditingNewTalk" class="text-center text-gray-500 py-8">
        <p>Sie haben noch keine Vorträge angelegt.</p>
        <button @click="addNewTalk" class="mt-4 btn-primary">
          <PlusIcon class="w-5 h-5 mr-1" /> Jetzt einen Vortrag hinzufügen
        </button>
      </div>

      <div v-else class="space-y-4">
        <div v-for="t in vortraege" :key="t.id"
             :class="['flex items-center justify-between p-4 border rounded-lg',
                      selectedTalk && selectedTalk.id === t.id ? 'bg-indigo-50 border-indigo-300' : 'bg-gray-50 border-gray-200']">
          <div class="flex flex-col">
            <span class="font-medium text-gray-800">{{ t.title || 'Unbenannter Vortrag' }}</span>
            <span class="text-xs text-gray-500">{{ t.veranstaltungName }}</span>
          </div>
          <div class="space-x-2">
            <button @click="selectTalk(t)" class="btn-secondary-sm">
              <EditIcon class="w-4 h-4" /> Bearbeiten
            </button>
            <button @click="deleteTalk(t)" class="btn-danger-sm">
              <Trash2Icon class="w-4 h-4" /> Löschen
            </button>
          </div>
        </div>
      </div>
    </section>

    <!-- Sektion: Vortragsdetails -->
    <section v-if="selectedTalk || isEditingNewTalk" class="bg-white p-6 rounded-xl shadow-sm border border-gray-100">
      <div class="flex items-center gap-2 mb-6 text-indigo-600">
        <FileTextIcon class="w-6 h-6" />
        <h2 class="text-xl font-bold">{{ isEditingNewTalk ? 'Neuen Vortrag anlegen' : 'Vortragsdetails bearbeiten' }}</h2>
      </div>
      <div class="space-y-4">
        <div>
          <label class="block text-sm font-medium text-gray-700">Titel des Vortrags</label>
          <input v-model="selectedTalk.title" type="text" class="input-field" />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700">Abstract (Kurzbeschreibung)</label>
          <textarea v-model="selectedTalk.abstractText" rows="4" class="input-field"></textarea>
        </div>

        <div class="flex items-center gap-4 p-4 bg-indigo-50 rounded-lg">
          <input v-model="selectedTalk.wiederholbar" type="checkbox" class="w-5 h-5 text-indigo-600" id="repeat" />
          <label for="repeat" class="text-sm font-medium text-indigo-900">
            Ich bin bereit, den Vortrag bei hoher Nachfrage mehrfach zu halten.
          </label>
        </div>
      </div>
    </section>

    <!-- Sektion: Verfügbarkeit -->
    <section v-if="selectedTalk || isEditingNewTalk" class="bg-white p-6 rounded-xl shadow-sm border border-gray-100">
      <div class="flex items-center gap-2 mb-6 text-indigo-600">
        <CalendarIcon class="w-6 h-6" />
        <h2 class="text-xl font-bold">Meine Verfügbarkeit für diesen Vortrag</h2>
      </div>

      <div v-if="relevantSlotsForTalk.length === 0" class="text-gray-500 py-4 italic">
        Für die Veranstaltung dieses Vortrags sind noch keine Zeit-Slots angelegt.
      </div>

      <div v-for="(slots, date) in groupedSlots" :key="date" class="mb-8 last:mb-0">
        <div class="flex justify-between items-center mb-4 border-b pb-2">
          <h3 class="font-bold text-gray-800">{{ formatDate(date) }}</h3>
          <div class="space-x-2">
            <button @click="toggleDay(date, true)" class="text-xs bg-green-100 text-green-700 px-2 py-1 rounded">Alle an</button>
            <button @click="toggleDay(date, false)" class="text-xs bg-red-100 text-red-700 px-2 py-1 rounded">Alle aus</button>
          </div>
        </div>

        <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-3">
          <button
              v-for="slot in slots" :key="slot.id"
              @click="toggleSlot(slot.id)"
              :class="['p-3 rounded-lg border text-sm transition-all text-center',
                     isAvailable(slot.id) ? 'bg-indigo-600 text-white border-indigo-600' : 'bg-gray-50 text-gray-400 border-gray-200']"
          >
            {{ formatTime(slot.startTime) }} - {{ formatTime(slot.endTime) }}
          </button>
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
             <div>
               <h3 class="font-bold text-lg text-gray-800">{{ event.name }}</h3>
               <p class="text-sm text-gray-600">{{ formatDate(event.beginntAm) }} - {{ formatDate(event.endetAm) }}</p>
             </div>
             <span v-if="!isFutureEvent(event)" class="text-xs bg-gray-100 text-gray-500 px-2 py-1 rounded">Vergangen</span>
             <span v-else class="text-xs bg-green-100 text-green-700 px-2 py-1 rounded">Zukünftig</span>
          </div>

          <div class="space-y-3">
            <!-- Schon angemeldete Vorträge -->
            <div v-for="talkItem in vortraege.filter(t => t.veranstaltungId === event.id)" :key="talkItem.id"
                 class="flex items-center justify-between p-3 bg-indigo-50 border border-indigo-100 rounded-md">
              <span class="text-sm font-medium text-indigo-900">{{ talkItem.title }}</span>
              <button v-if="isFutureEvent(event)" @click="deregisterTalkFromEvent(event.id, talkItem.id)" class="btn-danger-sm">
                <XIcon class="w-4 h-4 mr-1" /> Zurückziehen
              </button>
            </div>

            <!-- Klon-Optionen (Vorträge aus anderen Veranstaltungen) -->
            <template v-if="isFutureEvent(event)">
               <div v-for="talkItem in getTalksFromOtherEvents(event.id)" :key="'other-'+talkItem.id"
                   class="flex items-center justify-between p-3 bg-gray-50 rounded-md opacity-75">
                <span class="text-sm text-gray-600 italic">{{ talkItem.title }} (von {{ talkItem.veranstaltungName }})</span>
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

    <!-- Global Save Button -->
    <div class="fixed bottom-6 right-6">
      <button @click="saveAll" class="bg-indigo-600 hover:bg-indigo-700 text-white px-10 py-4 rounded-full shadow-2xl font-bold flex items-center gap-2">
        <SaveIcon /> Alles speichern
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue';
import api from '../api/axios';
import { User as UserIcon, FileText as FileTextIcon, Calendar as CalendarIcon, Save as SaveIcon, Plus as PlusIcon, Edit as EditIcon, Trash2 as Trash2Icon, ListChecks as ListChecksIcon, Check as CheckIcon, X as XIcon } from 'lucide-vue-next';

const referent = ref({});
const allSlots = ref([]);
const vortraege = ref([]);
const selectedTalk = ref(null);
const isEditingNewTalk = ref(false);
const events = ref([]);

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
  } catch (error) {
    console.error("Fehler beim Laden des Referentenprofils:", error);
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
    if (vortraege.value.length > 0 && !selectedTalk.value) {
      selectedTalk.value = { ...vortraege.value[0] };
    } else if (vortraege.value.length === 0) {
      selectedTalk.value = null;
    }
    isEditingNewTalk.value = false;
  } catch (error) {
    console.error("Fehler beim Laden der Vorträge:", error);
  }
};

const fetchEventsForRegistration = async () => {
  try {
    const eventsRes = await api.get('/api/referenten/events-for-registration');
    events.value = eventsRes.data;
  } catch (error) {
    console.error("Fehler beim Laden der Veranstaltungen:", error);
  }
};

const relevantSlotsForTalk = computed(() => {
  if (!selectedTalk.value) return [];
  // Wenn wir alle Slots haben, filtern wir die, die zur Veranstaltung des ausgewählten Vortrags gehören
  // Da die API /api/slots ggf. alle zurückgibt, müssen wir wissen, welche zur Veranstaltung gehören.
  // In der aktuellen Implementierung zeigen wir einfach alle an, die zeitlich passen oder wir gehen davon aus
  // dass der Referent nur Slots seiner Veranstaltung sieht.
  return allSlots.value;
});

const groupedSlots = computed(() => {
  const groups = {};
  relevantSlotsForTalk.value.forEach(slot => {
    const date = slot.startTime.split('T')[0];
    if (!groups[date]) groups[date] = [];
    groups[date].push(slot);
  });
  return groups;
});

const isAvailable = (slotId) => {
  return selectedTalk.value && selectedTalk.value.availabilities && selectedTalk.value.availabilities.includes(slotId);
};

const toggleSlot = (slotId) => {
  if (!selectedTalk.value) return;
  if (!selectedTalk.value.availabilities) selectedTalk.value.availabilities = [];
  if (isAvailable(slotId)) {
    selectedTalk.value.availabilities = selectedTalk.value.availabilities.filter(id => id !== slotId);
  } else {
    selectedTalk.value.availabilities.push(slotId);
  }
};

const toggleDay = (date, status) => {
  if (!selectedTalk.value) return;
  const daySlotIds = groupedSlots.value[date].map(s => s.id);
  if (!selectedTalk.value.availabilities) selectedTalk.value.availabilities = [];
  if (status) {
    daySlotIds.forEach(id => {
      if (!selectedTalk.value.availabilities.includes(id)) selectedTalk.value.availabilities.push(id);
    });
  } else {
    selectedTalk.value.availabilities = selectedTalk.value.availabilities.filter(id => !daySlotIds.includes(id));
  }
};

const selectTalk = (talk) => {
  selectedTalk.value = { ...talk };
  isEditingNewTalk.value = false;
};

const addNewTalk = () => {
  selectedTalk.value = {
    title: '',
    abstractText: '',
    wiederholbar: false,
    availabilities: []
  };
  isEditingNewTalk.value = true;
};

const deleteTalk = async (talk) => {
  const event = events.value.find(e => e.id === talk.veranstaltungId);
  if (event && !isFutureEvent(event)) {
      alert("Vorträge für vergangene Veranstaltungen können nicht gelöscht werden.");
      return;
  }
  if (!confirm('Sind Sie sicher?')) return;
  try {
    await api.delete(`/api/referenten/vortraege/${talk.id}`);
    selectedTalk.value = null;
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

const getTalksFromOtherEvents = (targetEventId) => {
    // Gibt alle Vorträge zurück, deren Titel noch nicht in der Zielveranstaltung existiert
    const existingTitles = vortraege.value
        .filter(t => t.veranstaltungId === targetEventId)
        .map(t => t.title);

    // Wir nehmen nur einen Vortrag pro Titel aus anderen Veranstaltungen
    const otherTalks = vortraege.value.filter(t => t.veranstaltungId !== targetEventId);
    const uniqueOther = [];
    const seenTitles = new Set();

    for (const t of otherTalks) {
        if (!existingTitles.includes(t.title) && !seenTitles.has(t.title)) {
            uniqueOther.push(t);
            seenTitles.add(t.title);
        }
    }
    return uniqueOther;
};

const registerTalkForEvent = async (eventId, talkId) => {
  try {
    await api.post(`/api/referenten/events/${eventId}/vortraege/${talkId}/register`);
    await fetchReferentenVortraege();
    await fetchEventsForRegistration();
    alert("Vortrag wurde erfolgreich kopiert.");
  } catch (e) {
    console.error("Fehler beim Anmelden:", e);
  }
};

const deregisterTalkFromEvent = async (eventId, talkId) => {
  if (!confirm('Abmelden? Dies zieht den Vortrag für diese Veranstaltung zurück.')) return;
  try {
    await api.delete(`/api/referenten/events/${eventId}/vortraege/${talkId}/deregister`);
    await fetchReferentenVortraege();
    await fetchEventsForRegistration();
  } catch (e) {
    console.error("Fehler beim Abmelden:", e);
  }
};

const saveAll = async () => {
  try {
    await api.put('/api/referenten/profile', referent.value);
    if (selectedTalk.value) {
      if (isEditingNewTalk.value) {
        await api.post('/api/referenten/vortraege', selectedTalk.value);
      } else {
        await api.put(`/api/referenten/vortraege/${selectedTalk.value.id}`, selectedTalk.value);
      }
      await fetchReferentenVortraege();
      await fetchEventsForRegistration();
    }
    alert("Gespeichert!");
  } catch (e) {
    console.error("Fehler beim Speichern:", e);
  }
};

const formatDate = (d) => new Date(d).toLocaleDateString('de-DE', { weekday: 'long', day: '2-digit', month: '2-digit' });
const formatTime = (t) => t.substring(11, 16);
</script>

<style scoped>
.input-field {
  @apply mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 bg-gray-50 p-2 border;
}
.btn-primary {
  @apply inline-flex items-center px-4 py-2 border border-transparent text-sm font-medium rounded-md shadow-sm text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500;
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
