<template>
  <section class="space-y-6 animate-fade-in">
    <!-- Qualitätsmetriken -->
    <div class="grid grid-cols-1 md:grid-cols-4 gap-4">
      <div class="bg-white p-3 rounded-xl shadow-sm border border-gray-100">
        <div class="text-[9px] text-gray-500 uppercase font-bold">Ø Priorität</div>
        <div class="text-xl font-black text-indigo-600">{{ qualitaet.durchschnittsPrio?.toFixed(2) || '0.00' }}</div>
      </div>
    </div>

    <!-- Artefakte (PDFs) -->
    <div class="bg-white p-4 rounded-xl shadow-sm border border-gray-100">
      <h3 class="text-sm font-bold mb-3">Downloads & Berichte</h3>
      <div class="grid grid-cols-1 md:grid-cols-2 gap-4 text-xs">
        <div class="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
          <div class="flex items-center space-x-2">
            <img src="/logo/konfplan-light_footer.svg" alt="Icon" class="w-5 h-5"/>
            <span class="font-semibold">Gesamt-Stundenplan</span>
          </div>
          <div class="space-x-2">
            <button @click="preview('raeume')"
                    class="px-2 py-1 bg-white border border-gray-200 rounded text-gray-600 hover:bg-gray-100">Vorschau
            </button>
            <button @click="download('raeume-pdf')" class="px-2 py-1 bg-indigo-500 text-white rounded hover:bg-indigo-600">PDF
            </button>
          </div>
        </div>
        <div class="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
          <div class="flex items-center space-x-2">
            <img src="/logo/konfplan-light_footer.svg" alt="Icon" class="w-5 h-5"/>
            <span class="font-semibold">Raumschilder</span>
          </div>
          <div class="space-x-2">
            <button @click="preview('raumschilder')"
                    class="px-2 py-1 bg-white border border-gray-200 rounded text-gray-600 hover:bg-gray-100">Vorschau
            </button>
            <button @click="download('raumschilder-pdf')" class="px-2 py-1 bg-indigo-500 text-white rounded hover:bg-indigo-600">
              PDF
            </button>
          </div>
        </div>
        <div class="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
          <div class="flex items-center space-x-2">
            <img src="/logo/konfplan-light_footer.svg" alt="Icon" class="w-5 h-5"/>
            <span class="font-semibold">Laufzettel (Teilnehmer)</span>
          </div>
          <div class="space-x-2">
            <button @click="preview('laufzettel/teilnehmer')"
                    class="px-2 py-1 bg-white border border-gray-200 rounded text-gray-600 hover:bg-gray-100">Vorschau
            </button>
            <button @click="download('laufzettel/teilnehmer-pdf')" class="px-2 py-1 bg-indigo-500 text-white rounded hover:bg-indigo-600">
              PDF
            </button>
          </div>
        </div>
        <div class="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
          <div class="flex items-center space-x-2">
            <img src="/logo/konfplan-light_footer.svg" alt="Icon" class="w-5 h-5"/>
            <span class="font-semibold">Laufzettel (Referenten)</span>
          </div>
          <div class="space-x-2">
            <button @click="preview('laufzettel/referenten')"
                    class="px-2 py-1 bg-white border border-gray-200 rounded text-gray-600 hover:bg-gray-100">Vorschau
            </button>
            <button @click="download('laufzettel/referenten-pdf')" class="px-2 py-1 bg-indigo-500 text-white rounded hover:bg-indigo-600">
              PDF
            </button>
          </div>
        </div>
        <div class="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
          <div class="flex items-center space-x-2">
            <img src="/logo/konfplan-light_footer.svg" alt="Icon" class="w-5 h-5"/>
            <span class="font-semibold">Freie Slots (Referenten)</span>
          </div>
          <div class="space-x-2">
            <button @click="preview('freie-slots-referenten')"
                    class="px-2 py-1 bg-white border border-gray-200 rounded text-gray-600 hover:bg-gray-100">Vorschau
            </button>
            <button @click="download('freie-slots-referenten-pdf')"
                    class="px-2 py-1 bg-indigo-500 text-white rounded hover:bg-indigo-600">
              PDF
            </button>
          </div>
        </div>
        <div class="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
          <div class="flex items-center space-x-2">
            <img src="/logo/konfplan-light_footer.svg" alt="Icon" class="w-5 h-5"/>
            <span class="font-semibold">Freie Slots (Teilnehmer)</span>
          </div>
          <div class="space-x-2">
            <button @click="preview('freie-slots-teilnehmer')"
                    class="px-2 py-1 bg-white border border-gray-200 rounded text-gray-600 hover:bg-gray-100">Vorschau
            </button>
            <button @click="download('freie-slots-teilnehmer-pdf')"
                    class="px-2 py-1 bg-indigo-500 text-white rounded hover:bg-indigo-600">PDF
            </button>
          </div>
        </div>

        <div class="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
          <div class="flex items-center space-x-2">
            <img src="/logo/konfplan-light_footer.svg" alt="Icon" class="w-5 h-5"/>
            <span class="font-semibold">Messeplaner</span>
          </div>
          <div class="space-x-2">
            <button @click="preview('admin-dashboard')"
                    class="px-2 py-1 bg-white border border-gray-200 rounded text-gray-600 hover:bg-gray-100">Dashboard
            </button>
            <button @click="preview('teilnehmer-dashboard')"
                    class="px-2 py-1 bg-white border border-gray-200 rounded text-gray-600 hover:bg-gray-100">Teilnehmer
            </button>
            <button @click="preview('prios-dashboard')"
                    class="px-2 py-1 bg-white border border-gray-200 rounded text-gray-600 hover:bg-gray-100">Prios
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- Belegungsplan pro Tag -->
    <div v-for="(day, date) in belegungsplanProTag" :key="date"
         class="bg-white shadow-lg rounded-2xl overflow-hidden border border-gray-100">
      <h3 class="text-lg font-bold p-4 bg-gray-50 border-b border-gray-200 text-indigo-800">{{ formatDate(date) }}</h3>
      <div class="overflow-x-auto">
        <table class="min-w-full divide-y divide-gray-200">
          <thead class="bg-gray-100 text-xs uppercase font-semibold text-gray-600">
          <tr>
            <th class="px-3 py-2 text-left sticky left-0 bg-gray-100 z-10 w-32 min-w-[128px]">Zeit</th>
            <template v-for="(group, gebaeudeName) in raeumeGruppiert" :key="gebaeudeName">
              <th :colspan="group.length" class="px-3 py-2 text-center border-l border-gray-200 whitespace-nowrap bg-gray-200">
                {{ gebaeudeName }}
              </th>
            </template>
          </tr>
          <tr>
            <th class="px-3 py-2 text-left sticky left-0 bg-gray-100 z-10 w-32 min-w-[128px]"></th>
            <template v-for="(group, gebaeudeName) in raeumeGruppiert" :key="gebaeudeName">
              <th v-for="raum in group" :key="raum.id" class="px-3 py-2 text-center border-l border-gray-200 whitespace-nowrap">
                {{ raum.name }}
              </th>
            </template>
          </tr>
          </thead>
          <tbody class="divide-y divide-gray-100 text-xs">
          <tr v-for="(slot, slotIndex) in day.slots" :key="slot.id"
              :class="['hover:bg-gray-50 transition-colors duration-150', { 'first-row': slotIndex === 0 }]">
            <td class="px-3 py-2 font-mono text-indigo-700 sticky left-0 bg-white z-10 w-32 min-w-[128px]">{{ slot.zeit }}</td>
            <template v-for="(group, gebaeudeName) in raeumeGruppiert" :key="gebaeudeName">
              <td v-for="raum in group" :key="raum.id"
                  class="px-2 py-2 text-center border-l border-gray-200 align-top relative group">
                <div v-if="day.belegungen[slot.id] && day.belegungen[slot.id][raum.id]"
                     class="p-2 rounded-lg h-full flex flex-col justify-center"
                     :style="{ backgroundColor: getVortragColor(day.belegungen[slot.id][raum.id].vortragTitel) }">
                  <p class="font-bold text-white text-[10px] leading-tight">{{
                      day.belegungen[slot.id][raum.id].referentNachname
                    }}</p>
                  <p class="text-white/80 text-[9px] leading-tight mt-1">{{
                      day.belegungen[slot.id][raum.id].vortragTitelShort
                    }}</p>
                  <!-- Tooltip -->
                  <div
                      class="tooltip-content absolute left-1/2 -translate-x-1/2 mb-2 w-64 p-3 bg-gray-800 text-white text-xs rounded-lg shadow-xl opacity-0 group-hover:opacity-100 transition-opacity duration-200 z-20 pointer-events-none">
                    <p class="font-bold border-b border-gray-600 pb-1 mb-1">{{
                        day.belegungen[slot.id][raum.id].vortragTitel
                      }}</p>
                    <p><strong>Referent:</strong> {{ day.belegungen[slot.id][raum.id].referentNachname }}</p>
                    <p><strong>Auslastung:</strong> {{ day.belegungen[slot.id][raum.id].teilnehmerNamen.length }} /
                      {{ raum.kapazitaet }}</p>
                    <p class="mt-2 text-gray-300 text-[10px]">{{
                        day.belegungen[slot.id][raum.id].teilnehmerNamen.join(', ')
                      }}</p>
                    <div class="tooltip-arrow absolute left-1/2 -translate-x-1/2 w-2 h-2 bg-gray-800 rotate-45"></div>
                  </div>
                </div>
              </td>
            </template>
          </tr>
          </tbody>
        </table>
      </div>
    </div>
  </section>
