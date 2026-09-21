import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import router from '../router';
import api, { cancelAllRequests } from '../api/axios';
import { useToast } from 'vue-toastification';
import { useEventContextStore } from './eventContext';
import keycloak from '../keycloak';

const KNOWN_ROLES = ['ORGANISATOR', 'ADMINISTRATOR', 'REFERENT', 'TEILNEHMER', 'BETRACHTER'];

// Bestimmt die Standard-Landing-Rolle beim ersten Login (siehe Redirecting.vue) - ADMINISTRATOR
// vor ORGANISATOR ist rein kosmetisch (ein Nutzer haelt als Primaerrolle nie beide gleichzeitig,
// siehe Backend: Administrator extends Organisator), die Reihenfolge sonst analog zur bisherigen
// Prioritaetskette.
export const ROLE_PRIORITY = ['ADMINISTRATOR', 'ORGANISATOR', 'REFERENT', 'TEILNEHMER', 'BETRACHTER'];

// Ziel-Route je Rolle (siehe #751) - ORGANISATOR/ADMINISTRATOR teilen sich dasselbe Dashboard.
export const ROLE_PATHS = {
    ADMINISTRATOR: '/organisator',
    ORGANISATOR: '/organisator',
    REFERENT: '/referent',
    TEILNEHMER: '/teilnehmer',
    BETRACHTER: '/betrachter'
};

// Anzeigename je Rolle (siehe #751) - fuer den Rollen-Dropdown im Seitenkopf (App.vue) und die
// Zusatzrollen-Verwaltung, analog zu UserEditorModal.vue's ROLLEN_NAMEN.
export const ROLE_LABELS = {
    ORGANISATOR: 'Organisator',
    ADMINISTRATOR: 'Administrator',
    REFERENT: 'Referent',
    TEILNEHMER: 'Teilnehmer',
    BETRACHTER: 'Betrachter'
};

function parseRolesFromStorage() {
    const stored = localStorage.getItem('roles');
    if (stored) {
        try {
            return JSON.parse(stored);
        } catch {
            // Fällt durch auf den Legacy-Fallback unten.
        }
    }
    // Legacy-Fallback (siehe #751): vor der Mehrfachrollen-Funktion stand hier ein einzelner
    // 'role'-String in localStorage - u.a. von Playwright-Tests genutzt, um einen Login
    // vorzutäuschen (siehe z.B. TeilnehmerDashboard.spec.js), ohne echten Keycloak-Token-Flow.
    const legacyRole = localStorage.getItem('role');
    return legacyRole ? [legacyRole] : [];
}

