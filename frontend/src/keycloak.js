import Keycloak from 'keycloak-js';

// Dev-Default passt zum Realm-Export fuer Quarkus Keycloak Dev Services
// (backend/src/main/resources/keycloak/konfplan-realm.json). In Prod wird Keycloak unter
// demselben Hostnamen wie die App unter /auth erreicht (siehe deploy/Caddyfile) - das laesst
// sich zur Laufzeit aus window.location.origin ableiten, ohne die URL zur Build-Zeit fest
// einzubacken (dasselbe CI-Image wird auf beliebigen Hosts deployt). VITE_KEYCLOAK_URL bleibt
// als expliziter Override moeglich, falls Keycloak doch mal auf einer anderen Domain laeuft.
const keycloak = new Keycloak({
    url: import.meta.env.VITE_KEYCLOAK_URL
        || (import.meta.env.DEV ? 'http://localhost:8180' : `${window.location.origin}/auth`),
    realm: import.meta.env.VITE_KEYCLOAK_REALM || 'konfplan',
    clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID || 'konfplan-frontend',
});

export default keycloak;
