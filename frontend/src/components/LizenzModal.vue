<template>
  <div v-if="isVisible" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4">
    <div class="w-full max-w-lg max-h-[90vh] overflow-y-auto rounded-xl bg-white p-6 shadow-2xl animate-fade-in">
      <div class="flex justify-between items-center mb-4">
        <h2 class="text-xl font-bold text-gray-900">Lizenz</h2>
        <button @click="$emit('close')" class="text-gray-400 hover:text-gray-600" aria-label="Schließen">
          <XIcon class="w-6 h-6" />
        </button>
      </div>

      <p v-if="ladefehler" class="text-sm text-red-600 mb-4">{{ ladefehler }}</p>

      <section v-if="status" class="mb-6" data-testid="lizenz-status">
        <div class="flex items-center gap-2 mb-2">
          <span class="px-2 py-0.5 rounded-full text-xs font-bold" :class="zustandKlasse" data-testid="lizenz-zustand">{{ zustandText }}</span>
        </div>
        <p v-if="status.grund" class="text-sm text-red-700 mb-2">{{ GRUND_TEXTE[status.grund] || status.grund }}</p>
        <p v-if="status.zustand === 'GRACE'" class="text-sm text-amber-800 mb-2" data-testid="lizenz-grace">
          Die Lizenz muss beim Lizenzserver erneuert werden. Bis {{ formatDatum(status.gueltigBis) }} bleibt die Anwendung voll nutzbar.
        </p>
        <dl v-if="status.lizenzId" class="grid grid-cols-3 gap-y-1 text-sm">
          <dt class="text-gray-500">Kunde</dt><dd class="col-span-2 text-gray-900">{{ status.kundenId }}</dd>
          <dt class="text-gray-500">Lizenz-ID</dt><dd class="col-span-2 text-gray-900">{{ status.lizenzId }}</dd>
          <dt class="text-gray-500">Instanzen</dt><dd class="col-span-2 text-gray-900">bis zu {{ status.maxInstanzen }}</dd>
          <dt class="text-gray-500">{{ status.typ === 'ONLINE' ? 'Ohne Prüfung bis' : 'Gültig bis' }}</dt><dd class="col-span-2 text-gray-900">{{ formatDatum(status.gueltigBis) }}</dd>
          <dt v-if="status.naechsteRevalidierung" class="text-gray-500">Nächste Prüfung</dt>
          <dd v-if="status.naechsteRevalidierung" class="col-span-2 text-gray-900">{{ formatDatum(status.naechsteRevalidierung) }}</dd>
          <dt v-if="status.features?.length" class="text-gray-500">Funktionen</dt>
          <dd v-if="status.features?.length" class="col-span-2 text-gray-900">{{ status.features.join(', ') }}</dd>
        </dl>
        <p class="text-[11px] text-gray-500 mt-3">
          Die Schreibsperre ist in dieser Instanz
          <strong>{{ status.durchsetzung ? 'aktiv' : 'nicht aktiv' }}</strong>.
          Sie sperrt Planerstellung, Importe sowie das Anlegen neuer Veranstaltungen und Nutzer, solange keine gültige Lizenz installiert ist.
        </p>
      </section>

      <section v-if="status?.onlineMoeglich" class="mb-6 border-t border-gray-100 pt-4" data-testid="lizenz-online">
        <h3 class="text-sm font-bold text-gray-700 mb-1">Online-Lizenz</h3>
        <template v-if="!status.onlineAktiviert">
          <p class="text-xs text-gray-500 mb-2">Aktiviert diese Instanz mit einem Lizenzschlüssel beim Lizenzserver.</p>
          <input v-model="lizenzschluessel" type="text" placeholder="Lizenzschlüssel" class="input-field w-full mb-2" autocomplete="off" data-testid="lizenz-schluessel" />
          <button @click="aktivieren" :disabled="!lizenzschluessel.trim() || isSubmitting" class="btn-primary text-xs py-1.5" data-testid="lizenz-aktivieren">Online aktivieren</button>
        </template>
        <template v-else>
          <p v-if="status.revalidierungVersuch" class="text-xs text-gray-600 mb-1">Letzte Prüfung: {{ formatZeit(status.revalidierungVersuch) }}</p>
          <p v-if="status.revalidierungFehler" class="text-xs text-red-600 mb-2" data-testid="lizenz-revalidierungsfehler">{{ status.revalidierungFehler }}</p>
          <div class="flex flex-wrap gap-2">
            <button @click="revalidieren" :disabled="isSubmitting" class="btn-secondary text-xs py-1.5" data-testid="lizenz-revalidieren">Jetzt prüfen</button>
            <button @click="entfernen(false)" :disabled="isSubmitting" class="btn-secondary text-xs py-1.5" data-testid="lizenz-entfernen">Lizenz entfernen</button>
            <button v-if="nurLokalAngebot" @click="entfernen(true)" :disabled="isSubmitting" class="btn-danger text-xs py-1.5" data-testid="lizenz-nur-lokal">Nur lokal entfernen</button>
          </div>
          <p v-if="nurLokalAngebot" class="text-[11px] text-gray-500 mt-1">
            Der Lizenzserver war nicht erreichbar. Lokales Entfernen gibt den Aktivierungsplatz beim Lizenzserver nicht frei - das muss dann der Hersteller tun.
          </p>
        </template>
      </section>

      <section v-if="anforderung" class="mb-6 border-t border-gray-100 pt-4">
        <h3 class="text-sm font-bold text-gray-700 mb-1">Lizenz anfordern</h3>
        <p class="text-xs text-gray-500 mb-2">
          Senden Sie die Anforderungsdatei an den Hersteller. Die Lizenz wird an diese Instanz gebunden.
        </p>
        <p class="text-xs text-gray-600 mb-2">Instanz-ID: <code class="select-all" data-testid="lizenz-instanz-id">{{ anforderung.instanzId }}</code></p>
        <button @click="anforderungHerunterladen" class="btn-secondary flex items-center gap-2 text-xs py-1.5">
          <DownloadIcon class="w-3.5 h-3.5" />
          Anforderungsdatei herunterladen
        </button>
      </section>

      <section class="border-t border-gray-100 pt-4">
        <h3 class="text-sm font-bold text-gray-700 mb-1">Lizenz installieren</h3>
        <input type="file" accept=".lic,.txt,.jwt" @change="dateiGewaehlt" class="block w-full text-xs mb-2" data-testid="lizenz-datei" />
        <textarea v-model="lizenzText" rows="4" placeholder="… oder Lizenz hier einfügen" class="input-field w-full font-mono text-xs" data-testid="lizenz-text"></textarea>
        <p v-if="fehler" class="text-xs text-red-600 mt-2" data-testid="lizenz-fehler">{{ fehler }}</p>
        <p v-if="erfolg" class="text-xs text-green-700 mt-2" data-testid="lizenz-erfolg">{{ erfolg }}</p>
      </section>

      <div class="mt-6 flex gap-3">
        <button @click="$emit('close')" class="btn-secondary flex-1">Schließen</button>
        <button @click="installieren" :disabled="!lizenzText.trim() || isSubmitting" class="btn-primary flex-1 flex items-center justify-center gap-2" data-testid="lizenz-installieren">
          <LoaderIcon v-if="isSubmitting" class="w-4 h-4 animate-spin" />
          Installieren
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue';
import { X as XIcon, Download as DownloadIcon, Loader as LoaderIcon } from '@lucide/vue';
import api from '../api/axios';
import { extractErrorMessage } from '../utils/errorMessage';

