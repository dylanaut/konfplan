<template>
  <section class="space-y-4 animate-fade-in">
    <div class="grid grid-cols-1 md:grid-cols-4 gap-4">
      <div class="bg-white p-3 rounded-xl shadow-sm border border-gray-100">
        <div class="text-[9px] text-gray-500 uppercase font-bold">Ø Priorität</div>
        <div class="text-xl font-black text-indigo-600">{{ qualitaet.durchschnittsPrio?.toFixed(2) || '0.00' }}</div>
      </div>
    </div>
    <!-- Belegungsplan Tabelle -->
    <div class="bg-white shadow rounded-xl overflow-hidden border border-gray-100">
      <table class="min-w-full divide-y divide-gray-200">
        <thead class="bg-gray-50 text-[9px] uppercase font-bold text-gray-500">
        <tr>
          <th class="px-4 py-1.5 text-left font-bold">Vortrag</th>
          <th class="px-4 py-1.5 text-left font-bold">Zeit/Raum</th>
          <th class="px-4 py-1.5 text-center font-bold">Belegung</th>
          <th class="px-4 py-1.5 text-left font-bold">Teilnehmer</th>
        </tr>
        </thead>
        <tbody class="divide-y divide-gray-100 text-xs">
        <tr v-for="b in belegungsPlan" :key="b.vortragTitel + b.slotZeit" class="hover:bg-gray-50 transition">
          <td class="px-4 py-2 font-bold">{{ b.vortragTitel }}</td>
          <td class="px-4 py-2">{{ b.slotZeit }} | {{ b.raumName }}</td>
          <td class="px-4 py-2 text-center">{{ b.teilnehmerNamen.length }} / {{ b.kapazitaet }}</td>
          <td class="px-4 py-2 text-[10px] text-gray-500">{{ b.teilnehmerNamen.join(', ') }}</td>
        </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<script setup>
defineProps({
  belegungsPlan: {
    type: Array,
    required: true
  },
  qualitaet: {
    type: Object,
    required: true
  }
});
</script>

<style scoped>
.animate-fade-in { animation: fadeIn 0.3s ease-in-out; }
@keyframes fadeIn { from { opacity: 0; transform: translateY(5px); } to { opacity: 1; transform: translateY(0); } }
</style>
