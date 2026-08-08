import { watch } from 'vue';
import { useAuthStore } from '../stores/auth';

/**
 * Sitzung wird nach dieser Dauer ohne Nutzerinteraktion automatisch beendet. Sicherheits-
 * massnahme, kein Performance-Feature: der Zweck ist, das Zeitfenster zu begrenzen, in dem ein
 * unbeaufsichtigtes, entsperrtes Geraet missbraucht werden koennte, unabhaengig von der vollen
 * Gueltigkeit der Keycloak-Session.
 */
export const INACTIVITY_TIMEOUT_MS = 30 * 60 * 1000;

// Aktivitaet, die die Sitzung als "genutzt" zaehlt.
const ACTIVITY_EVENTS = ['mousedown', 'mousemove', 'keydown', 'scroll', 'touchstart', 'wheel'];

// Verhindert, dass z.B. mousemove den Timer bei jedem einzelnen Event (mehrfach pro Sekunde)
// neu setzt - ein Reset pro Sekunde reicht fuer einen 30-Minuten-Timer voellig aus.
const RESET_THROTTLE_MS = 1000;

/**
 * Registriert (nur waehrend auth.isAuthenticated) globale Aktivitaets-Listener und meldet den
 * Nutzer nach INACTIVITY_TIMEOUT_MS ohne Interaktion automatisch ab. Einmalig aus App.vue
 * aufzurufen.
 */
export function useInactivityLogout() {
    const auth = useAuthStore();
    let timeoutId = null;
    let lastReset = 0;

    const resetTimer = () => {
        const now = Date.now();
        if (now - lastReset < RESET_THROTTLE_MS) {
            return;
        }
        lastReset = now;

        if (timeoutId) {
            clearTimeout(timeoutId);
        }
        timeoutId = setTimeout(() => {
            auth.logout({ reason: 'inactive' });
        }, INACTIVITY_TIMEOUT_MS);
    };

    const startTracking = () => {
        ACTIVITY_EVENTS.forEach((evt) => window.addEventListener(evt, resetTimer, { passive: true }));
        lastReset = 0; // erzwingt, dass der erste resetTimer()-Aufruf sicher durchgreift
        resetTimer();
    };

    const stopTracking = () => {
        ACTIVITY_EVENTS.forEach((evt) => window.removeEventListener(evt, resetTimer));
        if (timeoutId) {
            clearTimeout(timeoutId);
            timeoutId = null;
        }
    };

    watch(
        () => auth.isAuthenticated,
        (isAuthenticated) => {
            if (isAuthenticated) {
                startTracking();
            } else {
                stopTracking();
            }
        },
        { immediate: true }
    );
}
