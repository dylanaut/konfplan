<template>
  <div v-if="isVisible" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4">
    <div class="w-full max-w-md rounded-xl bg-white p-6 shadow-2xl animate-fade-in">
      <div class="flex justify-between items-center mb-4">
        <h2 class="text-xl font-bold text-gray-900">Passwort zurücksetzen</h2>
        <button @click="$emit('close')" class="text-gray-400 hover:text-gray-600">
          <XIcon class="w-6 h-6" />
        </button>
      </div>

      <div v-if="nutzer" class="mb-6">
        <p class="text-sm text-gray-600">
          Neues Passwort für <span class="font-bold text-gray-900">{{ nutzer.firstName }} {{ nutzer.lastName }}</span> ({{ nutzer.loginName }}) setzen.
        </p>
      </div>

      <div class="space-y-4">
        <div>
          <label class="block text-xs font-bold text-gray-500 uppercase tracking-wider mb-1">Neues Passwort</label>
          <div class="relative">
            <input v-model="newPassword" :type="showPassword ? 'text' : 'password'" class="input-field w-full pr-10" placeholder="Min. 8 Zeichen" />
            <button type="button" @click="showPassword = !showPassword"
                    class="absolute right-2 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
                    :aria-label="showPassword ? 'Passwort verbergen' : 'Passwort anzeigen'">
              <EyeOffIcon v-if="showPassword" class="w-4 h-4" />
              <EyeIcon v-else class="w-4 h-4" />
            </button>
          </div>
          <p class="text-[10px] mt-1" :class="newPassword.length === 0 ? 'text-gray-400' : isPasswordCompliant ? 'text-green-600' : 'text-red-600'">
            {{ hinweisText }}
          </p>
        </div>

        <div class="bg-indigo-50 p-3 rounded-lg border border-indigo-100">
          <p class="text-[10px] text-indigo-700 leading-relaxed">
            <InfoIcon class="w-3 h-3 inline mr-1" />
            Das Passwort wird sofort geändert. Bitte teilen Sie es dem Nutzer auf einem sicheren Weg mit.
          </p>
        </div>
      </div>

      <div class="mt-8 flex gap-3">
        <button @click="$emit('close')" class="btn-secondary flex-1">Abbrechen</button>
        <button
          @click="confirmReset"
          :disabled="!isPasswordCompliant || isSubmitting"
          class="btn-primary flex-1 flex items-center justify-center gap-2"
        >
          <LoaderIcon v-if="isSubmitting" class="w-4 h-4 animate-spin" />
          Zurücksetzen
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue';
import { X as XIcon, Info as InfoIcon, Loader as LoaderIcon, Eye as EyeIcon, EyeOff as EyeOffIcon } from '@lucide/vue';
import api from '../api/axios';

const props = defineProps({
  isVisible: Boolean,
  nutzer: Object,
  vid: [Number, String],
});

const emit = defineEmits(['close', 'reset']);

const newPassword = ref('');
const isSubmitting = ref(false);
const showPassword = ref(false);
// Standard-Regel als Default, solange die tatsächlich konfigurierte Richtlinie noch geladen
// wird oder (z.B. ohne vid) nicht ermittelt werden kann - identisch zum Server-seitigen Fallback
// Passwortrichtlinie.STANDARD.
const STANDARD_RICHTLINIE = {
  minLaenge: 8, maxLaenge: null,
  erfordertGrossbuchstabe: true, erfordertKleinbuchstabe: true, erfordertZiffer: true, erfordertSonderzeichen: true,
  nurZiffern: false,
};
const richtlinie = ref(STANDARD_RICHTLINIE);

const isPasswordCompliant = computed(() => {
  const pw = newPassword.value;
  const r = richtlinie.value;
  if (pw.length < r.minLaenge) return false;
  if (r.maxLaenge && pw.length > r.maxLaenge) return false;
  if (r.nurZiffern) return /^\d+$/.test(pw);
  return (!r.erfordertGrossbuchstabe || /[A-Z]/.test(pw))
    && (!r.erfordertKleinbuchstabe || /[a-z]/.test(pw))
    && (!r.erfordertZiffer || /[0-9]/.test(pw))
    && (!r.erfordertSonderzeichen || /[^A-Za-z0-9]/.test(pw));
});

const hinweisText = computed(() => {
  const r = richtlinie.value;
  if (r.nurZiffern) {
    return r.maxLaenge && r.maxLaenge !== r.minLaenge
      ? `Nur Ziffern, ${r.minLaenge}-${r.maxLaenge} Zeichen.`
      : `Nur Ziffern, genau ${r.minLaenge} Zeichen.`;
  }
  const teile = [];
  if (r.erfordertGrossbuchstabe) teile.push('ein Großbuchstabe');
  if (r.erfordertKleinbuchstabe) teile.push('ein Kleinbuchstabe');
  if (r.erfordertZiffer) teile.push('eine Ziffer');
  if (r.erfordertSonderzeichen) teile.push('ein Sonderzeichen');
  return `Mind. ${r.minLaenge} Zeichen` + (teile.length ? `, je mind. ${teile.join(', ')}.` : '.');
});

watch(() => props.isVisible, async (visible) => {
  if (!visible) return;
  newPassword.value = '';
  showPassword.value = false;
  richtlinie.value = STANDARD_RICHTLINIE;

  if (!props.vid || !props.nutzer?.role) return;
  try {
    const response = await api.get(`/api/organisator/veranstaltungen/${props.vid}/passwortrichtlinien`);
    const gefunden = response.data.find(r => r.rolle === props.nutzer.role);
    if (gefunden) {
      richtlinie.value = gefunden;
    }
  } catch (e) {
    console.error('Passwortrichtlinie konnte nicht geladen werden, verwende Standard.', e);
  }
});

const confirmReset = async () => {
  if (!isPasswordCompliant.value) return;
  isSubmitting.value = true;
  try {
    await emit('reset', { userId: props.nutzer.id, newPassword: newPassword.value });
    newPassword.value = '';
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
.btn-primary {
  @apply rounded-lg bg-indigo-600 px-4 py-2 text-white font-bold hover:bg-indigo-700 transition shadow-sm disabled:opacity-50;
}
.btn-secondary {
  @apply bg-white text-gray-700 px-4 py-2 rounded-lg hover:bg-gray-50 font-bold border border-gray-200 transition shadow-sm;
}
.animate-fade-in { animation: fadeIn 0.2s ease-out; }
@keyframes fadeIn { from { opacity: 0; transform: scale(0.95); } to { opacity: 1; transform: scale(1); } }
</style>
