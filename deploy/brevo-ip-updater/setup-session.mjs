// Loggt sich bei Brevo ein und speichert die resultierende Browser-Sitzung (Cookies) in
// storage-state.json. Wird von check-outbound-ip.sh AUTOMATISCH vor JEDEM update-ip.mjs-Lauf
// erneut ausgefuehrt (nicht mehr nur einmalig) - per Live-Test verifiziert (siehe
// error-api-*.json-Funde bei update-ip.mjs): das eigentliche Session-/auth-Cookie hat eine
// deutlich kuerzere Gueltigkeit als eine gespeicherte Sitzung ueberlebt, wird also zwischen zwei
// IP-Aenderungen zuverlaessig ungueltig.
//
// WICHTIG, per Live-Test abschliessend geklaert: Brevo verlangt bei DIESEM Login-Flow IMMER den
// per E-Mail zugestellten 6-stelligen Code - unabhaengig von Cookies, User-Agent oder IP (mehrere
// Theorien dazu wurden nacheinander widerlegt: weder verschiedene Cookie-Kombinationen noch ein
// gleichbleibender IP-Wechsel zwischen zwei unmittelbar aufeinanderfolgenden Laeufen vermieden
// die Geraeteverifizierung; ein zusaetzlicher UA-Override, siehe unten, war ebenfalls nicht die
// Ursache). Es gibt fuer diesen Flow offenbar KEINE dauerhafte "vertrauenswuerdiges Geraet"-
// Erkennung. Ein bereits zugestellter Code bleibt ca. 90s gueltig, danach schickt Brevo einen
// NEUEN Code. Fuer echte Automatisierung MUSS der Code deshalb automatisch aus der E-Mail
// gelesen werden - siehe die IMAP-Konfiguration unten (BREVO_CODE_IMAP_*). Ohne diese
// Konfiguration faellt das Skript auf einen interaktiven readline-Prompt zurueck (oder scheitert
// klar, wenn nicht-interaktiv).
//
// Voraussetzung (einmalig): node_modules in diesen Ordner installieren - das Playwright-Image
// bringt nur den Browser mit, nicht die npm-Pakete selbst:
//   docker run --rm -v "$(pwd):/work" -w /work mcr.microsoft.com/playwright:v1.62.1-noble npm install
//
// Aufruf mit automatischer Code-Ermittlung per IMAP (fuer Cron/nicht-interaktiv geeignet):
//   docker run --rm -v "$(pwd):/work" -w /work \
//     -e BREVO_LOGIN_EMAIL=... -e BREVO_LOGIN_PASSWORD=... \
//     -e BREVO_CODE_IMAP_HOST=... -e BREVO_CODE_IMAP_USER=... -e BREVO_CODE_IMAP_PASSWORD=... \
//     mcr.microsoft.com/playwright:v1.62.1-noble node setup-session.mjs
// Aufruf ohne IMAP-Konfiguration (interaktiv, Code manuell eingeben - braucht -it):
//   docker run --rm -it -v "$(pwd):/work" -w /work \
//     -e BREVO_LOGIN_EMAIL=... -e BREVO_LOGIN_PASSWORD=... \
//     mcr.microsoft.com/playwright:v1.62.1-noble node setup-session.mjs
//
// WICHTIG: Login-Formular-Selektoren sind nach bestem Wissen aus der oeffentlichen Brevo-
// Hilfe-Dokumentation entwickelt, aber nicht gegen den echten Account getestet (kein Zugriff
// auf echte Zugangsdaten) - bei Abweichungen bitte mit einem Blick auf error-*.png (wird bei
// jedem Fehler automatisch gespeichert) gemeinsam nachjustieren.

import { chromium } from 'playwright';
import { ImapFlow } from 'imapflow';
import { simpleParser } from 'mailparser';
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

// Optional: Postfach-Zugang, um den 6-stelligen Geraeteverifizierungs-Code automatisch aus der
// zugestellten E-Mail auszulesen, statt einen Menschen am Terminal zu fragen - noetig, weil sich
// per Live-Test herausgestellt hat, dass Brevo den Code fuer DIESEN Login-Flow IMMER verlangt
// (auch von einem zuvor "vertrauenswuerdigen" Geraet/derselben IP/denselben Cookies - siehe
// Kommentar unten). Ohne echte IMAP-Zugangsdaten faellt dieses Skript auf den bisherigen
// interaktiven readline-Prompt zurueck (oder scheitert klar, wenn nicht-interaktiv - TTY-Pruefung
// weiter unten).
const IMAP_HOST = process.env.BREVO_CODE_IMAP_HOST;
const IMAP_PORT = process.env.BREVO_CODE_IMAP_PORT ? Number(process.env.BREVO_CODE_IMAP_PORT) : 993;
const IMAP_USER = process.env.BREVO_CODE_IMAP_USER;
const IMAP_PASSWORD = process.env.BREVO_CODE_IMAP_PASSWORD;
const IMAP_CONFIGURED = Boolean(IMAP_HOST && IMAP_USER && IMAP_PASSWORD);

