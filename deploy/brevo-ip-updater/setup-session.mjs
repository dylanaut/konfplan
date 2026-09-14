// Loggt sich bei Brevo ein und speichert die resultierende Browser-Sitzung (Cookies) in
// storage-state.json. Wird von check-outbound-ip.sh AUTOMATISCH vor JEDEM update-ip.mjs-Lauf
// erneut ausgefuehrt (nicht mehr nur einmalig) - per Live-Test verifiziert (siehe
// error-api-*.json-Funde bei update-ip.mjs): das eigentliche Session-/auth-Cookie hat eine
// deutlich kuerzere Gueltigkeit als die "vertrauenswuerdiges Geraet"-Erkennung selbst (die
// den 6-stelligen Code erspart) - eine einmalig gespeicherte Sitzung wird deshalb zwischen
// zwei IP-Aenderungen zuverlaessig ungueltig, auch wenn Brevo das Geraet weiterhin als
// vertrauenswuerdig einstuft.
//
// Voraussetzung (einmalig): node_modules in diesen Ordner installieren - das Playwright-Image
// bringt nur den Browser mit, nicht das npm-Paket selbst:
//   docker run --rm -v "$(pwd):/work" -w /work mcr.microsoft.com/playwright:v1.62.1-noble npm install
//
// Aufruf (manuell, z.B. fuer das allererste Login von einem neuen Geraet, das einen per E-Mail
// zugestellten 6-stelligen Code verlangt - dafuer wird -it und ein echtes Terminal benoetigt):
//   docker run --rm -it -v "$(pwd):/work" -w /work \
//     -e BREVO_LOGIN_EMAIL=... -e BREVO_LOGIN_PASSWORD=... \
//     mcr.microsoft.com/playwright:v1.62.1-noble node setup-session.mjs
// Nicht-interaktive Aufrufe (per Cron, ohne -it) erwarten, dass das Geraet bereits als
// vertrauenswuerdig gilt und daher KEIN Code angefordert wird - siehe TTY-Pruefung unten. Dafuer
// werden alle Cookies aus einer vorhandenen storage-state.json AUSSER den drei session-
// spezifischen (auth/ACCOUNTSESSID/loggedin) uebernommen - dritter Versuch nach zwei per
// Live-Test widerlegten Varianten, siehe Kommentar weiter unten fuer Details. NICHT bestaetigt,
// ob das die eigentliche Geraete-Erkennung trifft.
//
// WICHTIG: Login-Formular-Selektoren sind nach bestem Wissen aus der oeffentlichen Brevo-
// Hilfe-Dokumentation entwickelt, aber nicht gegen den echten Account getestet (kein Zugriff
// auf echte Zugangsdaten) - bei Abweichungen bitte mit einem Blick auf error-*.png (wird bei
// jedem Fehler automatisch gespeichert) gemeinsam nachjustieren.

import { chromium } from 'playwright';
import readline from 'node:readline/promises';
import { stdin as input, stdout as output } from 'node:process';
import fs from 'node:fs';

const EMAIL = process.env.BREVO_LOGIN_EMAIL;
const PASSWORD = process.env.BREVO_LOGIN_PASSWORD;
const STORAGE_STATE_PATH = './storage-state.json';

if (!EMAIL || !PASSWORD) {
  console.error('BREVO_LOGIN_EMAIL/BREVO_LOGIN_PASSWORD nicht gesetzt.');
  process.exit(1);
}

async function saveErrorScreenshot(page, label) {
  if (!page) {
    return;
  }
  const path = `./error-${label}-${Date.now()}.png`;
  await page.screenshot({ path }).catch(() => {});
  console.error(`Screenshot gespeichert: ${path}`);
}

let browser;
let page;

