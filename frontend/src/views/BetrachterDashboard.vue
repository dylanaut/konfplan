<template>
  <div class="max-w-6xl mx-auto space-y-6 pb-20">
    <div class="flex justify-center py-4">
      <img src="/logo/konfplan-light.svg" alt="Konfplan Logo" class="h-16" />
    </div>

    <section class="bg-white p-6 rounded-xl shadow-sm border border-gray-100">
      <div class="flex items-center justify-between mb-4 text-indigo-600">
        <div class="flex items-center gap-2">
          <EyeIcon class="w-6 h-6" />
          <h2 class="text-xl font-bold">Betrachter-Ansicht</h2>
        </div>
        <select v-if="veranstaltungen.length > 1" v-model="selectedVid" class="input-field w-64">
          <option v-for="v in veranstaltungen" :key="v.id" :value="v.id">{{ v.name }}</option>
        </select>
      </div>

      <div v-if="veranstaltungen.length === 0" class="text-center text-gray-500 py-8">
        <p>Ihnen sind aktuell keine Veranstaltungen zugeordnet.</p>
      </div>
      <div v-else-if="selectedVeranstaltung">
        <h3 class="font-bold text-lg text-gray-800">{{ selectedVeranstaltung.name }}</h3>
        <p class="text-xs text-gray-600 mb-4">{{ formatDate(selectedVeranstaltung.beginntAm) }} - {{ formatDate(selectedVeranstaltung.endetAm) }}</p>

        <div v-if="teilnehmer.length === 0" class="text-center text-gray-500 py-8 border-2 border-dashed border-gray-200 rounded-lg">
          <p>Ihnen sind aktuell keine Gruppen zugewiesen, oder diese Gruppen haben keine Teilnehmer.</p>
        </div>
        <div v-else class="overflow-x-auto">
          <table class="min-w-full divide-y divide-gray-200 text-xs">
            <thead class="bg-gray-50 text-[9px] uppercase font-bold text-gray-500">
              <tr>
                <th class="px-3 py-2 text-left">Name</th>
                <th class="px-3 py-2 text-left">Gruppen</th>
                <th v-for="slot in sortedSlots" :key="slot.id" class="px-2 py-2 text-center text-[8px]">
                  {{ formatTime(slot.startTime) }}
                </th>
                <th class="px-3 py-2 text-left">Prioritäten</th>
                <th class="px-3 py-2 text-left">Vorträge</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-gray-100">
              <tr v-for="t in teilnehmer" :key="t.id" class="hover:bg-gray-50">
                <td class="px-3 py-2 font-bold">{{ t.firstName }} {{ t.lastName }}</td>
                <td class="px-3 py-2 text-gray-500">
                  {{ alleGruppenwerte(t).join(', ') || '–' }}
                </td>
                <td v-for="slot in sortedSlots" :key="slot.id" class="px-2 py-2 text-center">
                  <CheckIcon v-if="istVerfuegbar(t.id, slot.id)" class="w-3.5 h-3.5 inline text-green-600" />
                  <XIcon v-else class="w-3.5 h-3.5 inline text-gray-300" />
                </td>
                <td class="px-3 py-2 text-gray-500">
                  <span v-if="sortiertePrioritaeten(t).length === 0">–</span>
                  <ul v-else class="space-y-0.5">
                    <li v-for="p in sortiertePrioritaeten(t)" :key="p.vortragId">
                      {{ kurzerVortragTitel(p.vortragId) }}: {{ p.prioWert }}
                    </li>
                  </ul>
                </td>
                <td class="px-3 py-2 text-gray-500">
                  <span v-if="!teilnehmerVortraegeByTeilnehmer[t.id] || teilnehmerVortraegeByTeilnehmer[t.id].length === 0">–</span>
                  <table v-else class="text-[10px]">
                    <thead>
                      <tr class="text-left text-gray-400">
                        <th class="pr-2 font-medium">Vorträge</th>
                        <th class="pr-2 font-medium">Prio</th>
                        <th class="font-medium">besucht</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr v-for="(v, idx) in teilnehmerVortraegeByTeilnehmer[t.id]" :key="idx">
                        <td class="pr-2">{{ v.vortragTitel }}</td>
                        <td class="pr-2">{{ v.prioAnzeige }}</td>
                        <td>{{ v.besuchtAnzeige }}</td>
                      </tr>
                    </tbody>
                  </table>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue';
