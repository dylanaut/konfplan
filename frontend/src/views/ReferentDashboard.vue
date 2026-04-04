<template>
  <div class="max-w-5xl mx-auto space-y-8 pb-20">

    <!-- Sektion 1: Profil (unverändert) -->

    <!-- Sektion 2: Vortragsdetails -->
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
          <label class="block text-sm font-medium text-gray-700">Inhalt (Kurzbeschreibung)</label>
          <textarea v-model="vortrag.inhalt" rows="4" class="input-field"></textarea>
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700">Zielgruppe</label>
          <input v-model="vortrag.zielgruppe" type="text" class="input-field" />
        </div>

        <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div class="flex items-center gap-4 p-4 bg-indigo-50 rounded-lg">
            <input v-model="vortrag.wiederholbar" type="checkbox" class="w-5 h-5 text-indigo-600" id="repeat" />
            <label for="repeat" class="text-sm font-medium text-indigo-900">
              Wiederholbar bei hoher Nachfrage
            </label>
          </div>
          <div v-if="vortrag.wiederholbar">
            <label class="block text-sm font-medium text-gray-700">Max. Anzahl Wiederholungen</label>
            <input v-model.number="vortrag.maxWiederholungen" type="number" min="1" class="input-field" />
          </div>
        </div>
      </div>
    </section>

    <!-- Sektion 3: Verfügbarkeit (unverändert) -->

    <!-- Global Save Button (unverändert) -->
  </div>
</template>

<script setup>
/* ... (Logic mit aktualisierten Feldnamen) ... */
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
    vortrag.value = vortragRes.data || { titel: '', inhalt: '', zielgruppe: '', wiederholbar: false, maxWiederholungen: 1 };
    allSlots.value = slotRes.data;
    verfuegbareSlotIds.value = vortragRes.data?.verfuegbarkeiten?.map(v => v.slot.id) || [];
  } catch (err) { console.error(err); }
});

const saveAll = async () => {
  try {
    await Promise.all([
      api.put('/api/referent/profile', referent.value),
      api.put('/api/referent/vortrag', { ...vortrag.value, verfuegbareSlotIds: verfuegbareSlotIds.value })
    ]);
    alert("Erfolgreich gespeichert!");
  } catch (e) { alert("Fehler!"); }
};

/* ... (Hilfsfunktionen wie bisher) ... */
</script>
