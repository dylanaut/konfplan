// Einmaliges, interaktives Setup: loggt sich bei Brevo ein, fragt den beim ersten Login von
// einem neuen Geraet per E-Mail zugestellten 6-stelligen Code ab und speichert die daraus
// resultierende Browser-Sitzung (Cookies) dauerhaft in storage-state.json. Danach erkennt
// Brevo dieses "Geraet" (= diese gespeicherte Sitzung) als vertrauenswuerdig, weitere Logins
// ueber update-ip.mjs brauchen den Code nicht mehr.
//
// Voraussetzung (einmalig): node_modules in diesen Ordner installieren - das Playwright-Image
// bringt nur den Browser mit, nicht das npm-Paket selbst:
//   docker run --rm -v "$(pwd):/work" -w /work mcr.microsoft.com/playwright:v1.62.1-noble npm install
//
// Aufruf (einmalig, manuell, NICHT per Cron):
//   docker run --rm -it -v "$(pwd):/work" -w /work \
//     -e BREVO_LOGIN_EMAIL=... -e BREVO_LOGIN_PASSWORD=... \
//     mcr.microsoft.com/playwright:v1.62.1-noble node setup-session.mjs
//
// WICHTIG: Login-Formular-Selektoren sind nach bestem Wissen aus der oeffentlichen Brevo-
// Hilfe-Dokumentation entwickelt, aber nicht gegen den echten Account getestet (kein Zugriff
// auf echte Zugangsdaten) - bei Abweichungen bitte mit einem Blick auf error-*.png (wird bei
// jedem Fehler automatisch gespeichert) gemeinsam nachjustieren.

import { chromium } from 'playwright';
import readline from 'node:readline/promises';
import { stdin as input, stdout as output } from 'node:process';

const EMAIL = process.env.BREVO_LOGIN_EMAIL;
const PASSWORD = process.env.BREVO_LOGIN_PASSWORD;
const STORAGE_STATE_PATH = './storage-state.json';

if (!EMAIL || !PASSWORD) {
  console.error('BREVO_LOGIN_EMAIL/BREVO_LOGIN_PASSWORD nicht gesetzt.');
  process.exit(1);
}

async function saveErrorScreenshot(page, label) {
  const path = `./error-${label}-${Date.now()}.png`;
  await page.screenshot({ path }).catch(() => {});
  console.error(`Screenshot gespeichert: ${path}`);
}

const browser = await chromium.launch({ headless: true });
const context = await browser.newContext();
const page = await context.newPage();

try {
  console.log('Öffne Brevo-Login...');
  await page.goto('https://login.brevo.com', { waitUntil: 'networkidle' });

  // Cookie-Banner wegklicken, falls vorhanden - kann sonst spaetere Klicks verdecken/blockieren.
  await page.getByRole('button', { name: 'Reject All' }).click({ timeout: 5000 }).catch(() => {});

  // E-Mail UND Passwort stehen auf Brevo auf derselben Seite (kein zweistufiger Login) -
  // per Live-Test verifiziert (siehe error-unexpected-1787732195391.png).
  await page.fill('input[type="email"], input[name="email"]', EMAIL);
  await page.fill('input[type="password"], input[name="password"]', PASSWORD);
  await page.getByRole('button', { name: 'Log In' }).click();
  await page.waitForTimeout(2500);

  // Geraeteverifizierung: Brevo verlangt beim ersten Login von einem neuen Geraet einen per
  // E-Mail zugestellten 6-stelligen Code ("Verify your device"-Seite, Feld mit Placeholder
  // "E.g. 172846", Button "Verify" - per Live-Test verifiziert).
  const codeInput = page.locator(
    'input[placeholder*="172846"], input[name="code"], input[autocomplete="one-time-code"]'
  ).first();
  const codeFieldVisible = await codeInput.isVisible({ timeout: 5000 }).catch(() => false);

  if (codeFieldVisible) {
    console.log('Geraeteverifizierung erforderlich - Code wurde per E-Mail an ' + EMAIL + ' geschickt.');
    const rl = readline.createInterface({ input, output });
    const code = await rl.question('Bitte den 6-stelligen Code eingeben: ');
    rl.close();

    await codeInput.fill(code.trim());
    await page.getByRole('button', { name: /^Verify$|Confirm|Submit|Log In/i }).click();
    await page.waitForTimeout(2000);
  } else {
    console.log('Keine Geraeteverifizierung angefordert (evtl. Selektor nicht gefunden - siehe Screenshot bei Problemen).');
  }

  if (page.url().includes('login')) {
    await saveErrorScreenshot(page, 'login-not-completed');
    console.error('Login scheint nicht abgeschlossen zu sein (noch auf der Login-Seite). Bitte Screenshot pruefen.');
    process.exit(1);
  }

  await context.storageState({ path: STORAGE_STATE_PATH });
  console.log(`Sitzung gespeichert in ${STORAGE_STATE_PATH}. update-ip.mjs kann diese jetzt ohne erneute Geraeteverifizierung nutzen.`);
} catch (e) {
  await saveErrorScreenshot(page, 'unexpected');
  console.error('Setup fehlgeschlagen:', e.message);
  process.exit(1);
} finally {
  await browser.close();
}
