import { createApp } from 'vue'
import { createPinia } from 'pinia'
import router from './router/index'
import App from './App.vue'
import Toast from 'vue-toastification'
import 'vue-toastification/dist/index.css'
import 'bootstrap/dist/css/bootstrap.min.css';
import 'bootstrap/dist/js/bootstrap.bundle.min.js';

import './api/axios'
import './style.css'
import keycloak from './keycloak'
import { useAuthStore } from './stores/auth'

const app = createApp(App)
const pinia = createPinia()
app.use(pinia)
app.use(router)
app.use(Toast, {
    transition: "Vue-Toastification__bounce",
    maxToasts: 5,
    newestOnTop: true
})

// check-sso: pruefe eine ggf. bestehende Keycloak-Session (SSO-Cookie), ohne den Nutzer bei
// fehlender Session zum Login zu zwingen - das entscheidet erst der Router-Guard pro Route.
keycloak.init({ onLoad: 'check-sso', pkceMethod: 'S256', silentCheckSsoRedirectUri: window.location.origin + '/silent-check-sso.html' })
    .then((authenticated) => {
        if (authenticated) {
            useAuthStore(pinia).setToken(keycloak.token, keycloak.tokenParsed);
        }
        app.mount('#app');
    })
    .catch((error) => {
        console.error('Keycloak-Initialisierung fehlgeschlagen:', error);
        app.mount('#app');
    });