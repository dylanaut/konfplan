<template>
  <section class="space-y-4 animate-fade-in">
    <div class="bg-white p-4 rounded-xl shadow-sm border border-gray-100">
      <h2 class="text-lg font-bold text-gray-800 mb-1">Nachricht senden</h2>
      <p class="text-xs text-gray-500 mb-4">
        Sendet eine Nachricht in das In-App-Postfach ausgewählter Organisatoren, Teilnehmer und/oder Referenten dieser
        Veranstaltung. Kein E-Mail-Versand, rein In-App.
      </p>

      <div v-if="error" class="bg-red-50 border border-red-200 text-red-700 text-xs rounded-xl p-3 mb-3">{{ error }}</div>
      <div v-if="success" class="bg-green-50 border border-green-200 text-green-700 text-xs rounded-xl p-3 mb-3">{{ success }}</div>

      <div class="space-y-4">
        <div>
          <label class="block text-xs font-bold text-gray-500 uppercase tracking-wider mb-1">Empfänger ({{ selectedIds.length }} ausgewählt)</label>
          <input v-model="filter" placeholder="Suchen..." class="input-field w-full text-xs py-1 px-2 mb-2"/>

          <div class="border border-gray-200 rounded-lg divide-y divide-gray-100 max-h-72 overflow-y-auto">
            <div v-for="gruppe in gruppen" :key="gruppe.label" v-show="gruppe.mitglieder.length > 0">
              <div class="flex items-center justify-between bg-gray-50 px-3 py-1.5 text-xs font-bold text-gray-600">
                <label class="flex items-center gap-2 cursor-pointer">
                  <input type="checkbox" :checked="istGruppeVollstaendigAusgewaehlt(gruppe)" @change="toggleGruppe(gruppe)"/>
                  {{ gruppe.label }} ({{ gruppe.mitglieder.length }})
                </label>
              </div>
              <label v-for="m in gruppe.mitglieder" :key="m.id"
                     class="flex items-center gap-2 px-3 py-1.5 text-xs hover:bg-gray-50 cursor-pointer">
                <input type="checkbox" :value="m.id" v-model="selectedIds"/>
                {{ m.firstName }} {{ m.lastName }} <span class="text-gray-400">({{ m.email }})</span>
              </label>
            </div>
            <p v-if="gruppen.every(g => g.mitglieder.length === 0)" class="text-xs text-gray-500 p-3">Keine Nutzer gefunden.</p>
          </div>
        </div>

        <div>
          <label class="block text-xs font-bold text-gray-500 uppercase tracking-wider mb-1">Titel</label>
          <input v-model="titel" class="input-field w-full" maxlength="255"/>
        </div>

        <div>
          <label class="block text-xs font-bold text-gray-500 uppercase tracking-wider mb-1">Nachricht</label>
          <textarea v-model="inhalt" rows="5" class="input-field w-full"></textarea>
        </div>

        <button @click="senden" :disabled="!kannSenden || sending" class="btn-primary flex items-center gap-2 text-xs py-1.5 px-3">
          <SendIcon class="w-3.5 h-3.5"/>
          {{ sending ? 'Wird gesendet...' : `An ${selectedIds.length} Empfänger senden` }}
        </button>
      </div>
    </div>
  </section>
</template>

<script setup>
import { ref, computed } from 'vue';
import { Send as SendIcon } from '@lucide/vue';
import { useEventContextStore } from '../../../stores/eventContext';
import api from '../../../api/axios';

const props = defineProps({
  users: { type: Array, required: true },
});

const eventContext = useEventContextStore();

const filter = ref('');
const selectedIds = ref([]);
const titel = ref('');
const inhalt = ref('');
const sending = ref(false);
const error = ref('');
const success = ref('');

const mitgliederDieserVeranstaltung = computed(() => {
  const vid = eventContext.selectedEvent?.id;
  const suchbegriff = filter.value.trim().toLowerCase();
  return props.users
    .filter(u => u.veranstaltungIds?.includes(vid))
    .filter(u => !suchbegriff
      || `${u.firstName} ${u.lastName} ${u.email}`.toLowerCase().includes(suchbegriff));
});

const gruppen = computed(() => [
  { label: 'Organisatoren', rolle: 'ORGANISATOR', mitglieder: mitgliederDieserVeranstaltung.value.filter(u => u.role === 'ORGANISATOR' || u.role === 'ADMINISTRATOR') },
  { label: 'Teilnehmer', rolle: 'TEILNEHMER', mitglieder: mitgliederDieserVeranstaltung.value.filter(u => u.role === 'TEILNEHMER') },
  { label: 'Referenten', rolle: 'REFERENT', mitglieder: mitgliederDieserVeranstaltung.value.filter(u => u.role === 'REFERENT') },
]);

const kannSenden = computed(() => selectedIds.value.length > 0 && titel.value.trim() !== '' && inhalt.value.trim() !== '');

const istGruppeVollstaendigAusgewaehlt = (gruppe) => gruppe.mitglieder.length > 0 && gruppe.mitglieder.every(m => selectedIds.value.includes(m.id));

const toggleGruppe = (gruppe) => {
  const gruppenIds = gruppe.mitglieder.map(m => m.id);
  if (istGruppeVollstaendigAusgewaehlt(gruppe)) {
    selectedIds.value = selectedIds.value.filter(id => !gruppenIds.includes(id));
  } else {
    const neueIds = gruppenIds.filter(id => !selectedIds.value.includes(id));
    selectedIds.value.push(...neueIds);
  }
};

const senden = async () => {
  if (!kannSenden.value) return;
  if (!confirm(`Nachricht an ${selectedIds.value.length} Empfänger senden?`)) return;

  const vid = eventContext.selectedEvent?.id;
  sending.value = true;
  error.value = '';
  success.value = '';
  try {
    await api.post(`/api/veranstaltungen/${vid}/nachrichten`, {
      empfaengerIds: selectedIds.value,
      titel: titel.value,
      inhalt: inhalt.value,
    });
    success.value = 'Nachricht wurde gesendet.';
    selectedIds.value = [];
    titel.value = '';
    inhalt.value = '';
  } catch (e) {
    error.value = 'Senden fehlgeschlagen: ' + (e.response?.data?.error || e.message);
  } finally {
    sending.value = false;
  }
};
</script>

<style scoped>
@reference "tailwindcss";

.input-field { @apply rounded-lg border border-gray-300 px-3 py-2 text-sm text-gray-900 focus:ring-2 focus:ring-indigo-500 bg-white; }
.btn-primary { @apply rounded-lg bg-indigo-600 px-3 py-1.5 text-white font-bold hover:bg-indigo-700 transition shadow-sm border-none cursor-pointer disabled:opacity-50; }
.animate-fade-in { animation: fadeIn 0.3s ease-in-out; }
@keyframes fadeIn { from { opacity: 0; transform: translateY(5px); } to { opacity: 1; transform: translateY(0); } }
</style>