import { Eye as EyeIcon, Check as CheckIcon, X as XIcon } from '@lucide/vue';
import api from '../api/axios';
import { getGruppenkategorieWerte } from '../composables/useGruppenkategorieColumns';

const veranstaltungen = ref([]);
const selectedVid = ref(null);
const teilnehmer = ref([]);
const verfuegbarkeiten = ref([]);
const teilnehmerVortraegeByTeilnehmer = ref({});
const slots = ref([]);
const vortraege = ref([]);

const selectedVeranstaltung = computed(() => veranstaltungen.value.find(v => v.id === selectedVid.value) ?? null);
const sortedSlots = computed(() =>
  slots.value
    .filter(s => s.veranstaltungId === selectedVid.value)
    .sort((a, b) => new Date(a.startTime) - new Date(b.startTime))
);

const alleGruppenwerte = (t) =>
  Object.keys(t?.gruppenwerteByKategorie || {}).flatMap(name => getGruppenkategorieWerte(t, name));

const ANZAHL_TITELWOERTER = 4;
const vortragTitelById = computed(() => {
  const map = {};
  for (const v of vortraege.value) {
    map[v.id] = v.titel;
  }
  return map;
});
const kurzerVortragTitel = (vortragId) => {
  const titel = vortragTitelById.value[vortragId];
  if (!titel) return `#${vortragId}`;
  const woerter = titel.split(/\s+/);
  return woerter.length > ANZAHL_TITELWOERTER
    ? woerter.slice(0, ANZAHL_TITELWOERTER).join(' ') + '…'
    : titel;
};
const sortiertePrioritaeten = (t) =>
  [...(t.prioritaeten || [])].sort((a, b) => b.prioWert - a.prioWert);

const verfuegbarkeitByTeilnehmer = computed(() => {
  const map = {};
  for (const v of verfuegbarkeiten.value) {
    map[v.nutzerId] = new Set(v.verfuegbareSlotIds || []);
  }
  return map;
});
const istVerfuegbar = (teilnehmerId, slotId) => verfuegbarkeitByTeilnehmer.value[teilnehmerId]?.has(slotId) ?? false;

const formatDate = (d) => d ? new Date(d).toLocaleDateString('de-DE') : '';
const formatTime = (d) => d ? new Date(d).toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' }) : '';

const loadVeranstaltungsDaten = async (vid) => {
  if (!vid) return;
  const [teilnehmerRes, verfuegbarkeitenRes, teilnehmerVortraegeRes, slotsRes, vortraegeRes] = await Promise.all([
    api.get(`/api/betrachter/veranstaltungen/${vid}/teilnehmer`),
    api.get(`/api/betrachter/veranstaltungen/${vid}/verfuegbarkeiten`),
    api.get(`/api/betrachter/veranstaltungen/${vid}/teilnehmer-vortraege`),
    api.get('/api/slots'),
    api.get(`/api/betrachter/veranstaltungen/${vid}/vortraege`),
  ]);
  teilnehmer.value = teilnehmerRes.data;
  verfuegbarkeiten.value = verfuegbarkeitenRes.data;
  teilnehmerVortraegeByTeilnehmer.value = teilnehmerVortraegeRes.data;
  slots.value = slotsRes.data;
  vortraege.value = vortraegeRes.data;
};

watch(selectedVid, (vid) => { loadVeranstaltungsDaten(vid); });

onMounted(async () => {
  const response = await api.get('/api/betrachter/veranstaltungen');
  veranstaltungen.value = response.data;
  if (veranstaltungen.value.length > 0) {
    selectedVid.value = veranstaltungen.value[0].id;
  }
});
</script>

<style scoped>
@reference "tailwindcss";

.input-field {
  @apply rounded-lg border border-gray-300 px-3 py-2 text-gray-900 focus:outline-none focus:ring-2 focus:ring-indigo-500 bg-white text-sm;
}
</style>
