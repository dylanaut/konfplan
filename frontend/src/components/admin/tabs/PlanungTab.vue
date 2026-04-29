<template>
  <section class="space-y-4 animate-fade-in">
    <div
        class="bg-indigo-900 text-white p-6 rounded-2xl shadow-xl flex flex-col md:flex-row items-center justify-between gap-6">
      <div class="space-y-3 flex-1">
        <h2 class="text-2xl font-black">Planung & Optimierung</h2>
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-3 bg-white/10 p-3 rounded-xl border border-white/10">
          <div>
            <label class="block text-[9px] uppercase font-bold text-indigo-300 mb-0.5">MiniZinc Solver</label>
            <select v-model="solverConfig.solver"
                    class="w-full bg-indigo-800 border-none rounded text-xs text-white focus:ring-2 focus:ring-green-400 py-1">
              <option value="cp-sat">Google OR-Tools</option>
              <option value="Gecode">Gecode</option>
              <option value="coinbc">COIN-BC</option>
            </select>
          </div>
          <div>
            <label class="block text-[9px] uppercase font-bold text-indigo-300 mb-0.5">Timeout (Sek.)</label>
            <input v-model.number="solverConfig.timeout" type="number"
                   class="w-full bg-indigo-800 border-none rounded text-xs text-white focus:ring-2 focus:ring-green-400 py-1 px-2"/>
          </div>
        </div>
      </div>
      <button @click="emit('startOptimization', solverConfig)" :disabled="isOptimizing"
              class="bg-green-500 hover:bg-green-400 disabled:bg-gray-600 text-white px-8 py-4 rounded-xl font-black text-lg shadow-2xl transition-all transform hover:scale-105 flex items-center gap-3">
        <ZapIcon v-if="!isOptimizing" class="w-5 h-5"/>
        <LoaderIcon v-else class="animate-spin w-5 h-5"/>
        {{ isOptimizing ? 'Optimierung...' : 'Optimieren' }}
      </button>
    </div>
  </section>
</template>

<script setup>
import { reactive } from 'vue';
import {
  Loader as LoaderIcon,
  Zap as ZapIcon
} from 'lucide-vue-next';

const props = defineProps({
  isOptimizing: Boolean
});

const emit = defineEmits(['startOptimization']);

const solverConfig = reactive({solver: 'OR-tools', timeout: 120});
</script>

<style scoped>
.animate-fade-in { animation: fadeIn 0.3s ease-in-out; }
@keyframes fadeIn { from { opacity: 0; transform: translateY(5px); } to { opacity: 1; transform: translateY(0); } }
</style>
