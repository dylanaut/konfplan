<template>
  <div class="min-h-screen flex items-center justify-center bg-gray-50">
    <div class="text-center space-y-4">
      <img class="mx-auto h-24 w-auto" src="/logo/konfplan-light.svg" alt="Konfplan Logo" />
      <template v-if="authStore.isAuthenticated">
        <p class="text-gray-600">Weiterleitung läuft...</p>
      </template>
      <template v-else>
        <p class="text-gray-600">KonfPlan - Ihr Konferenzplaner</p>
        <button @click="authStore.login()" class="btn-primary">Anmelden</button>
      </template>
    </div>
  </div>
</template>

<script setup>
import { onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '../stores/auth';

const router = useRouter();
const authStore = useAuthStore();

onMounted(() => {
  if (authStore.isAdmin) {
    router.replace('/admin');
  } else if (authStore.isSpeaker) {
    router.replace('/referent');
  } else if (authStore.isParticipant) {
    router.replace('/teilnehmer');
  }
});
</script>

<style scoped>
.btn-primary {
  @apply inline-flex justify-center py-2 px-6 border border-transparent text-sm font-medium rounded-md text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500;
}
</style>
