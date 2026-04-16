<template>
  <div class="min-h-screen bg-gray-50" id="app">
    <!-- Navigation -->
    <nav v-if="auth.isAuthenticated" class="bg-indigo-600 text-white p-4 shadow-lg sticky top-0 z-50">
      <div class="container mx-auto flex justify-between items-center">
        <div class="flex items-center gap-4 overflow-hidden">
          <h1 class="font-bold text-xl whitespace-nowrap">Vortragsmanager</h1>

          <!-- EVENT CONTEXT UI -->
          <div v-if="eventContext.selectedEvent" class="flex items-center gap-3 border-l border-indigo-500 pl-4 animate-fade-in overflow-hidden">
            <a v-if="eventContext.selectedEvent.logoLink" :href="eventContext.selectedEvent.logoLink" target="_blank" rel="noopener noreferrer" class="shrink-0 hover:opacity-80 transition-opacity">
              <img v-if="eventContext.selectedEvent.logoUrl" :src="eventContext.selectedEvent.logoUrl" class="h-8 w-auto object-contain bg-white/10 rounded p-1" :alt="eventContext.selectedEvent.name" />
            </a>
            <div class="flex flex-col min-w-0">
              <span class="font-black text-xs uppercase tracking-tighter truncate">{{ eventContext.selectedEvent.name }}</span>
              <span class="text-[10px] text-indigo-200 font-bold whitespace-nowrap">{{ formatDate(eventContext.selectedEvent.beginntAm) }}</span>
            </div>
          </div>
        </div>

        <!-- Mobile Menu Toggle -->
        <button @click="mobileMenuOpen = !mobileMenuOpen" class="md:hidden">
          <MenuIcon />
        </button>

        <!-- Desktop Menu -->
        <div class="hidden md:flex gap-6 items-center">
          <router-link v-if="auth.isParticipant" to="/teilnehmer" class="hover:underline text-sm font-bold">Vorträge</router-link>
          <router-link v-if="auth.isSpeaker" to="/referent" class="hover:underline text-sm font-bold">Mein Vorträge</router-link>
          <router-link v-if="auth.isAdmin" to="/admin" class="hover:underline text-sm font-bold">Admin</router-link>
          <button @click="auth.logout()" class="bg-indigo-700 hover:bg-indigo-800 px-3 py-1 rounded text-xs font-black transition-colors uppercase">Logout</button>
        </div>
      </div>

      <!-- Mobile Menu Content -->
      <div v-if="mobileMenuOpen" class="md:hidden mt-4 flex flex-col gap-2 pb-2">
        <router-link v-if="auth.isParticipant" to="/teilnehmer" @click="mobileMenuOpen = false">Vorträge</router-link>
        <router-link v-if="auth.isSpeaker" to="/referent" @click="mobileMenuOpen = false">Meine Vorträge</router-link>
        <router-link v-if="auth.isAdmin" to="/admin" @click="mobileMenuOpen = false">Admin</router-link>
        <button @click="auth.logout()" class="text-left text-red-200">Logout</button>
      </div>
    </nav>

    <!-- Content -->
    <main class="container mx-auto p-4">
      <router-view />
    </main>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import { useAuthStore } from './stores/auth';
import { useEventContextStore } from './stores/eventContext';
import { Menu as MenuIcon } from 'lucide-vue-next';

const auth = useAuthStore();
const eventContext = useEventContextStore();
const mobileMenuOpen = ref(false);

const formatDate = (d) => d ? new Date(d).toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' }) : '';
</script>

<style>
.animate-fade-in {
  animation: fadeIn 0.3s ease-in-out;
}
@keyframes fadeIn {
  from { opacity: 0; transform: translateX(-10px); }
  to { opacity: 1; transform: translateX(0); }
}
</style>
