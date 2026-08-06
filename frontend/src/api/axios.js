import axios from 'axios';

import.meta.env.VITE_API_URL = undefined;
const api = axios.create({
    baseURL: import.meta.env.VITE_API_URL || 'http://localhost:9000',
});

// Alle aktuell offenen Requests, damit sie bei Logout gesammelt abgebrochen werden können.
const pendingControllers = new Set();

// Endpunkte, die im Backend @PermitAll sind (siehe AuthResource, ReferentResource,
// TeilnehmerResource) - hier darf ein evtl. noch in localStorage vorhandenes (z.B.
// abgelaufenes) Token NICHT mitgeschickt werden. Quarkus' JWT-Security-Layer lehnt jede
// Anfrage mit einem ungültigen Bearer-Token bereits VOR der @PermitAll-Prüfung mit 401 ab
// (per Live-Test verifiziert) - der Response-Interceptor unten würde das fälschlich als
// "Sitzung abgelaufen" werten und die Seite verlassen, was genau auf diesen öffentlichen
// Seiten (Passwort/E-Mail per Link zurücksetzen/bestätigen) fatal ist, da der Nutzer dort
// gerade deshalb ist, weil er ggf. gar keine gültige Sitzung (mehr) hat.
const PUBLIC_PATHS = [
    '/api/auth/login',
    '/api/auth/forgot-password',
    '/api/auth/reset-password',
    '/api/referenten/email-change-confirm',
    '/api/teilnehmer/email-change-confirm',
];

function isPublicPath(url) {
    return !!url && PUBLIC_PATHS.some((path) => url.startsWith(path));
}

// Interceptor: Fügt das JWT-Token automatisch in den Header ein (außer bei @PermitAll-
// Endpunkten, s.o.) und hängt einen AbortSignal an, damit der Request bei Logout
// abgebrochen werden kann.
api.interceptors.request.use((config) => {
    config.headers = config.headers ?? {};

    if (!isPublicPath(config.url)) {
        const token = localStorage.getItem('token');
        if (token) {
            config.headers.Authorization = `Bearer ${token}`;
        }
    }

    if (!config.signal) {
        const controller = new AbortController();
        config.signal = controller.signal;
        config.__abortController = controller;
        pendingControllers.add(controller);
    }
    return config;
});

// Interceptor: Erkennt abgelaufene Tokens (401)
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

            if (window.location.pathname !== '/login') {
                window.location = '/login';
            }
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