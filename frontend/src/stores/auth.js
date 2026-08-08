import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import router from '../router';
import api, { cancelAllRequests } from '../api/axios';
import { useToast } from 'vue-toastification';
import { useEventContextStore } from './eventContext';
import keycloak from '../keycloak';

const KNOWN_ROLES = ['ADMIN', 'REFERENT', 'TEILNEHMER'];

export const useAuthStore = defineStore('auth', () => {
    const token = ref(localStorage.getItem('token') || null);
    const userRole = ref(localStorage.getItem('role') || null);
    const toast = useToast();

    const isAuthenticated = computed(() => !!token.value);
    const isAdmin = computed(() => userRole.value === 'ADMIN');
    const isSpeaker = computed(() => userRole.value === 'REFERENT');
    const isParticipant = computed(() => userRole.value === 'TEILNEHMER');

    // Wird nach erfolgreicher Keycloak-Anmeldung (main.js, keycloak.js-Token-Refresh) mit dem
    // rohen Access-Token und dem von keycloak-js bereits dekodierten Payload aufgerufen.
    function setToken(newToken, parsed) {
        token.value = newToken;
        localStorage.setItem('token', newToken);

        const roles = parsed?.realm_access?.roles ?? [];
        const role = roles.find((r) => KNOWN_ROLES.includes(r)) ?? null;
        userRole.value = role;
        if (role) {
            localStorage.setItem('role', role);
        } else {
            localStorage.removeItem('role');
        }
    }

    function login(options) {
        keycloak.login(options);
    }

    function logout({ reason } = {}) {
        // Eine ggf. laufende Planerstellung serverseitig abbrechen, bevor der Token
        // geloescht wird (der Endpoint ist ADMIN-only, danach fehlt die Berechtigung).
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

        // reason: 'inactive' kommt von useInactivityLogout() (siehe App.vue) - eigene Meldung,
        // damit der Nutzer nicht denkt, er hätte sich selbst abgemeldet. Der eigentliche Redirect
        // erfolgt gleich im Anschluss durch keycloak.logout(), daher hier kein router.push mehr.
        if (reason === 'inactive') {
            toast.info('Sitzung wegen Inaktivität automatisch beendet. Bitte erneut anmelden.',
              { timeout: 5000, closeOnClick: true });
        }

        // Nur bei einer echten Keycloak-Session per echtem Redirect abmelden (invalidiert auch
        // das SSO-Cookie bei Keycloak selbst) - ohne eine solche Session (z.B. Token nur lokal
        // gesetzt) gaebe es dort nichts abzumelden, ein Redirect zu Keycloak waere unnoetig.
        if (keycloak.authenticated) {
            keycloak.logout({ redirectUri: window.location.origin + '/' });
        } else {
            router.push('/');
        }
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