</template>

<script setup>
import {computed} from 'vue';
import {useEventContextStore} from '../../../stores/eventContext';
import api from '../../../api/axios';

const props = defineProps({
  belegungsPlan: {type: Array, required: true},
  qualitaet: {type: Object, required: true},
  eventSlots: {type: Array, required: true},
  raeume: {type: Array, required: true}
});

const eventContext = useEventContextStore();

const raeumeGruppiert = computed(() => {
  const grouped = props.raeume.reduce((acc, raum) => {
    const gebaeudeName = raum.gebaeude?.name || 'Ohne Gebäude';
    if (!acc[gebaeudeName]) {
      acc[gebaeudeName] = [];
    }
    acc[gebaeudeName].push(raum);
    return acc;
  }, {});

  for (const key in grouped) {
    grouped[key].sort((a, b) => a.name.localeCompare(b.name));
  }
  return grouped;
});

const belegungsplanProTag = computed(() => {
  const plan = {};
  if (!props.eventSlots || !props.belegungsPlan) return plan;

  // Slots nach Datum gruppieren
  const slotsProTag = props.eventSlots.reduce((acc, slot) => {
    const date = slot.startTime.split('T')[0];
    if (!acc[date]) acc[date] = [];
    acc[date].push({
      id: slot.id,
      zeit: `${slot.startTime.substring(11, 16)} - ${slot.endTime.substring(11, 16)}`
    });
    return acc;
  }, {});

  // Belegungen für schnellen Zugriff strukturieren
  const belegungenMap = props.belegungsPlan.reduce((acc, belegung) => {
    if (!acc[belegung.slotId]) acc[belegung.slotId] = {};
    acc[belegung.slotId][belegung.raumId] = {
      ...belegung,
      vortragTitelShort: belegung.vortragTitel.length > 20 ? belegung.vortragTitel.substring(0, 18) + '…' : belegung.vortragTitel
    };
    return acc;
  }, {});

  // Endgültige Struktur zusammenbauen
  for (const date in slotsProTag) {
    plan[date] = {
      slots: slotsProTag[date].sort((a, b) => a.zeit.localeCompare(b.zeit)),
      belegungen: belegungenMap
    };
  }
  return plan;
});

