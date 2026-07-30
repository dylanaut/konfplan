<template>
  <section class="space-y-6 animate-fade-in">
    <!-- Belegungsplan -->
    <div v-if="belegungsPlan && belegungsPlan.length > 0">
      <Stundenplan :vid="eventContext.selectedEvent.id" />
    </div>
    <div v-else class="text-center text-gray-500 py-12 bg-white rounded-xl shadow-sm border border-gray-100">
      <p class="font-bold">Kein Planungsergebnis vorhanden.</p>
      <p class="text-xs mt-1">Bitte erstellen Sie zuerst einen Plan im Tab "Planerstellung".</p>
    </div>

    <!-- Planqualität -->
    <div v-if="belegungsPlan && belegungsPlan.length > 0" class="bg-white p-4 rounded-xl shadow-sm border border-gray-100">
      <h3 class="text-sm font-bold mb-3">Planqualität</h3>
      <div class="grid grid-cols-2 md:grid-cols-4 gap-4 text-xs">
        <div class="p-3 bg-gray-50 rounded-lg">
          <p class="text-gray-500">Güte</p>
          <p class="font-semibold text-sm">{{ qualitaet.guete }}</p>
        </div>
        <div class="p-3 bg-gray-50 rounded-lg">
          <p class="text-gray-500">Zuweisungen</p>
          <p class="font-semibold text-sm">{{ qualitaet.zuweisungen }}</p>
        </div>
        <div class="p-3 bg-gray-50 rounded-lg">
          <p class="text-gray-500">Raumwechsel</p>
          <p class="font-semibold text-sm">{{ qualitaet.raumwechsel }}</p>
        </div>
        <div class="p-3 bg-gray-50 rounded-lg">
          <p class="text-gray-500">Status</p>
          <p class="font-semibold text-sm">{{ qualitaet.status }}</p>
        </div>
      </div>
    </div>

    <!-- Artefakte -->
    <div v-if="belegungsPlan && belegungsPlan.length > 0" class="bg-white p-4 rounded-xl shadow-sm border border-gray-100">
      <h3 class="text-sm font-bold mb-3">Berichte</h3>
      <div class="grid grid-cols-1 md:grid-cols-2 gap-4 text-xs">
        <div class="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
          <div class="flex items-center space-x-2">
            <img src="/logo/konfplan-light_footer.svg" alt="Icon" class="w-5 h-5"/>
            <span class="font-semibold">Prioritäten Auswertung</span>
          </div>
          <div class="space-x-2">
            <button @click="navigateToReport('Prioritaeten')" class="px-2 py-1 bg-indigo-500 text-white rounded hover:bg-indigo-600">Anzeigen</button>
          </div>
        </div>
        <div class="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
          <div class="flex items-center space-x-2">
            <img src="/logo/konfplan-light_footer.svg" alt="Icon" class="w-5 h-5"/>
            <span class="font-semibold">Teilnehmer-Zuordnungen</span>
          </div>
          <div class="space-x-2">
            <button @click="navigateToReport('TeilnehmerZuordnungen')" class="px-2 py-1 bg-indigo-500 text-white rounded hover:bg-indigo-600">Anzeigen</button>
          </div>
        </div>
        <div class="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
          <div class="flex items-center space-x-2">
            <img src="/logo/konfplan-light_footer.svg" alt="Icon" class="w-5 h-5"/>
            <span class="font-semibold">Raumschilder</span>
          </div>
          <div class="space-x-2">
            <button @click="navigateToReport('Raumschilder')" class="px-2 py-1 bg-indigo-500 text-white rounded hover:bg-indigo-600">Anzeigen</button>
          </div>
        </div>
        <div class="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
          <div class="flex items-center space-x-2">
            <img src="/logo/konfplan-light_footer.svg" alt="Icon" class="w-5 h-5"/>
            <span class="font-semibold">Laufzettel (Alle)</span>
          </div>
          <div class="space-x-2">
            <button @click="navigateToReport('LaufzettelAlle')" class="px-2 py-1 bg-indigo-500 text-white rounded hover:bg-indigo-600">Anzeigen</button>
          </div>
        </div>
        <div class="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
          <div class="flex items-center space-x-2">
            <img src="/logo/konfplan-light_footer.svg" alt="Icon" class="w-5 h-5"/>
            <span class="font-semibold">Freie Slots (Referenten)</span>
          </div>
          <div class="space-x-2">
            <button @click="navigateToReport('FreieSlotsReferenten')" class="px-2 py-1 bg-indigo-500 text-white rounded hover:bg-indigo-600">Anzeigen</button>
          </div>
        </div>
        <div class="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
          <div class="flex items-center space-x-2">
            <img src="/logo/konfplan-light_footer.svg" alt="Icon" class="w-5 h-5"/>
            <span class="font-semibold">Freie Slots (Teilnehmer)</span>
          </div>
          <div class="space-x-2">
            <button @click="navigateToReport('FreieSlotsTeilnehmer')" class="px-2 py-1 bg-indigo-500 text-white rounded hover:bg-indigo-600">Anzeigen</button>
          </div>
        </div>
      </div>
    </div>

  </section>
</template>

<script setup>
import { useRouter } from 'vue-router';
import { useEventContextStore } from '../../../stores/eventContext';
import Stundenplan from '../../../views/report/Stundenplan.vue';

const props = defineProps({
  belegungsPlan: {type: Array, required: true},
  qualitaet: {type: Object, required: true},
});

const router = useRouter();
const eventContext = useEventContextStore();

const navigateToReport = (routeName) => {
  const vid = eventContext.selectedEvent.id;
  if (vid) {
    const route = router.resolve({ name: routeName, params: { vid } });
    window.open(route.href, '_blank');
  }
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
</style>
