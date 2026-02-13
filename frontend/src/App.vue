<template>
  <div class="min-h-screen bg-gray-50" id="app">
    <!-- Navigation -->
    <nav v-if="auth.isAuthenticated" class="bg-indigo-600 text-white p-4 shadow-lg">
      <div class="container mx-auto flex justify-between items-center">
        <h1 class="font-bold text-xl">Event Planner</h1>

        <!-- Mobile Menu Toggle -->
        <button @click="mobileMenuOpen = !mobileMenuOpen" class="md:hidden">
          <MenuIcon />
        </button>

        <!-- Desktop Menu -->
        <div class="hidden md:flex gap-6">
          <router-link v-if="auth.isParticipant" to="/participant" class="hover:underline">Vorträge</router-link>
          <router-link v-if="auth.isSpeaker" to="/speaker" class="hover:underline">Mein Vortrag</router-link>
          <router-link v-if="auth.isAdmin" to="/admin" class="hover:underline">Admin</router-link>
          <button @click="auth.logout()" class="bg-indigo-700 px-3 py-1 rounded">Logout</button>
        </div>
      </div>

      <!-- Mobile Menu Content -->
      <div v-if="mobileMenuOpen" class="md:hidden mt-4 flex flex-col gap-2 pb-2">
        <router-link to="/participant" @click="mobileMenuOpen = false">Vorträge</router-link>
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
import { Menu as MenuIcon } from 'lucide-vue-next';

const auth = useAuthStore();
const mobileMenuOpen = ref(false);
</script>