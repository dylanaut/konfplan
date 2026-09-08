<template>
  <div v-if="isVisible" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4">
    <div class="w-full max-w-xl rounded-xl bg-white p-6 shadow-2xl overflow-y-auto max-h-[90vh]">
      <div class="flex items-center justify-between mb-4">
        <h2 class="text-xl font-bold text-gray-900">Berichte ansehen</h2>
        <button class="text-gray-500 hover:text-gray-700" @click="$emit('close')">✕</button>
      </div>

      <p class="text-xs text-gray-500 mb-4">
        Öffnet die Berichte für genau dieses Planungsergebnis (unabhängig davon, ob es
        veröffentlicht ist) in einem neuen Tab.
      </p>

      <div class="grid grid-cols-1 gap-2 text-xs">
        <button v-for="report in reports" :key="report.routeName"
                @click="oeffnen(report.routeName)"
                class="flex items-center justify-between p-3 bg-gray-50 rounded-lg hover:bg-gray-100 transition text-left">
          <span class="font-semibold">{{ report.label }}</span>
          <span class="text-indigo-600">Öffnen →</span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router';

const props = defineProps({
  isVisible: { type: Boolean, required: true },
  vid: { type: [Number, String], default: null },
  ergebnisId: { type: [Number, String], default: null },
});

defineEmits(['close']);

const router = useRouter();

const reports = [
  { routeName: 'Stundenplan', label: 'Stundenplan' },
  { routeName: 'Prioritaeten', label: 'Prioritäten Auswertung' },
  { routeName: 'TeilnehmerZuordnungen', label: 'Teilnehmer-Zuordnungen' },
  { routeName: 'Raumschilder', label: 'Raumschilder' },
  { routeName: 'Anwesenheiten', label: 'Anwesenheiten' },
  { routeName: 'LaufzettelAlle', label: 'Laufzettel für Teilnehmer' },
  { routeName: 'LaufzettelAlleReferenten', label: 'Laufzettel für Referenten' },
  { routeName: 'FreieSlotsReferenten', label: 'Freie Slots (Referenten)' },
  { routeName: 'FreieSlotsTeilnehmer', label: 'Freie Slots (Teilnehmer)' },
];

const oeffnen = (routeName) => {
  if (!props.vid) return;
  const route = router.resolve({
    name: routeName,
    params: { vid: props.vid },
    query: props.ergebnisId ? { ergebnisId: props.ergebnisId } : {},
  });
  window.open(route.href, '_blank');
};
</script>
