<template>
  <div class="max-w-5xl mx-auto space-y-8 pb-20">

    <!-- MODUS: MEIN EINSATZPLAN (Sichtbar, wenn Zuweisungen vorhanden sind) -->
    <section v-if="myPlan.length > 0" class="bg-emerald-900 text-white p-8 rounded-2xl shadow-2xl animate-fade-in">
      <div class="flex items-center gap-3 mb-6">
        <CalendarCheckIcon class="w-8 h-8 text-emerald-300" />
        <h2 class="text-3xl font-black">Mein Einsatzplan</h2>
      </div>

      <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        <div v-for="z in myPlan" :key="z.id" class="bg-white/10 border border-white/20 p-5 rounded-xl backdrop-blur-sm">
          <div class="text-[10px] uppercase font-bold text-emerald-300 mb-1">{{ z.slotZeit }}</div>
          <h3 class="text-lg font-bold mb-2">{{ z.vortragTitel }}</h3>
          <div class="flex items-center gap-2 text-sm text-emerald-100">
            <MapPinIcon class="w-4 h-4" />
            <span>{{ z.raumName }} ({{ z.gebaeudeName }})</span>
          </div>
        </div>
      </div>

      <div class="mt-6 text-xs text-emerald-300 italic">
        Hinweis: Dies sind Ihre fest gebuchten Vortragsslots.
      </div>
    </section>

    <!-- Sektion 1: Profil (unverändert) -->
    <section class="bg-white p-6 rounded-xl shadow-sm border border-gray-100">
      <div class="flex items-center gap-2 mb-6 text-indigo-600">
        <UserIcon class="w-6 h-6" />
        <h2 class="text-xl font-bold">Mein Profil</h2>
      </div>
      <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div>
          <label class="block text-sm font-medium text-gray-700">Vorname</label>
          <input v-model="referent.firstName" type="text" class="input-field bg-gray-100" readonly />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700">Nachname</label>
          <input v-model="referent.lastName" type="text" class="input-field bg-gray-100" readonly />
        </div>
        <div class="md:col-span-2">
          <label class="block text-sm font-medium text-gray-700">Position / Titel</label>
          <input v-model="referent.jobRole" type="text" class="input-field" placeholder="z.B. IT-Experte, Dozent..." />
        </div>
        <div class="md:col-span-2">
          <label class="block text-sm font-medium text-gray-700">Organisation (Firma/Uni)</label>
          <input v-model="referent.organisation" type="text" class="input-field" placeholder="Name Ihrer Organisation" />
        </div>
        <div class="md:col-span-2">
          <label class="block text-sm font-medium text-gray-700">Motto / Slogan</label>
          <input v-model="referent.slogan" type="text" class="input-field" placeholder="Ihre Kernbotschaft in einem Satz" />
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

    <!-- Sektion 2: Vortragsdetails (unverändert) -->
    <section class="bg-white p-6 rounded-xl shadow-sm border border-gray-100">
      <div class="flex items-center gap-2 mb-6 text-indigo-600">
        <FileTextIcon class="w-6 h-6" />
        <h2 class="text-xl font-bold">Vortragsdetails</h2>
      </div>
      <div class="space-y-4">
        <div>
          <label class="block text-sm font-medium text-gray-700">Titel des Vortrags</label>
          <input v-model="vortrag.titel" type="text" class="input-field" />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700">Abstract (Kurzbeschreibung)</label>
          <textarea v-model="vortrag.inhalt" rows="4" class="input-field"></textarea>
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700">Zielpublikum</label>
          <input v-model="vortrag.zielgruppe" type="text" class="input-field" />
        </div>

        <div class="flex items-center gap-4 p-4 bg-indigo-50 rounded-lg">
          <input v-model="vortrag.wiederholbar" type="checkbox" class="w-5 h-5 text-indigo-600" id="repeat" />
          <label for="repeat" class="text-sm font-medium text-indigo-900">
            Ich bin bereit, den Vortrag bei hoher Nachfrage mehrfach zu halten.
          </label>
        </div>
      </div>
    </section>

    <!-- Global Save Button -->
    <div class="fixed bottom-6 right-6">
      <button @click="saveAll" class="bg-indigo-600 hover:bg-indigo-700 text-white px-10 py-4 rounded-full shadow-2xl font-bold flex items-center gap-2 transition-transform active:scale-95">
        <SaveIcon class="w-5 h-5" /> Alles speichern
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue';
import api from '../api/axios';
import {
  User as UserIcon, FileText as FileTextIcon,
  Save as SaveIcon, CalendarCheck as CalendarCheckIcon,
  MapPin as MapPinIcon
} from 'lucide-vue-next';

const referent = ref({});
const vortrag = ref({});
const myPlan = ref([]);

onMounted(async () => {
  try {
    const [userRes, vortragRes, planRes] = await Promise.all([
      api.get('/api/referent/profile'),
      api.get('/api/referent/vortrag'),
      api.get('/api/referent/my-plan')
    ]);
    referent.value = userRes.data;
    vortrag.value = vortragRes.data || { titel: '', inhalt: '', zielgruppe: '', wiederholbar: false };
    myPlan.value = planRes.data;
  } catch (err) {
    console.error("Ladefehler:", err);
  }
});

const saveAll = async () => {
  try {
    await Promise.all([
      api.put('/api/referent/profile', referent.value),
      api.put('/api/referent/vortrag', vortrag.value)
    ]);
    alert("Erfolgreich gespeichert!");
  } catch (e) {
    alert("Fehler beim Speichern.");
  }
};

const formatTime = (t) => t.substring(11, 16);
</script>

<style scoped>
.input-field {
  @apply mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 bg-gray-50 p-2 border;
}
.animate-fade-in { animation: fadeIn 0.5s ease-in-out; }
@keyframes fadeIn { from { opacity: 0; transform: translateY(-10px); } to { opacity: 1; transform: translateY(0); } }
</style>
