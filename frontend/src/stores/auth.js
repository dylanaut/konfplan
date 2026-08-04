import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import router from '../router';
import api, { cancelAllRequests } from '../api/axios';
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

    // Direkt nach dem Start von 'mvnw quarkus:dev' gibt es ein knappes Zeitfenster, in dem der
    // allererste Login-Request an einem Verbindungsfehler scheitert, weil Backend/Vite-Proxy
    // noch nicht bereit sind, obwohl die Zugangsdaten korrekt sind. Statt sofort eine
    // Fehlermeldung zu zeigen, wird deshalb mehrfach mit kurzer Pause automatisch erneut
    // versucht, bevor der letzte Fehler tatsächlich angezeigt wird.
    const LOGIN_RETRY_ATTEMPTS = 3;
    const LOGIN_RETRY_DELAY_MS = 1200;

    async function login(credentials) {
        let lastError;

        for (let attempt = 1; attempt <= LOGIN_RETRY_ATTEMPTS; attempt++) {
            try {
                handleLoginSuccess(await api.post('/api/auth/login', credentials));
                return;
            } catch (error) {
                lastError = error;
                // Nur bei einem Verbindungsfehler (kein response, s.o.) erneut versuchen. Ein
                // tatsächliches 401 (falsche Zugangsdaten) oder 429 (Login-Rate-Limit, siehe
                // LoginRateLimiterService) ist eine deterministische Antwort eines erreichbaren
                // Servers - ein erneuter Versuch würde nichts bringen und zusätzlich unnötig
                // gegen das Rate-Limit zählen.
                if (error.response) {
                    break;
                }
                if (attempt < LOGIN_RETRY_ATTEMPTS) {
                    await new Promise((resolve) => setTimeout(resolve, LOGIN_RETRY_DELAY_MS));
                }
            }
        }

        if (!lastError.response) {
            toast.error('Server nicht erreichbar. Startet das Backend gerade? Bitte in wenigen Sekunden erneut versuchen.');
        } else if (lastError.response.status === 401) {
            toast.error('Login fehlgeschlagen. Bitte überprüfen Sie Ihre Anmeldedaten.');
        } else if (lastError.response.status === 429) {
            const retryAfterSeconds = Number(lastError.response.headers?.['retry-after']);
            const wartezeit = Number.isFinite(retryAfterSeconds) && retryAfterSeconds > 0
                ? `${Math.ceil(retryAfterSeconds / 60)} Minute(n)`
                : 'einigen Minuten';
            toast.error(`Zu viele fehlgeschlagene Login-Versuche. Bitte in ${wartezeit} erneut versuchen.`);
        } else {
            const errorMessage = lastError.response?.data?.message || 'Login fehlgeschlagen. Bitte versuchen Sie es erneut.';
            toast.error(errorMessage);
        }
        console.error(lastError);
    }

    function logout() {
        // Eine ggf. laufende Planerstellung serverseitig abbrechen, bevor der Token
        // gelöscht wird (der Endpoint ist ADMIN-only, danach fehlt die Berechtigung).
        // Header wird explizit gesetzt statt über den Request-Interceptor, da localStorage
        // gleich im Anschluss geleert wird und der Interceptor sonst ins Leere liefe.
        if (isAdmin.value && token.value) {
            api.delete('/api/planungen', { headers: { Authorization: `Bearer ${token.value}` } })
                .catch(() => {});
        }
        cancelAllRequests();

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