const props = defineProps({
  isVisible: Boolean
});

const emit = defineEmits(['close', 'installed']);

const GRUND_TEXTE = {
  FORMAT: 'Die Lizenz hat ein ungültiges Format.',
  ALGORITHMUS: 'Die Lizenz verwendet einen nicht unterstützten Signaturalgorithmus.',
  UNBEKANNTER_SCHLUESSEL: 'Die Lizenz wurde mit einem unbekannten Schlüssel signiert.',
  GESPERRTER_SCHLUESSEL: 'Der Signaturschlüssel der Lizenz ist gesperrt.',
  SIGNATUR: 'Die Signatur der Lizenz ist ungültig.',
  AUSSTELLER: 'Die Lizenz stammt nicht vom erwarteten Aussteller.',
  ZIELGRUPPE: 'Die Lizenz ist nicht für KonfPlan ausgestellt.',
  TYP: 'Der Lizenztyp ist hier nicht zulässig.',
  UNVOLLSTAENDIG: 'Der Lizenz fehlen Pflichtangaben.',
  INSTANZ: 'Die Lizenz gehört zu einer anderen Instanz.',
  ABGELAUFEN: 'Die Lizenz ist abgelaufen.',
  NOCH_NICHT_GUELTIG: 'Die Lizenz ist noch nicht gültig.',
  ZEIT: 'Die Systemuhr wurde zurückgestellt - die Lizenz wird erst wieder anerkannt, wenn die Zeit stimmt.'
};

