// Traegt die uebergebene IP-Adresse per direktem, authentifiziertem API-Call in Brevos
// Authorized-IPs-Liste ein. Nutzt die von setup-session.mjs gespeicherte Sitzung (Cookies) -
// kein erneuter Login/Code noetig, solange Brevo das Geraet noch als vertrauenswuerdig einstuft.
//
// Aufruf: node update-ip.mjs <ip-adresse>
// Exit-Code 0 = erfolgreich, ungleich 0 = Fehlschlag (siehe stderr + error-api-*.json).
//
// Vorgeschichte (mehrere gescheiterte Ansaetze ueber vollstaendiges Browser-Rendering der
// Brevo-"Security"-Seite, siehe Git-Historie dieser Datei fuer Details): die Seite haengt im
// headless Playwright dauerhaft in einem Lade-Skeleton, weil mehrere UNBETEILIGTE Widgets
// (Chat, Marketing-Benachrichtigungen, Sidebar-App-Switcher auf eigenen Subdomains wie
// conversations-app.brevo.com/sidebar-backend.brevo.com) mit 401/403 fehlschlagen - weder
// navigator.*-Overrides noch ein Stealth-Plugin (playwright-extra + puppeteer-extra-plugin-
// stealth) behoben das, und ein manueller Vergleich mit einem echten Browser widerlegte sowohl
// die Bot-Erkennungs- als auch die Abgelaufene-Sitzung-Theorie als Unterschied.
//
// Der eigentliche Fund: der Button "Authorize IP addresses" loest beim Absenden lediglich einen
// simplen, rein cookie-authentifizierten POST an eine ANDERE, bislang nie fehlgeschlagene
// Subdomain aus (siehe BULK_CREATE_URL) - kein Zusammenhang mit den oben genannten Widgets.
// Per Live-Test (Network-Tab-Mitschnitt eines echten Browsers) verifiziert: kein Auth-Header,
// kein CSRF-Token noetig, nur die vorhandenen Session-Cookies. Deshalb hier bewusst KEIN
// Browser-Rendering der Seite mehr - nur ein direkter authentifizierter API-Call ueber
// Playwright's request-Kontext (der die Cookies aus storage-state.json automatisch mitschickt).
// Damit entfaellt die gesamte Klasse von Lade-Skeleton-/Fingerprinting-Problemen der Seite, da
// deren fehlerhafte Widgets fuer diese eine Aktion gar nicht erst geladen werden.
import { request } from 'playwright';
import fs from 'node:fs';
import { IMAP_CONFIGURED, getStartingUid, pollForVerificationCodeViaImap } from './imap-code.mjs';

const STORAGE_STATE_PATH = './storage-state.json';
const BULK_CREATE_URL = 'https://ato-manager.brevo.com/ips/bulk-create';
const STEP_UP_BASE_URL = 'https://step-up-auth.brevo.com';
const STEP_UP_ACTION = 'authorized-ip-bulk-create';

// Header, die Brevos Backend per CORS/Origin-Pruefung verlangt (per Live-Test verifiziert) -
// ohne origin/referer schlaegt der Call vermutlich fehl, auch mit gueltigen Cookies. Die
// uebrigen sind nicht bestaetigt notwendig, aber 1:1 aus dem mitgeschnittenen Browser-Request.
const API_HEADERS = {
  accept: '*/*',
  'content-type': 'application/json',
  origin: 'https://app.brevo.com',
  referer: 'https://app.brevo.com/',
  'user-agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 '
    + '(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36',
  'sec-fetch-site': 'same-site',
  'sec-fetch-mode': 'cors',
  'sec-fetch-dest': 'empty',
};

const currentIp = process.argv[2];
if (!currentIp) {
  console.error('Aufruf: node update-ip.mjs <ip-adresse>');
  process.exit(1);
}

if (!fs.existsSync(STORAGE_STATE_PATH)) {
  console.error(`Keine gespeicherte Sitzung gefunden (${STORAGE_STATE_PATH}) - bitte zuerst setup-session.mjs ausführen.`);
  process.exit(1);
}

let apiContext;

try {
  apiContext = await request.newContext({ storageState: STORAGE_STATE_PATH });

  const bulkCreate = () => apiContext.post(BULK_CREATE_URL, {
    headers: API_HEADERS,
    data: { ip_addresses: [currentIp] },
  });

  let response = await bulkCreate();

  // Brevo verlangt fuer diese Aktion eine Step-up-Verifizierung per E-Mail-Code (403 step_up_required).
  if (response.status() === 403 && (await response.text().catch(() => '')).includes('step_up_required')) {
    if (!IMAP_CONFIGURED) {
      console.error('Step-up-Verifizierung erforderlich, aber BREVO_CODE_IMAP_* nicht konfiguriert.');
      process.exit(1);
    }
    console.log('Step-up-Verifizierung erforderlich - hole Code automatisch per IMAP...');

    /* Muss VOR dem Ausloesen der Code-Mail erfasst werden. */
    const startUid = await getStartingUid();

    /* Der verify-Call liefert 401 und wird ignoriert; er initialisiert die Step-up-Sitzung. */
    await apiContext.get(`${STEP_UP_BASE_URL}/verify`, { headers: API_HEADERS });

    /* Brevo verschickt keinen neuen Code, solange der vorherige (z.B. vom Login in setup-session.mjs)
       noch gueltig ist - nach Ablauf des Poll-Fensters den Code daher einmal erneut anfordern. */
    let code;
    for (let attempt = 1; code === undefined; attempt++) {
      const requestResponse = await apiContext.get(`${STEP_UP_BASE_URL}/request/email_verification`, { headers: API_HEADERS });
      if (!requestResponse.ok()) {
        console.error(`Step-up-Code-Anforderung fehlgeschlagen: ${requestResponse.status()} ${await requestResponse.text().catch(() => '')}`);
        process.exit(1);
      }
      try {
        code = await pollForVerificationCodeViaImap(startUid);
      } catch (e) {
        if (attempt >= 2)
          throw e;
        console.log(`Kein Code erhalten (Versuch ${attempt}) - fordere erneut an...`);
      }
    }
    const verifyResponse = await apiContext.post(`${STEP_UP_BASE_URL}/request/email_verification/verify`, {
      headers: API_HEADERS,
      data: { verification_code: code, action: STEP_UP_ACTION },
    });
    if (!verifyResponse.ok()) {
      console.error(`Step-up-Verifizierung fehlgeschlagen: ${verifyResponse.status()} ${await verifyResponse.text().catch(() => '')}`);
      process.exit(1);
    }

    response = await bulkCreate();
  }

  if (!response.ok()) {
    const bodyText = await response.text().catch(() => '(kein Body)');
    const diagPath = `./error-api-${Date.now()}.json`;
    fs.writeFileSync(diagPath, JSON.stringify({
      status: response.status(),
      statusText: response.statusText(),
      body: bodyText,
    }, null, 2));
    console.error(`Brevo-API antwortete mit ${response.status()} ${response.statusText()} - Diagnose gespeichert: ${diagPath}`);
    process.exit(1);
  }

  // Cookies koennen sich durch den Call erneuert haben (z.B. rotierender Session-Token) -
  // erneut sichern, damit der naechste Lauf davon profitiert.
  await apiContext.storageState({ path: STORAGE_STATE_PATH });

  console.log(`IP ${currentIp} bei Brevo unter Authorized IPs eingetragen (direkter API-Call).`);
} catch (e) {
  console.error(`Update fehlgeschlagen (${e.name}):`, e.message);
  process.exit(1);
} finally {
  if (apiContext) {
    await apiContext.dispose();
  }
}
