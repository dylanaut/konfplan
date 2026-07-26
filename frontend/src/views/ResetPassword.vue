<template>
  <div class="min-h-screen flex items-center justify-center bg-gray-50 py-12 px-4 sm:px-6 lg:px-8">
    <div class="max-w-md w-full space-y-8 bg-white p-8 rounded-xl shadow-lg border border-gray-100">

      <!-- Header -->
      <div class="text-center">
        <h2 class="mt-6 text-3xl font-extrabold text-gray-900">
          Neues Passwort festlegen
        </h2>
        <p class="mt-2 text-sm text-gray-600">
          Geben Sie bitte Ihr neues Passwort ein.
        </p>
      </div>

      <!-- Feedback Messages -->
      <div v-if="status === 'success'"
           class="bg-green-100 border border-green-400 text-green-700 px-4 py-3 rounded relative">
        <span class="block sm:inline">Ihr Passwort wurde erfolgreich geändert. Sie werden gleich zum Login weitergeleitet...</span>
      </div>

      <div v-if="status === 'error'" class="bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded relative">
        <span class="block sm:inline">{{ errorMessage }}</span>
      </div>

      <!-- Form -->
      <form v-if="status !== 'success'" class="mt-8 space-y-6" @submit.prevent="handleSubmit">
        <div class="rounded-md shadow-sm space-y-4">
          <div>
            <label for="password" class="block text-sm font-medium text-gray-700">Neues Passwort</label>
            <input
                id="password"
                v-model="password"
                type="password"
                required
                class="appearance-none rounded-lg relative block w-full px-3 py-2 border border-gray-300 placeholder-gray-500 text-gray-900 focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm"
                placeholder="Min. 8 Zeichen"
            />
          </div>
          <div>
            <label for="confirmPassword" class="block text-sm font-medium text-gray-700">Passwort bestätigen</label>
            <input
                id="confirmPassword"
                v-model="confirmPassword"
                type="password"
                required
                class="appearance-none rounded-lg relative block w-full px-3 py-2 border border-gray-300 placeholder-gray-500 text-gray-900 focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm"
                placeholder="Passwort erneut eingeben"
            />
          </div>
        </div>

        <div>
          <button
              type="submit"
              :disabled="isLoading"
              class="group relative w-full flex justify-center py-2 px-4 border border-transparent text-sm font-medium rounded-md text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 disabled:opacity-50"
          >
            <span v-if="isLoading">Verarbeite...</span>
            <span v-else>Passwort speichern</span>
          </button>
        </div>
      </form>
    </div>
  </div>
</template>

<script setup>
import {onMounted, ref} from 'vue';
import {useRoute, useRouter} from 'vue-router';
import api from '../api/axios';

const route = useRoute();
const router = useRouter();

const password = ref('');
const confirmPassword = ref('');
const token = ref('');
const status = ref('idle'); // idle, loading, success, error
const errorMessage = ref('');
const isLoading = ref(false);

onMounted(() => {
  // Extrahiert den Token aus der URL: ?token=...
  const queryToken = route.query.token;
  if (!queryToken) {
    status.value = 'error';
    errorMessage.value = 'Kein gültiger Sicherheitstoken gefunden. Bitte fordern Sie einen neuen Link an.';
  } else {
    token.value = queryToken;
  }
});

const handleSubmit = async () => {
  // Validierung im Frontend
  if (password.value.length < 8) {
    status.value = 'error';
    errorMessage.value = 'Das Passwort muss mindestens 8 Zeichen lang sein.';
    return;
  }

  if (password.value !== confirmPassword.value) {
    status.value = 'error';
    errorMessage.value = 'Die Passwörter stimmen nicht überein.';
    return;
  }

  isLoading.value = true;
  status.value = 'loading';

  try {
    // API Call zum Quarkus Backend (ResetRequest DTO)
    await api.post('/api/auth/reset-password', {
      token: token.value,
      newPassword: password.value
    });

    status.value = 'success';

    // Nach 3 Sekunden zum Login weiterleiten
    setTimeout(() => {
      router.push('/login');
    }, 3000);

  } catch (error) {
    status.value = 'error';
    if (error.response && error.response.status === 400) {
      errorMessage.value = 'Der Link ist abgelaufen oder ungültig.';
    } else {
      errorMessage.value = 'Ein Fehler ist aufgetreten. Bitte versuchen Sie es später erneut.';
    }
  } finally {
    isLoading.value = false;
  }
};
</script>