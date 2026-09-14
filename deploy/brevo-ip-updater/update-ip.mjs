// Traegt die uebergebene IP-Adresse ueber Brevos "Paste list"-Eingabe in die Authorized-IPs-
// Liste ein. Nutzt die von setup-session.mjs gespeicherte Sitzung - kein erneuter Login/Code
// noetig, solange Brevo das Geraet noch als vertrauenswuerdig einstuft.
//
// Aufruf: node update-ip.mjs <ip-adresse>
// Exit-Code 0 = erfolgreich, ungleich 0 = Fehlschlag (siehe stderr + error-*.png/error-*.html/error-*-diag.json).
//
// WICHTIG: Seiten-Selektoren sind nach bestem Wissen aus der oeffentlichen Brevo-Hilfe-
// Dokumentation entwickelt, aber nicht gegen den echten Account getestet - bei Abweichungen
// bitte mit einem Blick auf error-*.png, error-*.html (vollstaendiges DOM zum Fehlerzeitpunkt,
// aussagekraeftiger als der Screenshot allein bei z.B. dauerhaften Lade-Skeletons) UND
// error-*-diag.json (Konsolen-Fehler/-Warnungen + fehlgeschlagene Netzwerk-Antworten waehrend
// des gesamten Laufs - zeigt z.B. eine 401/403 auf einen API-Call, die im DOM-Snapshot allein
// unsichtbar bleibt) gemeinsam nachjustieren.
//
// Bekannte Einschraenkung: Die "Security"-Seite laedt im normalen Browser problemlos, bleibt
// aber im headless Playwright dauerhaft im Lade-Skeleton haengen (Header/Nav rendern normal, nur
// der "security-tabs"-Bereich nicht) - mehrere Live-Test-Runden zur Ursache, siehe
// error-*-diag.json der jeweiligen Fehlschlaege:
//   1. navigator.webdriver-Theorie (error-unexpected-1788333378470.html): einzelne
//      navigator.*-Properties ueberschrieben - hat NICHT nachhaltig geholfen.
//   2. Abgelaufene-Sitzung-Theorie: app.brevo.com ist eine Microfrontend-Architektur, bei der
//      Konto-/Sidebar-Daten per Hintergrund-Fetch von eigenen Subdomains (account-app.brevo.com,
//      sidebar-backend.brevo.com, ...) nachgeladen werden; diese Calls lieferten 401/403, waehrend
//      die Haupt-Seiten-URL nie auf /login umleitete. WIDERLEGT: derselbe Fehler trat unmittelbar
//      nach einem frischen setup-session.mjs-Lauf erneut auf - eine Sekunden alte Sitzung kann
//      nicht "abgelaufen" sein. Per manuellem Vergleich (echter Inkognito-Browser, Cookies
//      akzeptiert) verifiziert: dort liefern dieselben Calls 200, UND es gibt dort ebenfalls nur
//      Cookies fuer app.brevo.com (keine separaten Cookies je Subdomain) - Cookies/Session sind
//      also NICHT der Unterschied zwischen echtem Browser und Playwright.
//   3. Also vermutlich eine Erkennung unterhalb der JS-Ebene (z.B. TLS-/Netzwerk-Fingerprinting),
//      die einzelne navigator.*-Overrides grundsaetzlich nicht adressieren koennen - deshalb
//      unten playwright-extra + puppeteer-extra-plugin-stealth statt des blossen
//      'playwright'-Imports: patcht deutlich mehr, tiefer liegende Automatisierungsmerkmale
//      (u.a. WebGL-Vendor/Renderer, chrome.runtime/csi/loadTimes, iframe.contentWindow,
//      Permissions-API) als ein einzelnes addInitScript. NICHT bestaetigt, ob das die
//      eigentliche (vermutete TLS-/Netzwerk-)Ursache tatsaechlich behebt - falls nicht, bleibt
//      die manuelle Nachtrage laut Deployment-DockerCompose.adoc der Fallback.
import { chromium } from 'playwright-extra';
import stealth from 'puppeteer-extra-plugin-stealth';
import fs from 'node:fs';

chromium.use(stealth());

const STORAGE_STATE_PATH = './storage-state.json';
const AUTHORIZED_IPS_URL = 'https://app.brevo.com/security/authorised_ips';

const currentIp = process.argv[2];
if (!currentIp) {
  console.error('Aufruf: node update-ip.mjs <ip-adresse>');
  process.exit(1);
}

if (!fs.existsSync(STORAGE_STATE_PATH)) {
  console.error(`Keine gespeicherte Sitzung gefunden (${STORAGE_STATE_PATH}) - bitte zuerst setup-session.mjs ausführen.`);
  process.exit(1);
}

// Sammelt Konsolen-Meldungen und fehlgeschlagene (Status >= 400) Netzwerk-Antworten waehrend
// der gesamten Laufzeit - bei einem haengenden Lade-Skeleton (siehe Kommentar oben) zeigt weder
// Screenshot noch DOM-Snapshot, WARUM nichts rendert (z.B. eine 401/403 auf einen API-Call oder
// ein JS-Fehler beim Mounten der Komponente). Muss vor page.goto() registriert werden.
const diagnosticLog = { console: [], failedResponses: [] };

