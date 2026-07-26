<template>
  <div class="min-h-screen flex items-center justify-center bg-gray-50 py-12 px-4 sm:px-6 lg:px-8">
    <div class="max-w-md w-full space-y-8 bg-white p-8 rounded-xl shadow-lg border border-gray-100">

      <!-- Modus: LOGIN -->
      <div v-if="!isForgotMode">
        <div class="text-center">
          <p class="mt-2 text-base text-gray-600 font-medium">KonfPlan - Ihr Konferenzplaner</p>
          <img class="mx-auto h-24 w-auto" src="/logo/konfplan-light.svg" alt="Konfplan Logo"/>
          <h2 class="text-2xl font-extrabold text-gray-900 flex items-center justify-center gap-2">
            Anmeldung/Registrierung
            <span class="cursor-help text-xl text-gray-500"
                  title="Zur Registrierung bitte 'Passwort vergessen?' verwenden">
             &nbsp;&#9432;
            </span>
          </h2>
        </div>

        <form class="mt-8 space-y-6" @submit.prevent="handleLogin">
          <div class="rounded-md shadow-sm space-y-4">
            <input v-model="email" type="email" required placeholder="E-Mail Adresse" class="input-field"/>
            <input v-model="password" type="password" required placeholder="Passwort" class="input-field"/>
          </div>

          <div class="flex items-center justify-between">
            <div class="text-sm">
              <a href="#" @click.prevent="isForgotMode = true"
                 class="font-medium text-indigo-600 hover:text-indigo-500">
                Passwort vergessen?
              </a>
            </div>
          </div>

          <button type="submit" :disabled="loading" class="btn-primary w-full">
            <span v-if="loading">Anmelden...</span>
            <span v-else>Anmelden</span>
          </button>
        </form>
      </div>

      <!-- Modus: PASSWORT VERGESSEN -->
      <div v-else>
        <div class="text-center">
          <img class="mx-auto h-24 w-auto" src="/logo/konfplan-light.svg" alt="Konfplan Logo"/>
          <h2 class="text-2xl font-bold text-gray-900">Passwort zurücksetzen</h2>
          <p class="mt-2 text-sm text-gray-600">
            Geben Sie Ihre E-Mail Adresse ein. Wir senden Ihnen einen Link zum Zurücksetzen.
          </p>
        </div>

        <form class="mt-8 space-y-6" @submit.prevent="handleForgot">
          <input v-model="forgotEmail" type="email" required placeholder="E-Mail Adresse" class="input-field"/>

          <div v-if="forgotSuccess" class="text-green-600 text-sm bg-green-50 p-3 rounded">
            Falls die Adresse registriert ist, erhalten Sie in Kürze eine E-Mail.
          </div>

          <div class="flex flex-col gap-3">
            <button type="submit" :disabled="loading" class="btn-primary w-full">
              Link anfordern
            </button>
            <button type="button" @click="isForgotMode = false" class="text-sm text-gray-500 hover:underline">
              Zurück zum Login
            </button>
          </div>
        </form>
      </div>

    </div>
  </div>
</template>

<script setup>
import {ref} from 'vue';
import {useAuthStore} from '../stores/auth';
import api from '../api/axios';
import { useToast } from 'vue-toastification';

const authStore = useAuthStore();
const toast = useToast();

// UI States
const isForgotMode = ref(false);
const loading = ref(false);
const forgotSuccess = ref(false);

// Form Fields
const email = ref('');
const password = ref('');
const forgotEmail = ref('');

const handleLogin = async () => {
  loading.value = true;
  await authStore.login({ email: email.value, password: password.value });
  loading.value = false;
};

const handleForgot = async () => {
  loading.value = true;
  try {
    await api.post(`/api/auth/forgot-password?email=${forgotEmail.value}`);
    forgotSuccess.value = true;
    toast.success("Email versendet. Bitte prüfen Sie Ihr Postfach.");
  } catch (e) {
    console.error(e);
    toast.error("Emailversand fehlgeschlagen. Bitte versuchen Sie es später erneut.");
  } finally {
    loading.value = false;
  }
};
</script>

<style scoped>
.input-field {
  @apply appearance-none rounded-lg relative block w-full px-3 py-2 border border-gray-300 placeholder-gray-500 text-gray-900 focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm;
}

.btn-primary {
  @apply flex justify-center py-2 px-4 border border-transparent text-sm font-medium rounded-md text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 disabled:opacity-50;
}
</style>
