<template>
  <div class="max-w-5xl mx-auto space-y-8 pb-20">
    <!-- App Logo -->
    <div class="flex justify-center py-4">
      <img src="/logo/konfplan-light.svg" alt="Konfplan Logo" class="h-16" />
    </div>

    <!-- Sektion: Mein Zeitplan -->
    <section class="bg-white p-6 rounded-xl shadow-sm border border-gray-100">
      <div class="flex items-center justify-between mb-6 text-indigo-600">
        <div class="flex items-center gap-2">
          <CalendarCheckIcon class="w-6 h-6" />
          <h2 class="text-xl font-bold">Mein Zeitplan</h2>
        </div>
      </div>
      <div class="space-y-4">
        <div v-if="events.length === 0" class="text-center text-gray-500 py-8">
          <p>Sie sind für keine Veranstaltungen angemeldet.</p>
        </div>
        <div v-for="event in events" :key="event.id" class="border border-gray-200 rounded-lg p-4 flex justify-between items-center">
          <div>
            <h3 class="font-bold text-lg text-gray-800">{{ event.name }}</h3>
            <p class="text-xs text-gray-600">{{ formatDate(event.beginntAm) }} - {{ formatDate(event.endetAm) }}</p>
          </div>
          <div v-if="event.planErstellt" class="flex gap-2">
            <button @click="viewMySchedule(event.id)" class="btn-secondary">
              <PrinterIcon class="w-4 h-4 mr-2" /> Laufzettel
            </button>
            <button @click="downloadMySchedule(event.id)" class="btn-primary">
              <DownloadIcon class="w-4 h-4 mr-2" /> PDF
            </button>
          </div>
          <div v-else>
            <span class="text-xs text-gray-400 italic">Plan noch nicht verfügbar</span>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import api from '../api/axios';
import { CalendarCheck as CalendarCheckIcon, Printer as PrinterIcon, Download as DownloadIcon } from '@lucide/vue';

const events = ref([]);

onMounted(async () => {
  await fetchTeilnehmerVeranstaltungen();
});

const fetchTeilnehmerVeranstaltungen = async () => {
  try {
    const response = await api.get('/api/teilnehmer/veranstaltungen');
    events.value = response.data;
  } catch (error) {
    console.error("Fehler beim Laden der Veranstaltungen:", error);
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

const formatDate = (d) => new Date(d).toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' });
</script>

<style scoped>
.btn-primary {
  @apply inline-flex items-center px-4 py-2 border border-transparent text-sm font-medium rounded-md shadow-sm text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 disabled:opacity-50;
}
.btn-secondary {
  @apply inline-flex items-center px-4 py-2 border border-gray-300 text-sm font-medium rounded-md text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500;
}
</style>