function attachDiagnosticListeners(page) {
  page.on('console', msg => {
    if (['error', 'warning'].includes(msg.type())) {
      diagnosticLog.console.push({ type: msg.type(), text: msg.text() });
    }
  });
  page.on('response', response => {
    if (response.status() >= 400) {
      diagnosticLog.failedResponses.push({ url: response.url(), status: response.status() });
    }
  });
}

async function saveErrorDiagnostics(page, label) {
  if (!page) {
    return;
  }
  const stamp = Date.now();
  const pngPath = `./error-${label}-${stamp}.png`;
  const htmlPath = `./error-${label}-${stamp}.html`;
  const diagPath = `./error-${label}-${stamp}-diag.json`;
  await page.screenshot({ path: pngPath, fullPage: true }).catch(() => {});
  await page.content().then(html => fs.writeFileSync(htmlPath, html)).catch(() => {});
  fs.writeFileSync(diagPath, JSON.stringify(diagnosticLog, null, 2));
  console.error(`Diagnose gespeichert: ${pngPath}, ${htmlPath}, ${diagPath}`);
  console.error(`Seite zum Zeitpunkt des Fehlers: url=${page.url()}, title=${await page.title().catch(() => '?')}`);
}

let browser;
let page;

try {
  browser = await chromium.launch({
    headless: true,
    args: ['--disable-blink-features=AutomationControlled'],
  });
  const context = await browser.newContext({
    storageState: STORAGE_STATE_PATH,
    userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 '
      + '(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36',
  });
  // navigator.webdriver/.plugins/.languages/window.chrome werden jetzt vom Stealth-Plugin
  // (chromium.use(stealth()) oben) gepatcht - siehe Kommentar oben, warum das den manuellen
  // addInitScript-Overrides von vorher vorgezogen wird.
  page = await context.newPage();
  attachDiagnosticListeners(page);

  // 'networkidle' statt 'domcontentloaded' faellt bei SPAs mit dauerhaften Verbindungen
  // (Websocket/Long-Polling fuer Live-Benachrichtigungen) leicht auf den Navigations-Timeout
  // (Playwright-Default 30s) durch, OHNE dass die eigentliche Seite ueberhaupt fertig gerendert
  // sein muss - das Timeout traegt dann selbst KEINE Aussage darueber, ob das Rendering
  // grundsaetzlich haengt oder nur der reine Idle-Zustand nie eintritt.
  await page.goto(AUTHORIZED_IPS_URL, { waitUntil: 'domcontentloaded', timeout: 30000 });

  if (page.url().includes('login')) {
    await saveErrorDiagnostics(page, 'session-expired');
    console.error('Gespeicherte Sitzung ist abgelaufen/ungültig (Weiterleitung zur Login-Seite) - bitte setup-session.mjs erneut ausführen.');
    process.exit(1);
  }

  // Hintergrund-Calls zu Konto-/Sidebar-Subdomains (account-app.brevo.com, sidebar-backend.brevo.com,
  // ...) lieferten in bisherigen Fehlschlaegen zuverlaessig 401/403 (siehe error-*-diag.json),
  // OHNE dass die Haupt-Seiten-URL selbst je auf /login umleitete - das wurde faelschlich als
  // "abgelaufene Sitzung" gedeutet und fuehrte zu einem verfruehten, fehlleitenden Abbruch hier
  // (siehe Kommentar oben: per echtem Browser-Vergleich widerlegt). Bewusst KEIN Abbruch mehr
  // allein auf Basis dieser Hintergrund-401s - stattdessen bleibt der explizite Warte-auf-Button-
  // Schritt unten die einzige verlaessliche Quelle dafuer, ob die Seite tatsaechlich nutzbar ist;
  // error-*-diag.json faengt die Hintergrund-401s weiterhin fuer die Post-mortem-Analyse ein.

  // Explizit auf den tatsaechlichen Button warten (statt dem impliziten Timeout von .click()),
  // damit ein Fehlschlag hier eindeutig "Button nie erschienen" bedeutet und nicht mit einem
  // Navigations- oder sonstigen Timeout verwechselt werden kann.
  const authorizeButton = page.getByRole('button', { name: 'Authorize IP addresses' });
  await authorizeButton.waitFor({ state: 'visible', timeout: 45000 });
  await authorizeButton.click();
  await page.waitForTimeout(500);

  // "Paste list"-Tab waehlen, falls die Datei-Upload-Ansicht standardmaessig aktiv ist.
  await page.getByText('Paste list', { exact: false }).click().catch(() => {});

  await page.locator('textarea').first().fill(currentIp);
  await page.getByRole('button', { name: 'Authorize IP addresses' }).last().click();
  await page.waitForTimeout(1500);

  // Sitzung kann sich durch die Interaktion erneuert haben (z.B. neuer CSRF-Token) - erneut
  // sichern, damit der naechste Lauf davon profitiert.
  await context.storageState({ path: STORAGE_STATE_PATH });

  console.log(`IP ${currentIp} bei Brevo unter Authorized IPs eingetragen.`);
} catch (e) {
  await saveErrorDiagnostics(page, 'unexpected');
  console.error(`Update fehlgeschlagen (${e.name}):`, e.message);
  process.exit(1);
} finally {
  if (browser) {
    await browser.close();
  }
}
