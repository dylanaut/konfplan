import axios from 'axios';
import keycloak from '../keycloak';

import.meta.env.VITE_API_URL = undefined;
const api = axios.create({
    baseURL: import.meta.env.VITE_API_URL || 'http://localhost:9000',
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
    } catch {
        // Keine (mehr gueltige) Keycloak-Session - Fallback unten greift ggf. trotzdem.
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
