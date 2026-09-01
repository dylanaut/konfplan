// Traegt die uebergebene IP-Adresse ueber Brevos "Paste list"-Eingabe in die Authorized-IPs-
// Liste ein. Nutzt die von setup-session.mjs gespeicherte Sitzung - kein erneuter Login/Code
// noetig, solange Brevo das Geraet noch als vertrauenswuerdig einstuft.
//
// Aufruf: node update-ip.mjs <ip-adresse>
// Exit-Code 0 = erfolgreich, ungleich 0 = Fehlschlag (siehe stderr + error-*.png).
//
// WICHTIG: Seiten-Selektoren sind nach bestem Wissen aus der oeffentlichen Brevo-Hilfe-
// Dokumentation entwickelt, aber nicht gegen den echten Account getestet - bei Abweichungen
// bitte mit einem Blick auf error-*.png gemeinsam nachjustieren.

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
  const context = await browser.newContext({ storageState: STORAGE_STATE_PATH });
  page = await context.newPage();

  await page.goto(AUTHORIZED_IPS_URL, { waitUntil: 'networkidle' });

  if (page.url().includes('login')) {
    await saveErrorScreenshot(page, 'session-expired');
    console.error('Gespeicherte Sitzung ist abgelaufen/ungültig (Weiterleitung zur Login-Seite) - bitte setup-session.mjs erneut ausführen.');
    process.exit(1);
  }

  await page.getByRole('button', { name: 'Authorize IP addresses' }).click();
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
  await saveErrorScreenshot(page, 'unexpected');
  console.error('Update fehlgeschlagen:', e.message);
  process.exit(1);
} finally {
  if (browser) {
    await browser.close();
  }
}
