<template>
  <div class="min-h-screen flex items-center justify-center bg-gray-50">
    <div class="text-center space-y-4">
      <img class="mx-auto h-24 w-auto" src="/logo/konfplan-light.svg" alt="Konfplan Logo" />
      <p class="text-gray-600">Weiterleitung läuft...</p>
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
  if (!authStore.isAuthenticated) {
    authStore.login({ redirectUri: window.location.origin });
    return;
  }

  if (authStore.isAdmin) {
    router.replace('/admin');
  } else if (authStore.isSpeaker) {
    router.replace('/referent');
  } else if (authStore.isParticipant) {
    router.replace('/teilnehmer');
  }
});
</script>
