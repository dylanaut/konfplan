<template>
  <div class="max-w-5xl mx-auto space-y-8 pb-20">

    <!-- Sektion 1: Profil -->
    <section class="bg-white p-6 rounded-xl shadow-sm border border-gray-100">
      <div class="flex items-center gap-2 mb-6 text-indigo-600">
        <UserIcon class="w-6 h-6" />
        <h2 class="text-xl font-bold">Mein Profil</h2>
      </div>
      <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div>
          <label class="block text-sm font-medium text-gray-700">Vorname</label>
          <input v-model="referent.firstName" type="text" class="input-field" readonly />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700">Nachname</label>
          <input v-model="referent.lastName" type="text" class="input-field" readonly />
        </div>
        <div class="md:col-span-2">
          <label class="block text-sm font-medium text-gray-700">Biografie / Kurzvita</label>
          <textarea v-model="referent.biography" rows="3" class="input-field"></textarea>
        </div>
        <div class="md:col-span-2">
          <label class="block text-sm font-medium text-gray-700">E-Mail Adresse</label>
          <input v-model="referent.email" type="email" class="input-field" />
        </div>
      </div>
    </section>

    <!-- Sektion 2: Vortragsdetails -->
    <section class="bg-white p-6 rounded-xl shadow-sm border border-gray-100">
      <div class="flex items-center gap-2 mb-6 text-indigo-600">
        <FileTextIcon class="w-6 h-6" />
        <h2 class="text-xl font-bold">Vortragsdetails</h2>
      </div>
      <div class="space-y-4">
        <div>
          <label class="block text-sm font-medium text-gray-700">Titel des Vortrags</label>
          <input v-model="vortrag.title" type="text" class="input-field" />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700">Abstract (Kurzbeschreibung)</label>
          <textarea v-model="vortrag.abstractText" rows="4" class="input-field"></textarea>
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700">Zielpublikum</label>
          <input v-model="vortrag.targetAudience" type="text" class="input-field" />
        </div>

        <div class="flex items-center gap-4 p-4 bg-indigo-50 rounded-lg">
          <input v-model="vortrag.readyToRepeat" type="checkbox" class="w-5 h-5 text-indigo-600" id="repeat" />
          <label for="repeat" class="text-sm font-medium text-indigo-900">
            Ich bin bereit, den Vortrag bei hoher Nachfrage mehrfach zu halten.
          </label>
        </div>
      </div>
    </section>

    <!-- Sektion 3: Verfügbarkeit (Slot-Grid) -->
    <section class="bg-white p-6 rounded-xl shadow-sm border border-gray-100">
      <div class="flex items-center gap-2 mb-6 text-indigo-600">
        <CalendarIcon class="w-6 h-6" />
        <h2 class="text-xl font-bold">Meine Verfügbarkeit</h2>
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

    <!-- Global Save Button -->
    <div class="fixed bottom-6 right-6">
      <button @click="saveAll" class="bg-indigo-600 hover:bg-indigo-700 text-white px-10 py-4 rounded-full shadow-2xl font-bold flex items-center gap-2">
        <SaveIcon class="w-5 h-5" /> Alles speichern
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue';
import api from '../api/axios';
import { User as UserIcon, FileText as FileTextIcon, Calendar as CalendarIcon, Save as SaveIcon } from 'lucide-vue-next';

const referent = ref({});
const vortrag = ref({});
const allSlots = ref([]);
const verfuegbareSlotIds = ref([]);

onMounted(async () => {
  try {
    const [userRes, vortragRes, slotRes] = await Promise.all([
      api.get('/api/referent/profile'),
      api.get('/api/referent/vortrag'),
      api.get('/api/admin/slots')
    ]);
    referent.value = userRes.data;
    vortrag.value = vortragRes.data || { title: '', abstractText: '', targetAudience: '', readyToRepeat: false };
    allSlots.value = slotRes.data;
    verfuegbareSlotIds.value = vortragRes.data?.verfuegbarkeiten?.map(v => v.slot.id) || [];
  } catch (err) {
    console.error("Ladefehler:", err);
  }
});

const groupedSlots = computed(() => {
  const groups = {};
  allSlots.value.forEach(slot => {
    const date = slot.startTime.split('T')[0];
    if (!groups[date]) groups[date] = [];
    groups[date].push(slot);
  });
  return groups;
});

const isAvailable = (slotId) => verfuegbareSlotIds.value.includes(slotId);

const toggleSlot = (slotId) => {
  if (isAvailable(slotId)) {
    verfuegbareSlotIds.value = verfuegbareSlotIds.value.filter(id => id !== slotId);
  } else {
    verfuegbareSlotIds.value.push(slotId);
  }
};

const toggleDay = (date, status) => {
  const daySlotIds = groupedSlots.value[date].map(s => s.id);
  if (status) {
    daySlotIds.forEach(id => { if(!isAvailable(id)) verfuegbareSlotIds.value.push(id) });
  } else {
    verfuegbareSlotIds.value = verfuegbareSlotIds.value.filter(id => !daySlotIds.includes(id));
  }
};

const saveAll = async () => {
  try {
    await Promise.all([
      api.put('/api/referent/profile', referent.value),
      api.put('/api/referent/vortrag', { ...vortrag.value, verfuegbareSlotIds: verfuegbareSlotIds.value })
    ]);
    alert("Erfolgreich gespeichert!");
  } catch (e) {
    alert("Fehler beim Speichern.");
  }
};

const formatDate = (d) => new Date(d).toLocaleDateString('de-DE', { weekday: 'long', day: '2-digit', month: '2-digit' });
const formatTime = (t) => t.substring(11, 16);
</script>

<style scoped>
.input-field {
  @apply mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 bg-gray-50 p-2 border;
}
</style>
