import axios from 'axios';

import.meta.env.VITE_API_URL = undefined;
const api = axios.create({
    baseURL: import.meta.env.VITE_API_URL || 'http://localhost:9000',
});

// Alle aktuell offenen Requests, damit sie bei Logout gesammelt abgebrochen werden können.
const pendingControllers = new Set();

// Interceptor: Fügt das JWT-Token automatisch in den Header ein und hängt einen
// AbortSignal an, damit der Request bei Logout abgebrochen werden kann.
api.interceptors.request.use((config) => {
    const token = localStorage.getItem('token');

    config.headers = config.headers ?? {};

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