const status = ref(null);
const anforderung = ref(null);
const lizenzText = ref('');
const ladefehler = ref('');
const fehler = ref('');
const erfolg = ref('');
const isSubmitting = ref(false);
const lizenzschluessel = ref('');
const nurLokalAngebot = ref(false);

const zustandText = computed(() => ({
  LIZENZIERT: 'Lizenziert',
  EINGESCHRAENKT: 'Eingeschränkt',
  UNLIZENZIERT: 'Keine Lizenz',
  GRACE: 'Prüfung fällig'
}[status.value?.zustand] || status.value?.zustand));

const zustandKlasse = computed(() => ({
  LIZENZIERT: 'bg-green-100 text-green-800',
  EINGESCHRAENKT: 'bg-red-100 text-red-800',
  UNLIZENZIERT: 'bg-amber-100 text-amber-900',
  GRACE: 'bg-amber-100 text-amber-900'
}[status.value?.zustand] || 'bg-gray-100 text-gray-800'));

const formatZeit = (iso) => iso ? new Date(iso).toLocaleString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' }) : '';

const formatDatum = (iso) => iso ? new Date(iso).toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' }) : '';

const laden = async () => {
  ladefehler.value = '';
  try {
    const [s, a] = await Promise.all([api.get('/api/lizenz'), api.get('/api/lizenz/anforderung')]);
    status.value = s.data;
    anforderung.value = a.data;
  } catch (e) {
    ladefehler.value = extractErrorMessage(e, 'Lizenzstatus konnte nicht geladen werden.');
  }
};

// immediate: das Modal kann schon sichtbar gemountet werden (Link im Lizenz-Banner).
watch(() => props.isVisible, (visible) => {
  if (!visible) return;
  lizenzText.value = '';
  lizenzschluessel.value = '';
  nurLokalAngebot.value = false;
  fehler.value = '';
  erfolg.value = '';
  laden();
}, { immediate: true });

const dateiGewaehlt = async (event) => {
  const file = event.target.files?.[0];
  if (file) lizenzText.value = (await file.text()).trim();
};

const anforderungHerunterladen = () => {
  const blob = new Blob([JSON.stringify(anforderung.value, null, 2)], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `konfplan-lizenzanforderung-${anforderung.value.instanzId}.json`;
  link.click();
  URL.revokeObjectURL(url);
};

// Führt einen Lizenzaufruf aus und übernimmt den gelieferten Status; liefert, ob er gelang.
const aufrufen = async (aufruf, erfolgsText) => {
  isSubmitting.value = true;
  fehler.value = '';
  erfolg.value = '';
  try {
    const res = await aufruf();
    status.value = res.data;
    erfolg.value = erfolgsText;
    emit('installed');
    return true;
  } catch (e) {
    fehler.value = extractErrorMessage(e);
    return false;
  } finally {
    isSubmitting.value = false;
  }
};

const aktivieren = async () => {
  if (await aufrufen(() => api.post('/api/lizenz/aktivierung', { lizenzschluessel: lizenzschluessel.value.trim() }), 'Die Instanz wurde online aktiviert.')) {
    lizenzschluessel.value = '';
  }
};

const revalidieren = () => aufrufen(() => api.post('/api/lizenz/revalidierung'), 'Die Lizenz wurde beim Lizenzserver erneuert.');

const entfernen = async (nurLokal) => {
  const frage = nurLokal
    ? 'Die Lizenz nur lokal entfernen? Der Aktivierungsplatz beim Lizenzserver bleibt belegt.'
    : 'Die Lizenz von dieser Instanz entfernen und den Aktivierungsplatz freigeben?';
  if (!window.confirm(frage)) return;
  const ok = await aufrufen(() => api.delete('/api/lizenz/aktivierung', { params: nurLokal ? { nurLokal: true } : {} }), 'Die Lizenz wurde entfernt.');
  nurLokalAngebot.value = !ok && !nurLokal;
};

const installieren = async () => {
  isSubmitting.value = true;
  fehler.value = '';
  erfolg.value = '';
  try {
    const res = await api.put('/api/lizenz', lizenzText.value.trim(), { headers: { 'Content-Type': 'text/plain' } });
    status.value = res.data;
    lizenzText.value = '';
    erfolg.value = 'Die Lizenz wurde installiert.';
    emit('installed');
  } catch (e) {
    fehler.value = extractErrorMessage(e);
  } finally {
    isSubmitting.value = false;
  }
};
</script>

<style scoped>
@reference "tailwindcss";

.input-field {
  @apply rounded-lg border border-gray-300 px-3 py-2 text-sm text-gray-900 focus:ring-2 focus:ring-indigo-500 bg-white;
}
</style>
