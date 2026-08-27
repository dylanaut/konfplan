import axios from 'axios';
import { useToast } from 'vue-toastification';
import keycloak from '../keycloak';

// useToast() statt useAuthStore()/requireLogin(), um keinen Zirkelimport zu erzeugen
// (auth.js importiert bereits von hier). Erst im Interceptor selbst aufgeloest (nicht hier
// auf Modulebene) - zu diesem fruehen Zeitpunkt (Import-Reihenfolge in main.js) ist das
// Toast-Plugin noch nicht per app.use(Toast, ...) registriert.
const toast = () => useToast();

// In Produktion (Quinoa-Deployment: Frontend und Backend auf demselben Origin) muss die
// baseURL relativ bleiben - ein hartcodierter 'http://localhost:9000'-Fallback wuerde im
// Browser des Nutzers auf dessen eigenen localhost zeigen, nicht auf den Server.
// VITE_API_URL (siehe .env.development) ueberschreibt das nur fuer den lokalen Dev-Betrieb,
// wo Frontend (:5173) und Backend (:9000) getrennte Origins sind.
const api = axios.create({
    baseURL: import.meta.env.VITE_API_URL || '',
});

// Alle aktuell offenen Requests, damit sie bei Logout gesammelt abgebrochen werden können.
const pendingControllers = new Set();

// Interceptor: Erneuert das Keycloak-Token bei Bedarf (falls es in <30s ablaeuft) und haengt es
// als Bearer-Header an. Faellt auf einen ggf. per localStorage gesetzten Token zurueck (z.B.
// Playwright-Tests, die eine Session ohne echten Keycloak-Login simulieren, siehe auth.js).
// Zusaetzlich ein AbortSignal, damit der Request bei Logout abgebrochen werden kann.
api.interceptors.request.use(async (config) => {
    config.headers = config.headers ?? {};

    try {
        await keycloak.updateToken(30);
    } catch (e) {
        // Keine (mehr gueltige) Keycloak-Session - Fallback unten greift ggf. trotzdem, aber ohne
        // dieses Log war ein fehlgeschlagener Refresh in der Browser-Konsole unsichtbar und zeigte
        // sich nur indirekt als 401 mit einem WARN im Backend-Log (Quarkus OidcProvider).
        console.warn('Keycloak-Token-Refresh fehlgeschlagen, Request nutzt ggf. abgelaufenen Token:', e);
    }
    const token = keycloak.token ?? localStorage.getItem('token');
    if (token) {
        config.headers.Authorization = `Bearer ${token}`;
    }

    if (!config.signal) {
        const controller = new AbortController();
        config.signal = controller.signal;
        config.__abortController = controller;
        pendingControllers.add(controller);
    }
    return config;
});

// Interceptor: Erkennt eine ungueltig gewordene Sitzung (401) und schickt den Nutzer zurueck
// zu Keycloaks Login-Seite.
api.interceptors.response.use(
    (response) => {
        pendingControllers.delete(response.config.__abortController);
        return response;
    },
    (error) => {
        pendingControllers.delete(error.config?.__abortController);

        if (error.response?.status === 401) {
            localStorage.removeItem('token');
            localStorage.removeItem('role');
            toast().info('Sitzung abgelaufen oder abgemeldet. Bitte erneut anmelden.',
              { timeout: 5000, closeOnClick: true });
            keycloak.login();
        }
        return Promise.reject(error);
    }
);

// Bricht alle gerade offenen Requests ab (siehe auth.js: logout()).
export function cancelAllRequests() {
    pendingControllers.forEach((controller) => controller.abort());
    pendingControllers.clear();
}

export default api;
