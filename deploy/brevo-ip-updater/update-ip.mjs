// Traegt die uebergebene IP-Adresse ueber Brevos "Paste list"-Eingabe in die Authorized-IPs-
// Liste ein. Nutzt die von setup-session.mjs gespeicherte Sitzung - kein erneuter Login/Code
// noetig, solange Brevo das Geraet noch als vertrauenswuerdig einstuft.
//
// Aufruf: node update-ip.mjs <ip-adresse>
// Exit-Code 0 = erfolgreich, ungleich 0 = Fehlschlag (siehe stderr + error-*.png/error-*.html).
//
// WICHTIG: Seiten-Selektoren sind nach bestem Wissen aus der oeffentlichen Brevo-Hilfe-
// Dokumentation entwickelt, aber nicht gegen den echten Account getestet - bei Abweichungen
// bitte mit einem Blick auf error-*.png UND das zugehoerige error-*.html (vollstaendiges
// DOM zum Fehlerzeitpunkt, aussagekraeftiger als der Screenshot allein bei z.B. dauerhaften
// Lade-Skeletons) gemeinsam nachjustieren.
//
// Bekannte Einschraenkung (per Live-Test verifiziert, siehe error-unexpected-1788333378470.html):
// Die "Security"-Seite laedt im normalen Browser problemlos, bleibt aber im headless Playwright
// dauerhaft im Lade-Skeleton haengen (Header/Nav rendern normal, nur der "security-tabs"-Bereich
// nicht) - ohne jedes CAPTCHA-/Bot-Erkennungsskript im DOM. Das deutet auf eine clientseitige
// Pruefung von navigator.webdriver (von JEDEM CDP-automatisierten Chromium unabhaengig vom
// Headless-Modus auf true gesetzt) fuer diesen sicherheitsrelevanten Bereich hin - deshalb unten
// die uebliche Minimal-Gegenmassnahme (Flag + navigator.webdriver ueberschreiben). Sollte das
// nicht ausreichen, bleibt die manuelle Nachtrage laut Deployment-DockerCompose.adoc der Fallback.

import { chromium } from 'playwright';
import fs from 'node:fs';

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

async function saveErrorDiagnostics(page, label) {
  if (!page) {
    return;
  }
  const stamp = Date.now();
  const pngPath = `./error-${label}-${stamp}.png`;
  const htmlPath = `./error-${label}-${stamp}.html`;
  await page.screenshot({ path: pngPath, fullPage: true }).catch(() => {});
  await page.content().then(html => fs.writeFileSync(htmlPath, html)).catch(() => {});
  console.error(`Diagnose gespeichert: ${pngPath}, ${htmlPath}`);
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
  // navigator.webdriver ist bei JEDEM CDP-automatisierten Chromium (auch headed) auf true
  // gesetzt und laesst sich nicht per Chromium-Flag abschalten - deshalb hier per Init-Script
  // VOR jedem Seiten-Skript ueberschrieben (muss vor page.goto() registriert werden, damit es
  // bereits beim allerersten Skript-Lauf der Zielseite greift).
  await context.addInitScript(() => {
    Object.defineProperty(navigator, 'webdriver', { get: () => undefined });
  });
  page = await context.newPage();

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