// Nutzer-Beobachtung (per Live-Test): ein bereits zugestellter Code bleibt ca. 90s gueltig,
// danach schickt Brevo einen NEUEN Code per E-Mail. Das Zeitfenster hier deckt das grosszuegig ab.
const CODE_POLL_INTERVAL_MS = 3000;
const CODE_POLL_TIMEOUT_MS = 90000;

function createImapClient() {
  return new ImapFlow({
    host: IMAP_HOST,
    port: IMAP_PORT,
    secure: true,
    auth: { user: IMAP_USER, pass: IMAP_PASSWORD },
    logger: false,
  });
}

// Muss VOR dem Login-Versuch aufgerufen werden (der erst die Code-Mail auslöst) - liefert die
// UID, ab der neu ankommende Mails als "waehrend dieses Versuchs zugestellt" gelten. Verhindert,
// dass eine bereits im Postfach liegende (evtl. laengst verbrauchte/abgelaufene) Code-Mail aus
// einem fruehreren Lauf faelschlich wiederverwendet wird.
async function getStartingUid() {
  const client = createImapClient();
  await client.connect();
  try {
    const status = await client.status('INBOX', { uidNext: true });
    return status.uidNext;
  } finally {
    await client.logout().catch(() => {});
  }
}

async function pollForVerificationCodeViaImap(sinceUid) {
  const client = createImapClient();
  await client.connect();
  try {
    const lock = await client.getMailboxLock('INBOX');
    try {
      const deadline = Date.now() + CODE_POLL_TIMEOUT_MS;
      while (Date.now() < deadline) {
        const uids = await client.search({ uid: `${sinceUid}:*` }, { uid: true });
        if (uids && uids.length > 0) {
          const newestUid = Math.max(...uids);
          const { content } = await client.download(newestUid, undefined, { uid: true });
          const parsed = await simpleParser(content);
          const text = `${parsed.text ?? ''}\n${parsed.html ?? ''}`;
          const match = text.match(/\b(\d{6})\b/);
          if (match) {
            return match[1];
          }
        }
        await new Promise(resolve => setTimeout(resolve, CODE_POLL_INTERVAL_MS));
      }
      throw new Error(`Kein Bestätigungscode innerhalb von ${CODE_POLL_TIMEOUT_MS / 1000}s per E-Mail erhalten (IMAP).`);
    } finally {
      lock.release();
    }
  } finally {
    await client.logout().catch(() => {});
  }
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
  // Weder eine realistische UA (Playwrights Standard-UA im headless Modus enthaelt woertlich
  // "HeadlessChrome/...") noch verschiedene Cookie-Kombinationen (siehe unten) waren am Ende die
  // Ursache der Geraeteverifizierung (siehe Kommentar am Dateianfang) - die eigentliche Abhilfe
  // ist die IMAP-Code-Ermittlung oben. Beide Massnahmen schaden aber nicht und bleiben als
  // sinnvolle Grundhaltung erhalten (realistischere UA, Session ueber Neustarts hinweg nicht
  // komplett verwerfen).
  const context = await browser.newContext({
    userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 '
      + '(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36',
  });

  // Alle Cookies aus einer vorhandenen storage-state.json AUSSER den drei session-spezifischen
  // (auth/ACCOUNTSESSID/loggedin) uebernehmen - die KOMPLETTE alte Sitzung (inkl. auth/loggedin)
  // liess login.brevo.com dagegen per Live-Test direkt zum Dashboard durchleiten, OHNE das
  // Formular zu zeigen (nie ein echter Login, dieselbe ungueltige Sitzung wurde unveraendert
  // erneut gesichert) - das muss also in jedem Fall vermieden werden, unabhaengig von der
  // Geraeteverifizierungs-Frage.
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

  // Muss VOR dem Login-Klick erfasst werden, der erst die Code-Mail auslöst.
  const startUid = IMAP_CONFIGURED ? await getStartingUid() : null;

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
    let code;

    if (IMAP_CONFIGURED) {
      console.log('Geraeteverifizierung erforderlich - hole Code automatisch per IMAP aus dem Postfach...');
      code = await pollForVerificationCodeViaImap(startUid);
    } else if (input.isTTY) {
      console.log('Geraeteverifizierung erforderlich - Code wurde per E-Mail an ' + EMAIL + ' geschickt.');
      const rl = readline.createInterface({ input, output });
      code = await rl.question('Bitte den 6-stelligen Code eingeben: ');
      rl.close();
    } else {
      // Ohne IMAP-Konfiguration UND ohne TTY (per Cron, ohne -it) wuerde rl.question() auf eine
      // Eingabe warten, die nie kommt - der Prozess wuerde dann bis zu einem aeusseren Timeout
      // haengen bleiben, statt klar zu scheitern. Stattdessen sofort mit einer eindeutigen
      // Fehlermeldung abbrechen.
      await saveErrorScreenshot(page, 'code-required-non-interactive');
      console.error('Geraeteverifizierung (6-stelliger Code) erforderlich, aber weder IMAP-Zugangsdaten '
        + '(BREVO_CODE_IMAP_*) konfiguriert noch ein interaktives Terminal (TTY) verfuegbar.');
      process.exit(1);
    }

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
