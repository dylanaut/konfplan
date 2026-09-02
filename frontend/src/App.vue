<template>
  <div class="min-h-screen bg-gray-50" id="app">
    <MaintenanceBanner v-if="auth.isAuthenticated" />
    <VersionUpdateBanner v-if="auth.isAuthenticated" />
    <!-- Navigation -->
    <nav v-if="auth.isAuthenticated" class="bg-indigo-600 text-white p-4 shadow-lg sticky top-0 z-50 no-print">
      <div class="container mx-auto flex justify-between items-center">
        <div class="flex items-center gap-4 overflow-hidden">
          <h1 class="font-bold text-xl whitespace-nowrap">KonfPlan</h1>

          <!-- EVENT CONTEXT UI -->
          <div v-if="eventContext.selectedEvent" class="flex items-center gap-3 border-l border-indigo-500 pl-4 animate-fade-in overflow-hidden">
            <a v-if="eventContext.selectedEvent.logo_link" :href="eventContext.selectedEvent.logo_link" target="_blank" rel="noopener noreferrer" class="shrink-0 hover:opacity-80 transition-opacity">
              <EventLogo :src="eventContext.selectedEvent.logo" :alt="eventContext.selectedEvent.name" img-class="h-8 w-auto object-contain bg-white/10 rounded p-1" />
            </a>
            <div class="flex flex-col min-w-0">
              <span class="font-black text-xs uppercase tracking-tighter truncate">{{ eventContext.selectedEvent.name }}</span>
              <span class="text-[10px] text-indigo-200 font-bold whitespace-nowrap">{{ formatZeitraum(eventContext.selectedEvent) }}</span>
              <span v-if="eventContext.selectedEvent.organisatorNamen?.length" class="text-[10px] text-indigo-200 truncate">
                {{ eventContext.selectedEvent.organisatorNamen.join(', ') }}
              </span>
            </div>
          </div>
        </div>

        <!-- Mobile Menu Toggle -->
        <button @click="mobileMenuOpen = !mobileMenuOpen" class="md:hidden">
          <MenuIcon />
        </button>

        <!-- Desktop Menu -->
        <div class="hidden md:flex gap-6 items-center relative">
          <button @click="toggleInfoPanel" class="hover:underline text-sm font-bold">Info</button>
          <button @click="auth.logout()" class="bg-indigo-700 hover:bg-indigo-800 px-3 py-1 rounded text-xs font-black transition-colors uppercase">Logout</button>
          <InfoPanel v-if="infoPanelOpen" class="absolute right-0 top-full mt-2" @close="infoPanelOpen = false" @open-feedback="openFeedbackModal" />
        </div>
      </div>

      <!-- Mobile Menu Content -->
      <div v-if="mobileMenuOpen" class="md:hidden mt-4 flex flex-col gap-2 pb-2 relative">
        <button @click="toggleInfoPanel" class="text-left">Info</button>
        <button @click="auth.logout()" class="text-left text-red-200">Logout</button>
        <InfoPanel v-if="infoPanelOpen" @close="infoPanelOpen = false" @open-feedback="openFeedbackModal" />
      </div>
    </nav>

    <!-- Content -->
    <main class="container mx-auto p-4">
      <router-view />
    </main>

    <FeedbackModal :is-visible="showFeedbackModal" @close="showFeedbackModal = false" />
  </div>
</template>

<script setup>
import { ref } from 'vue';
import { useAuthStore } from './stores/auth';
import { useEventContextStore } from './stores/eventContext';
import { useInactivityLogout } from './composables/useInactivityLogout';
import { Menu as MenuIcon } from '@lucide/vue';
import { formatZeitraum } from './utils/veranstaltungFormat';
import InfoPanel from './components/InfoPanel.vue';
import EventLogo from './components/EventLogo.vue';
import FeedbackModal from './components/FeedbackModal.vue';
import MaintenanceBanner from './components/MaintenanceBanner.vue';
import VersionUpdateBanner from './components/VersionUpdateBanner.vue';

const auth = useAuthStore();
const eventContext = useEventContextStore();
const mobileMenuOpen = ref(false);
const infoPanelOpen = ref(false);
const showFeedbackModal = ref(false);

const toggleInfoPanel = () => {
  infoPanelOpen.value = !infoPanelOpen.value;
};

const openFeedbackModal = () => {
  infoPanelOpen.value = false;
  mobileMenuOpen.value = false;
  showFeedbackModal.value = true;
};

useInactivityLogout();
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
