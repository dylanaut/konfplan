<template>
  <div class="min-h-screen flex items-center justify-center bg-gray-50 py-12 px-4 sm:px-6 lg:px-8">
    <div class="max-w-md w-full space-y-8 bg-white p-8 rounded-xl shadow-lg border border-gray-100">

      <div class="text-center">
        <h2 class="mt-6 text-3xl font-extrabold text-gray-900">
          E-Mail-Adresse bestätigen
        </h2>
      </div>

      <div v-if="status === 'loading'" class="text-center text-sm text-gray-600">
        Bestätigung wird verarbeitet...
      </div>

      <div v-if="status === 'success'"
           class="bg-green-100 border border-green-400 text-green-700 px-4 py-3 rounded relative">
        <span class="block sm:inline">Ihre E-Mail-Adresse wurde erfolgreich geändert. Sie werden gleich zum Login weitergeleitet...</span>
      </div>

      <div v-if="status === 'error'" class="bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded relative">
        <span class="block sm:inline">{{ errorMessage }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import api from '../api/axios';

const route = useRoute();
const router = useRouter();

const status = ref('loading'); // loading, success, error
const errorMessage = ref('');

onMounted(async () => {
  const token = route.query.token;
  if (!token) {
    status.value = 'error';
    errorMessage.value = 'Kein gültiger Bestätigungstoken gefunden. Bitte fordern Sie die E-Mail-Änderung erneut an.';
    return;
  }

  try {
    await api.get('/api/teilnehmer/email-change-confirm', { params: { token } });
    status.value = 'success';
    setTimeout(() => {
      router.push('/login');
    }, 3000);
  } catch (error) {
    status.value = 'error';
    errorMessage.value = error.response?.data || 'Der Link ist abgelaufen oder ungültig.';
  }
});
</script>