const fetchReport = async (report, blobType) => {
  try {
    const vid = eventContext.selectedEvent.id;
    const response = await api.get(`/api/reports/${vid}/${report}`, {responseType: 'blob'});
    return new Blob([response.data], {type: blobType});
  } catch (error) {
    console.error(`Fehler beim Abrufen von ${report}:`, error);
    return null;
  }
};

const preview = async (report) => {
  const file = await fetchReport(report, 'text/html;charset=utf-8');
  if (file) {
    // Für PDFs weiterhin die Blob-URL verwenden, da sie den PDF-Viewer des Browsers korrekt initialisiert.
    if (file.type.includes('pdf')) {
      const fileURL = URL.createObjectURL(file);
      window.open(fileURL, '_blank');
    } else {
      // Für HTML den Inhalt manuell in ein neues Fenster schreiben, um den Tab-Titel korrekt zu setzen.
      const htmlContent = await file.text();
      const newWindow = window.open('', '_blank');
      if (newWindow) {
        newWindow.document.writeln(htmlContent);
        newWindow.document.close(); // Wichtig, um das Laden abzuschließen
      }
    }
  }
};

const download = async (report) => {
  const file = await fetchReport(report, 'application/pdf');
  if (file) {
    const downloadName = report.replace('-pdf', '') + '.pdf';
    const fileURL = URL.createObjectURL(file);
    const link = document.createElement('a');
    link.href = fileURL;
    link.setAttribute('download', downloadName);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  }
};

const formatDate = (dateString) => {
  return new Date(dateString).toLocaleDateString('de-DE', {weekday: 'long', year: 'numeric', month: 'long', day: 'numeric'});
};

// Helper für farbliche Kodierung der Vorträge
const vortragColors = {};
const colorPalette = ['#1A5276', '#1E6B3C', '#C0392B', '#D4820A', '#6E2F7A', '#4A4A4A', '#B7470A', '#7D6608', '#5D6D7E', '#C2185B', '#00838F', '#5C4033'];
let colorIndex = 0;

const getVortragColor = (titel) => {
  if (titel === 'Frei') {
    return '#f5f5f5'
  }
  if (!vortragColors[titel]) {
    vortragColors[titel] = colorPalette[colorIndex % colorPalette.length];
    colorIndex++;
  }
  return vortragColors[titel];
};
</script>

<style scoped>
.animate-fade-in {
  animation: fadeIn 0.5s ease-in-out;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.tooltip-content {
  bottom: 100%;
}

.tooltip-arrow {
  bottom: -4px;
}

.first-row .tooltip-content {
  bottom: auto;
  top: 100%;
  margin-bottom: 0;
  margin-top: 0.5rem;
}

.first-row .tooltip-arrow {
  bottom: auto;
  top: -4px;
}
</style>