<template>
  <div v-if="isVisible" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4">
    <div class="w-full max-w-2xl rounded-xl bg-white p-6 shadow-2xl overflow-y-auto max-h-[90vh]">
      <div class="flex items-center justify-between mb-6">
        <h2 class="text-xl font-bold text-gray-900">
          {{ vortrag?.id ? 'Vortrag bearbeiten' : 'Neuen Vortrag anlegen' }}
        </h2>
        <button class="text-gray-500 hover:text-gray-700" @click="$emit('close')">✕</button>
      </div>

      <form class="space-y-4" @submit.prevent="save">
        <div class="md:col-span-2">
          <label class="block text-sm font-medium text-gray-700 mb-1">Typ</label>
          <select v-model="form.vortrag_typ" class="input-field" :disabled="!!vortrag?.id" required>
            <option value="WAHL">Wahlvortrag</option>
            <option value="PFLICHT">Pflichtvortrag</option>
          </select>
        </div>

        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Titel</label>
          <input v-model="form.titel" type="text" class="input-field" required />
        </div>

        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Inhalt / Beschreibung</label>
          <textarea v-model="form.inhalt" rows="3" class="input-field"></textarea>
        </div>

        <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Zielgruppe</label>
            <input v-model="form.zielgruppe" type="text" class="input-field" />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Referent</label>
            <select v-model="form.referent.id" class="input-field" required :disabled="form.vortrag_typ === 'PFLICHT'">
              <option :value="null">Bitte wählen...</option>
              <option v-for="r in referenten" :key="r.id" :value="r.id">
                {{ r.lastName }}, {{ r.firstName }}
              </option>
            </select>
            <p v-if="form.vortrag_typ === 'PFLICHT'" class="text-[10px] text-gray-500 mt-1 italic">
              Referent bei Pflichtvorträgen nicht änderbar.
            </p>
          </div>
        </div>

        <!-- SPEZIFISCH: PFLICHTVORTRAG -->
        <div v-if="form.vortrag_typ === 'PFLICHT'" class="bg-red-50 p-4 rounded-lg space-y-4 border border-red-100">
          <h3 class="text-xs font-bold text-red-700 uppercase tracking-wider">Pflicht-Zuweisung</h3>
          <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Pflicht-Raum</label>
              <select v-model="form.pflichtraum.id" class="input-field" required>
                <option v-for="r in raeume" :key="r.id" :value="r.id">{{ r.name }}</option>
              </select>
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Pflicht-Slot</label>
              <select v-model="form.pflichtslot.id" class="input-field" required>
                <option v-for="s in slots" :key="s.id" :value="s.id">{{ formatSlot(s) }}</option>
              </select>
            </div>
          </div>
        </div>

        <!-- SPEZIFISCH: WAHLVORTRAG -->
        <div v-if="form.vortrag_typ === 'WAHL'" class="bg-indigo-50 p-4 rounded-lg space-y-4 border border-indigo-100">
          <h3 class="text-xs font-bold text-indigo-700 uppercase tracking-wider">Wahl-Einstellungen</h3>
          <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div class="flex items-center gap-2">
              <input id="wiederholbar" v-model="form.wiederholbar" type="checkbox" class="h-5 w-5" />
              <label for="wiederholbar" class="text-sm font-medium">Wiederholbar</label>
            </div>
            <div v-if="form.wiederholbar">
              <label class="block text-xs text-gray-500 uppercase">Max. Wiederholungen</label>
              <input v-model.number="form.maxWiederholungen" type="number" min="1" class="input-field" />
            </div>
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-2">Verfügbare Wahl-Slots</label>
            <div class="grid grid-cols-1 gap-2 max-h-48 overflow-y-auto p-2 bg-white border rounded-lg">
              <div v-for="s in slots" :key="s.id" class="flex items-center gap-2 text-xs">
                <input type="checkbox" :value="s.id" v-model="selectedWahlslotIds" class="h-4 w-4 rounded" />
                <span class="font-medium text-gray-800">{{ formatSlot(s) }}</span>
              </div>
            </div>
          </div>
        </div>

        <div class="flex justify-end gap-3 pt-4 border-t">
          <button type="button" class="btn-secondary" @click="$emit('close')">Abbrechen</button>
          <button type="submit" class="btn-primary">Speichern</button>
        </div>
      </form>
    </div>
  </div>
</template>

<script setup>
import { reactive, watch, ref } from 'vue';

const props = defineProps({
  isVisible: { type: Boolean, required: true },
  vortrag: { type: Object, default: null },
  referenten: { type: Array, default: () => [] },
  raeume: { type: Array, default: () => [] },
  slots: { type: Array, default: () => [] }
});

const emit = defineEmits(['close', 'save']);
const selectedWahlslotIds = ref([]);

const form = reactive({
  id: null,
  vortrag_typ: 'WAHL',
  titel: '',
  inhalt: '',
  zielgruppe: '',
  referent: { id: null },
  pflichtraum: { id: null },
  pflichtslot: { id: null },
  wiederholbar: false,
  maxWiederholungen: 1,
});

watch(
    () => props.vortrag,
    (val) => {
      form.id = val?.id ?? null;
      form.vortrag_typ = val?.vortrag_typ ?? 'WAHL';
      form.titel = val?.titel ?? '';
      form.inhalt = val?.inhalt ?? '';
      form.zielgruppe = val?.zielgruppe ?? '';
      form.referent.id = val?.referent?.id ?? null;
      form.pflichtraum.id = val?.pflichtraum?.id ?? null;
      form.pflichtslot.id = val?.pflichtslot?.id ?? null;
      form.wiederholbar = val?.wiederholbar ?? false;
      form.maxWiederholungen = val?.maxWiederholungen ?? 1;
      selectedWahlslotIds.value = val?.wahlslots?.map(s => s.id) ?? [];
    },
    { immediate: true }
);

const formatSlot = (s) => {
  if (!s || !s.startTime) return '';
  const date = new Date(s.startTime);
  const weekday = date.toLocaleDateString('de-DE', { weekday: 'short' });
  const time = date.toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' });
  const endTime = s.endTime ? new Date(s.endTime).toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' }) : '';
  return `${s.description} (${weekday}, ${time}${endTime ? ' - ' + endTime : ''})`;
};

const save = () => {
  const payload = { ...form };
  if (form.vortrag_typ === 'WAHL') {
    payload.wahlslots = props.slots.filter(s => selectedWahlslotIds.value.includes(s.id));
    payload.pflichtraum = null;
    payload.pflichtslot = null;
  } else {
    payload.wahlslots = [];
    payload.wiederholbar = false;
  }
  emit('save', payload);
};
</script>

<style scoped>
.input-field { @apply w-full rounded-lg border border-gray-300 px-3 py-2 text-gray-900 focus:ring-2 focus:ring-indigo-500 bg-white text-sm; }
.btn-primary { @apply rounded-lg bg-indigo-600 px-4 py-2 text-white font-bold hover:bg-indigo-700 transition; }
.btn-secondary { @apply rounded-lg bg-gray-100 px-4 py-2 text-gray-700 font-medium hover:bg-gray-200 transition; }
</style>