try {
  browser = await chromium.launch({ headless: true });
  const context = await browser.newContext();

  // Alle Cookies aus einer vorhandenen storage-state.json AUSSER den drei session-spezifischen
  // (auth/ACCOUNTSESSID/loggedin) uebernehmen. Bisherige, per Live-Test widerlegte Varianten:
  //   - Komplett leerer Kontext: zeigt zuverlaessig das Formular, aber Brevo fordert bei JEDEM
  //     Lauf erneut den 6-stelligen Code an.
  //   - KOMPLETTE alte Sitzung uebernommen (inkl. auth/loggedin): liess login.brevo.com direkt
  //     zum Dashboard durchleiten, OHNE das Formular zu zeigen - nie ein echter Login, die
  //     bereits ungueltige Sitzung wurde beim Speichern unveraendert erneut gesichert.
  //     - NUR "did" uebernommen: zeigte das Formular normal, forderte aber TROTZDEM wieder den
  //     Code an - "did" alleine ist also NICHT das (einzige) Geraete-Erkennungsmerkmal.
  // Dieser Versuch: alle NICHT session-identifizierenden Cookies (Tracking-/Praeferenz-Cookies,
  // "did", etc.) mitnehmen, nur die drei bekannten Session-Marker weglassen, um zu testen, ob die
  // Geraete-Erkennung an einer Kombination mehrerer Cookies haengt, statt an "did" allein.
  if (fs.existsSync(STORAGE_STATE_PATH)) {
    const SESSION_ONLY_COOKIES = new Set(['auth', 'ACCOUNTSESSID', 'loggedin']);
    const previousState = JSON.parse(fs.readFileSync(STORAGE_STATE_PATH, 'utf-8'));
    const reusableCookies = (previousState.cookies ?? []).filter(c => !SESSION_ONLY_COOKIES.has(c.name));
    if (reusableCookies.length > 0) {
      await context.addCookies(reusableCookies);
    }
  }

  page = await context.newPage();

  console.log('Öffne Brevo-Login...');
  // 'networkidle' faellt bei SPAs mit dauerhaften Verbindungen (Websocket/Long-Polling) leicht
  // auf den Navigations-Timeout durch, OHNE dass die Login-Seite ueberhaupt fertig gerendert
  // sein muss (siehe dieselbe Korrektur in update-ip.mjs) - 'domcontentloaded' reicht hier, da
  // die folgenden Schritte ohnehin auf konkrete Formularfelder warten.
  await page.goto('https://login.brevo.com', { waitUntil: 'domcontentloaded', timeout: 30000 });

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
    // Bei einem nicht-interaktiven Aufruf (per Cron, ohne -it) haette stdin kein TTY und
    // rl.question() wuerde auf eine Eingabe warten, die nie kommt - der Prozess wuerde dann bis
    // zu einem aeusseren Timeout haengen bleiben, statt klar zu scheitern. Stattdessen sofort mit
    // einer eindeutigen Fehlermeldung abbrechen.
    if (!input.isTTY) {
      await saveErrorScreenshot(page, 'code-required-non-interactive');
      console.error('Geraeteverifizierung (6-stelliger Code) erforderlich, aber nicht-interaktiver Aufruf (kein TTY) - '
        + 'bitte einmalig manuell mit "docker run -it" ausführen, um das Geraet erneut als vertrauenswürdig einzustufen.');
      process.exit(1);
    }

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

  // Frueher wurde hier vor dem Speichern noch einmal die Authorized-IPs-Seite besucht (Theorie:
  // notwendiger Init-State in localStorage). Diese Theorie ist inzwischen ueberholt - update-ip.mjs
  // braucht nur noch die Cookies fuer einen direkten API-Call (siehe dessen Kommentar), kein
  // Seiten-Besuch mehr noetig. Der Extra-Request entfaellt bewusst, da diese Seite bekanntermassen
  // im headless Playwright haengen kann und dieser Lauf jetzt vor JEDEM update-ip.mjs-Aufruf
  // erfolgt (Latenz zaehlt hier mehr als bei einem frueher wirklich einmaligen Setup).
  await context.storageState({ path: STORAGE_STATE_PATH });
  console.log(`Sitzung gespeichert in ${STORAGE_STATE_PATH}.`);
} catch (e) {
  await saveErrorScreenshot(page, 'unexpected');
  console.error('Setup fehlgeschlagen:', e.message);
  process.exit(1);
} finally {
  if (browser) {
    await browser.close();
  }
}
