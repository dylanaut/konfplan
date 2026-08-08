import Keycloak from 'keycloak-js';

// Dev-Werte passen zum Realm-Export fuer Quarkus Keycloak Dev Services
// (backend/src/main/resources/keycloak/konfplan-realm.json). In Prod per Vite-Env-Variable
// ueberschreiben.
const keycloak = new Keycloak({
    url: import.meta.env.VITE_KEYCLOAK_URL || 'http://localhost:8180',
    realm: import.meta.env.VITE_KEYCLOAK_REALM || 'konfplan',
    clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID || 'konfplan-frontend',
});

export default keycloak;
