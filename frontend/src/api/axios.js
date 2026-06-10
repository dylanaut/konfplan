import axios from 'axios';

import.meta.env.VITE_API_URL = undefined;
const api = axios.create({
    baseURL: import.meta.env.VITE_API_URL || 'http://localhost:9000',
});

// Interceptor: Fügt das JWT-Token automatisch in den Header ein
api.interceptors.request.use((config) => {
    const token = localStorage.getItem('token');

    config.headers = config.headers ?? {};

    if (token) {
        config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
});

// Interceptor: Erkennt abgelaufene Tokens (401)
api.interceptors.response.use(
    (response) => response,
    (error) => {
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

export default api;