export const useAuthStore = defineStore('auth', () => {
    const token = ref(localStorage.getItem('token') || null);
    const userRoles = ref(parseRolesFromStorage());
    // Welches Dashboard aktuell aktiv ist (siehe #751) - bewusst in sessionStorage statt
    // localStorage/eventContext.js-Muster: die Rollenwahl ist ein Pro-Sitzung/Tab-Konzept, kein
    // geräteweites "letzte Veranstaltung merken".
    const activeRole = ref(sessionStorage.getItem('activeRole') || null);
    const toast = useToast();

    const isAuthenticated = computed(() => !!token.value);
    // Administrator hat dieselben Rechte wie Organisator (siehe Backend: Administrator extends
    // Organisator) - ORGANISATOR/ADMINISTRATOR sind dabei stets die Primärrolle, nie eine
    // Zusatzrolle des jeweils anderen (siehe Backend Nutzer.hatRolle), daher hier weiterhin
    // explizit beide Rollenwerte prüfen. Diese Getter drücken aus, ob der Nutzer die Rolle
    // ÜBERHAUPT hält (Primär- oder Zusatzrolle) - NICHT, ob sie gerade aktiv ist (siehe activeRole).
    const isOrganisator = computed(() => userRoles.value.includes('ORGANISATOR') || userRoles.value.includes('ADMINISTRATOR'));
    const isAdministrator = computed(() => userRoles.value.includes('ADMINISTRATOR'));
    const isSpeaker = computed(() => userRoles.value.includes('REFERENT'));
    const isParticipant = computed(() => userRoles.value.includes('TEILNEHMER'));
    const isViewer = computed(() => userRoles.value.includes('BETRACHTER'));

    // Wird nach erfolgreicher Keycloak-Anmeldung (main.js, keycloak.js-Token-Refresh) mit dem
    // rohen Access-Token und dem von keycloak-js bereits dekodierten Payload aufgerufen.
    function setToken(newToken, parsed) {
        token.value = newToken;
        localStorage.setItem('token', newToken);

        const roles = parsed?.realm_access?.roles?.filter((r) => KNOWN_ROLES.includes(r)) ?? [];
        userRoles.value = roles;
        if (roles.length) {
            localStorage.setItem('roles', JSON.stringify(roles));
        } else {
            localStorage.removeItem('roles');
        }
    }


    /**
     * Wechselt das aktuell angezeigte Dashboard (siehe #751) - reine Frontend-Routing-Auswahl,
     * KEINE zusätzliche Zugriffsschranke (das Backend kennt keinen "aktive Rolle"-Begriff; wer
     * eine Rolle hält, darf die zugehörige Route jederzeit direkt ansteuern, siehe router/index.js).
     * Räumt wie logout() laufende Requests und den Veranstaltungskontext auf, damit beim
     * Dashboard-Wechsel keine Daten der vorherigen Rolle/Veranstaltung durchscheinen.
     */
    function setActiveRole(role) {
        if (!userRoles.value.includes(role)) {
            return;
        }
        cancelAllRequests();
        const eventContext = useEventContextStore();
        eventContext.clearEvent();

        activeRole.value = role;
        sessionStorage.setItem('activeRole', role);
    }

    function login(options) {
        keycloak.login(options);
    }

    // Vom Router-Guard genutzt, wenn eine geschuetzte Route ohne (mehr) gueltiges Token
    // aufgerufen wird - z.B. nach Browser-Zurueck auf eine Seite, die vor einem abgelaufenen
    // Token/Logout besucht wurde. Ohne die Meldung wirkt der anschliessende Redirect zu Keycloak
    // wie ein unerklaerter Sprung ("Pseudo-Undo") statt eines nachvollziehbaren Session-Endes.
    // silent: true fuer die allererste Navigation nach einem Seiten-/App-Neuladen (kein
    // "davor" innerhalb der SPA) - da ist "nicht angemeldet" der Normalfall, keine Ueberraschung.
    function requireLogin(redirectUri, { silent = false } = {}) {
        if (!silent) {
            toast.info('Sitzung abgelaufen oder abgemeldet. Bitte erneut anmelden.',
              { timeout: 5000, closeOnClick: true });
        }
        keycloak.login({ redirectUri });
    }

    function logout({ reason } = {}) {
        // Eine ggf. laufende Planerstellung serverseitig abbrechen, bevor der Token
        // geloescht wird (der Endpoint ist ORGANISATOR/ADMINISTRATOR-only, danach fehlt die Berechtigung).
        if (isOrganisator.value && token.value) {
            api.delete('/api/planungen', { headers: { Authorization: `Bearer ${token.value}` } })
                .catch(() => {});
        }
        cancelAllRequests();

        token.value = null;
        userRoles.value = [];
        activeRole.value = null;
        localStorage.removeItem('token');
        localStorage.removeItem('role');
        localStorage.removeItem('roles');
        sessionStorage.removeItem('activeRole');

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
        userRoles,
        activeRole,
        isAuthenticated,
        isOrganisator,
        isAdministrator,
        isSpeaker,
        isParticipant,
        isViewer,
        login,
        requireLogin,
        logout,
        setToken,
        setActiveRole
    };
});
