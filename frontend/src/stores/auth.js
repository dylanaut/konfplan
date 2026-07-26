import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import router from '../router';
import api from '../api/axios';
import { useToast } from 'vue-toastification';
import { useEventContextStore } from './eventContext';
import jwtDecode from 'jwt-decode';

export const useAuthStore = defineStore('auth', () => {
    const token = ref(localStorage.getItem('token') || null);
    const userRole = ref(localStorage.getItem('role') || null);
    const toast = useToast();

    const isAuthenticated = computed(() => !!token.value);
    const isAdmin = computed(() => userRole.value === 'ADMIN');
    const isSpeaker = computed(() => userRole.value === 'REFERENT');
    const isParticipant = computed(() => userRole.value === 'TEILNEHMER');

    function setToken(newToken) {
        token.value = newToken;
        localStorage.setItem('token', newToken);

        try {
            const decoded = jwtDecode(newToken);
            const role = decoded.groups[0] || null;
            userRole.value = role;
            localStorage.setItem('role', role);
        } catch (error) {
            console.error("Fehler beim Dekodieren des Tokens:", error);
            logout();
        }
    }

    function handleLoginSuccess(response) {
        const newToken = response.data.token;
        setToken(newToken);
        toast.success('Login erfolgreich!');

        // Redirect based on role
        if (isAdmin.value) {
            router.push('/admin');
        } else if (isSpeaker.value) {
            router.push('/referent');
        } else if (isParticipant.value) {
            router.push('/teilnehmer');
        } else {
            router.push('/'); // Fallback
        }
    }

    async function login(credentials) {
        try {
            handleLoginSuccess(await api.post('/api/auth/login', credentials));
        } catch (error) {
            // Kein error.response => Netzwerk-/Verbindungsfehler (z.B. Backend im Dev-Mode
            // noch nicht bereit). Ein automatischer Retry fängt das übliche Startup-Race ab.
            if (!error.response) {
                try {
                    await new Promise((resolve) => setTimeout(resolve, 1500));
                    handleLoginSuccess(await api.post('/api/auth/login', credentials));
                    return;
                } catch (retryError) {
                    if (!retryError.response) {
                        toast.error('Server nicht erreichbar. Startet das Backend gerade? Bitte in wenigen Sekunden erneut versuchen.');
                        console.error(retryError);
                        return;
                    }
                    error = retryError;
                }
            }

            if (error.response?.status === 401) {
                toast.error('Login fehlgeschlagen. Bitte überprüfen Sie Ihre Anmeldedaten.');
            } else {
                const errorMessage = error.response?.data?.message || 'Login fehlgeschlagen. Bitte versuchen Sie es erneut.';
                toast.error(errorMessage);
            }
            console.error(error);
        }
    }

    function logout() {
        token.value = null;
        userRole.value = null;
        localStorage.removeItem('token');
        localStorage.removeItem('role');

        const eventContext = useEventContextStore();
        eventContext.clearEvent();

        toast.info('Sie haben sich erfolgreich abgemeldet.',
          { timeout: 3000, closeOnClick: true});
        router.push('/login');
    }

    return {
        token,
        userRole,
        isAuthenticated,
        isAdmin,
        isSpeaker,
        isParticipant,
        login,
        logout,
        setToken
    };
});
