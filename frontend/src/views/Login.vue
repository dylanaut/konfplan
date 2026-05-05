<template>
  <div class="min-h-screen flex items-center justify-center bg-gray-50 py-12 px-4 sm:px-6 lg:px-8">
    <div class="max-w-md w-full space-y-8 bg-white p-8 rounded-xl shadow-lg border border-gray-100">

      <!-- Modus: LOGIN -->
      <div v-if="!isForgotMode">
        <div class="text-center">
          <h2 class="text-3xl font-extrabold text-gray-900 flex items-center justify-center gap-2">
            Anmeldung/Registrierung
            <span class="text-gray-400 hover:text-gray-600 cursor-help"
                  title="Zur Registrierung bitte 'Passwort vergessen?' verwenden">
              &#x1F6C8;
            </span>
          </h2>
          <p class="mt-2 text-sm text-gray-600 font-medium">Vortragsmanager Login</p>
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
            <span v-if="loading">Lädt...</span>
            <span v-else>Anmelden</span>
          </button>
        </form>
      </div>

      <!-- Modus: PASSWORT VERGESSEN -->
      <div v-else>
        <div class="text-center">
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
import {useRouter} from 'vue-router';
import {useAuthStore} from '../stores/auth';
import api from '../api/axios';

const router = useRouter();
const authStore = useAuthStore();

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
  try {
    const res = await api.post('/api/auth/login', {email: email.value, password: password.value});
    // TokenResponse: { token: '...', role: '...' }
    authStore.setToken(res.data.token, res.data.role);

    // Redirect basierend auf Rolle
    if (res.data.role === 'ADMIN') router.push('/admin');
    else if (res.data.role === 'REFERENT') router.push('/referent');
    else router.push('/teilnehmer');  // TEILNEHMER
  } catch (e) {
    alert("Login fehlgeschlagen. Bitte prüfen Sie Ihre Daten.");
  } finally {
    loading.value = false;
  }
};

const handleForgot = async () => {
  loading.value = true;
  try {
    // API-Call an den forgot-password Endpunkt
    await api.post(`/api/auth/forgot-password?email=${forgotEmail.value}`);
    forgotSuccess.value = true;
    // Aus Sicherheitsgründen zeigen wir immer Erfolg an (Email Enumeration verhindern)
  } catch (e) {
    console.error(e);
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