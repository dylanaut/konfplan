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

// Kein onLoad-Modus: init() verarbeitet nur einen ggf. in der URL vorhandenen Redirect von
// Keycloak (Authorization Code). Ohne Code/vorhandene Session wird NICHT automatisch per
// stillem iFrame nach einer bestehenden SSO-Session gefragt (das waere zusaetzlich fragil,
// da moderne Browser Third-Party-Cookies in iFrames zunehmend blockieren) - der Router-Guard
// entscheidet pro Route, ob ein Login (per echtem Redirect) noetig ist.
// checkLoginIframe:false, da Keycloaks periodischer iFrame-basierter Session-Check (per Default
// aktiv) sonst bei jedem Laden versucht, ein Cross-Origin-iFrame gegen den Keycloak-Server zu
// laden - schlaegt das fehl (z.B. Keycloak nicht erreichbar), kann der Browser in diesem iFrame
// sogar den Zugriff auf localStorage verweigern und die App-Initialisierung crashen lassen.
keycloak.init({ pkceMethod: 'S256', checkLoginIframe: false })
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