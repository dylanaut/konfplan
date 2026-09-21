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
        <NewsLaufband />
      </template>
    </div>
  </div>
</template>

<script setup>
import { onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore, ROLE_PRIORITY, ROLE_PATHS } from '../stores/auth';
import NewsLaufband from '../components/NewsLaufband.vue';

const router = useRouter();
const authStore = useAuthStore();

onMounted(() => {
  if (!authStore.isAuthenticated) {
    return;
  }

  // Bei einem Reload im selben Tab (activeRole steht noch in sessionStorage, siehe auth.js)
  // direkt dorthin weiterleiten, statt die Prioritätskette erneut anzuwenden - sonst würde ein
  // Administrator, der gerade als Teilnehmer unterwegs ist, bei jedem Reload ungefragt zurück
  // ins Organisator-Dashboard springen.
  if (authStore.activeRole && authStore.userRoles.includes(authStore.activeRole)) {
    router.replace(ROLE_PATHS[authStore.activeRole]);
    return;
  }

  const defaultRole = ROLE_PRIORITY.find((role) => authStore.userRoles.includes(role));
  if (defaultRole) {
    authStore.setActiveRole(defaultRole);
    router.replace(ROLE_PATHS[defaultRole]);
  }
});
</script>

<style scoped>
@reference "tailwindcss";

.btn-primary {
  @apply inline-flex justify-center py-2 px-6 border border-transparent text-sm font-medium rounded-md text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500;
}
</style>
