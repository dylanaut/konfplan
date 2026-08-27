// Nimmt den Teilnehmer-Kern-Workflow (Erstanmeldung -> Profil/Neigungen -> Verfügbarkeit ->
// Prioritäten) als Video auf, passend zum Erklärvideo-Skript
// (backend/src/main/asciidoc/docx/KonfPlan_Erklaervideo_Teilnehmer_Skript.docx).
//
// Kein eigener "playwright"-Dependency nötig - @playwright/test (bereits in frontend/
// installiert, siehe package.json) re-exportiert dieselbe chromium-API auch außerhalb des
// Testrunners.
//
// Voraussetzung: Backend läuft im Dev-Modus (siehe CLAUDE.md: `cd backend && ../mvnw quarkus:dev`)
// mit einem Teilnehmer-Testkonto in der gewünschten Veranstaltung (Neigungen, Vorträge und Slots
// müssen vorhanden sein, damit die entsprechenden Bildschirme etwas zu zeigen haben).
//
// Aufruf (aus frontend/):
//   TEILNEHMER_LOGIN=max.mustermann TEILNEHMER_PASSWORD=konfplan node scripts/record-teilnehmer-demo.mjs
//
// Falls das Testkonto ein TEMPORÄRES Passwort hat (Keycloak UPDATE_PASSWORD Required Action,
// z.B. frisch per Bulk-Passwort-Reset erzeugt), zusätzlich TEILNEHMER_NEW_PASSWORD setzen - das
// Skript prüft automatisch, ob dieser Zwischenschritt erscheint, und überspringt ihn sonst.
//
// WICHTIG: Alle "Speichern"-Aktionen im Teilnehmer-Dashboard nutzen native alert()-Dialoge
// (kein Toast) - ohne automatisches Bestätigen (siehe page.on('dialog', ...) unten) würde die
// Seite an jeder Speichern-Aktion hängen bleiben.

import { chromium } from '@playwright/test';

const BASE_URL = process.env.BASE_URL || 'http://localhost:9000';
const LOGIN_NAME = process.env.TEILNEHMER_LOGIN;
const PASSWORD = process.env.TEILNEHMER_PASSWORD;
const NEW_PASSWORD = process.env.TEILNEHMER_NEW_PASSWORD; // nur nötig, falls Zwangswechsel aktiv ist
const VIDEO_DIR = './videos';

// Pause zwischen Aktionen, damit man im Video gut folgen kann (Millisekunden).
const SHORT_PAUSE = 1500;
const LONG_PAUSE = 3000;
const pause = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

// Viewport UND recordVideo.size müssen übereinstimmen - sonst skaliert/beschneidet Playwright
// das Video gegenüber dem tatsächlich sichtbaren Fensterinhalt (ohne dies blieb das Fenster
// beim Rendern auf der Playwright-Standardgröße 1280x720, unabhängig von recordVideo.size).
const VIEWPORT = { width: 1280, height: 720 };

if (!LOGIN_NAME || !PASSWORD) {
  console.error('TEILNEHMER_LOGIN und TEILNEHMER_PASSWORD müssen gesetzt sein.');
  process.exit(1);
}

async function saveErrorScreenshot(page, label) {
  const path = `./error-${label}-${Date.now()}.png`;
  await page.screenshot({ path }).catch(() => {});
  console.error(`Screenshot gespeichert: ${path}`);
}

// Scrollt ein Element mittig in die Anzeige, statt es (wie die automatische Actionability-Prüfung
// von Playwright) nur gerade eben in den sichtbaren Bereich zu schieben - im Video bleibt so immer
// das aktuell bearbeitete Eingabefeld im Blickfeld.
async function focusCenter(locator) {
  await locator.evaluate((el) => el.scrollIntoView({ block: 'center', inline: 'center', behavior: 'smooth' }));
  await pause(500); // Zeit für den smooth-scroll, bevor die eigentliche Interaktion beginnt
}

const browser = await chromium.launch({ headless: false, args: [`--window-size=${VIEWPORT.width},${VIEWPORT.height}`] });
// ignoreHTTPSErrors: falls die lokale Instanz mit selbstsigniertem Zertifikat über HTTPS läuft.
const context = await browser.newContext({
  ignoreHTTPSErrors: true,
  viewport: VIEWPORT,
  recordVideo: { dir: VIDEO_DIR, size: VIEWPORT },
});
const page = await context.newPage();

// Alle "Speichern"-Buttons im Teilnehmer-Dashboard lösen native alert()-Dialoge aus.
page.on('dialog', async (dialog) => {
  console.log(`Dialog erschienen: "${dialog.message()}" - wird automatisch bestätigt.`);
  await pause(SHORT_PAUSE); // kurze Pause, damit der Dialog im Video sichtbar bleibt
  await dialog.accept();
});

try {
  // 1. Erstanmeldung: Startseite öffnen
  console.log('Öffne Startseite...');
  await page.goto(BASE_URL, { waitUntil: 'networkidle' });
  await pause(LONG_PAUSE);

  await page.getByRole('button', { name: 'Anmelden' }).click();
  await pause(LONG_PAUSE);

  // 2. Keycloak-Anmeldeseite: Anmeldename + einmaliges/aktuelles Passwort
  console.log('Fülle Keycloak-Login...');
  const usernameField = page.locator('#username');
  await focusCenter(usernameField);
  await usernameField.fill(LOGIN_NAME);
  await pause(SHORT_PAUSE);
  const passwordField = page.locator('#password');
  await focusCenter(passwordField);
  await passwordField.fill(PASSWORD);
  await pause(SHORT_PAUSE);
  await page.locator('#kc-login').click();
  await pause(LONG_PAUSE);

  // 3. Passwortvergabe: Keycloak zeigt diese Seite NUR, wenn das Konto ein temporäres
  // Passwort hat (UPDATE_PASSWORD Required Action) - daher defensiv prüfen statt vorauszusetzen.
  const passwordUpdateVisible = await page.locator('#password-new')
    .isVisible({ timeout: 5000 }).catch(() => false);

  if (passwordUpdateVisible) {
    if (!NEW_PASSWORD) {
      throw new Error('Konto verlangt ein neues Passwort (Zwangswechsel), aber TEILNEHMER_NEW_PASSWORD ist nicht gesetzt.');
    }
    console.log('Zwangs-Passwortwechsel erkannt, vergebe neues Passwort...');
    const newPasswordField = page.locator('#password-new');
    await focusCenter(newPasswordField);
    await newPasswordField.fill(NEW_PASSWORD);
    await pause(SHORT_PAUSE);
    const confirmPasswordField = page.locator('#password-confirm');
    await focusCenter(confirmPasswordField);
    await confirmPasswordField.fill(NEW_PASSWORD);
    await pause(SHORT_PAUSE);
    await page.locator('#kc-login, input[type="submit"]').first().click();
    await pause(LONG_PAUSE);
  } else {
    console.log('Kein Zwangs-Passwortwechsel nötig, weiter mit der Anmeldung.');
  }

  // 4. Anmeldung abgeschlossen: Weiterleitung auf das Teilnehmer-Dashboard abwarten.
  await page.getByText('Mein Profil').first().waitFor({ timeout: 15000 });
  console.log('Teilnehmer-Dashboard geladen.');
  await pause(LONG_PAUSE);

  // 6. Eintrag der Neigungen: die ersten beiden Neigungen-Checkboxen ankreuzen.
  console.log('Wähle Neigungen aus...');
  const neigungCheckboxes = page.locator('input[id^="profile-neigung-"]');
  const neigungCount = await neigungCheckboxes.count();
  for (let i = 0; i < Math.min(2, neigungCount); i++) {
    const checkbox = neigungCheckboxes.nth(i);
    await focusCenter(checkbox);
    await checkbox.check();
    await pause(SHORT_PAUSE);
  }

  await page.getByRole('button', { name: 'Speichern', exact: true }).click();
  await pause(LONG_PAUSE); // Zeit für den alert()-Dialog (wird oben automatisch bestätigt)

  // 7. Bearbeitung der Verfügbarkeit: erste Veranstaltung aufklappen.
  console.log('Öffne Verfügbarkeit...');
  const availabilityToggle = page.getByRole('button', { name: /Meine Verfügbarkeit/ }).first();
  await availabilityToggle.click();
  await pause(LONG_PAUSE);

  // Auf das direkt unter dem Toggle-Button eingeblendete Panel scopen - sonst trifft der generische
  // Checkbox-Selektor zuerst die bereits angehakten Neigungen-Checkboxen weiter oben im DOM (togglet
  // sie versehentlich wieder aus, ohne dass sich an der Verfügbarkeit etwas ändert -> "Verfügbarkeit
  // speichern" bleibt disabled, siehe TeilnehmerDashboard.vue: v-if="activeAvailabilityEventId===...").
  const availabilityPanel = availabilityToggle.locator('xpath=following-sibling::div[1]');
  // Nur nicht deaktivierte (= keine Pflichtvorträge) Checkboxen sind änderbar.
  const availabilityCheckboxes = availabilityPanel.locator('input[type="checkbox"]:not([disabled])');
  const availabilityCount = await availabilityCheckboxes.count();
  for (let i = 0; i < Math.min(2, availabilityCount); i++) {
    const checkbox = availabilityCheckboxes.nth(i);
    await focusCenter(checkbox);
    await checkbox.click();
    await pause(SHORT_PAUSE);
  }

  await page.getByRole('button', { name: /Verfügbarkeit speichern/ }).click();
  await pause(LONG_PAUSE);

  // 8. Vergabe von Prioritäten: dieselbe Veranstaltung, Abschnitt "Vorträge & Prioritäten".
  console.log('Öffne Vorträge & Prioritäten...');
  await page.getByRole('button', { name: /Vorträge & Prioritäten/ }).first().click();
  await pause(LONG_PAUSE);

  const prioInputs = page.locator('input.prio-input');
  const prioCount = await prioInputs.count();
  const beispielWerte = [10, 8];
  for (let i = 0; i < Math.min(beispielWerte.length, prioCount); i++) {
    const prioInput = prioInputs.nth(i);
    await focusCenter(prioInput);
    await prioInput.fill(String(beispielWerte[i]));
    await pause(SHORT_PAUSE);
  }

  await page.getByRole('button', { name: /Meine Prioritäten speichern/ }).click();
  await pause(LONG_PAUSE);

  // 9. Abschluss: Dialog mit Logout beenden (Button liegt im globalen Nav aus App.vue, auf allen
  // authentifizierten Seiten sichtbar - siehe auth.logout() in stores/auth.js).
  console.log('Melde ab...');
  const logoutButton = page.getByRole('button', { name: /logout/i }).first();
  await focusCenter(logoutButton);
  await logoutButton.click();
  // auth.logout() kann bei aktiver Keycloak-SSO-Sitzung einen vollen Seiten-Redirect auslösen
  // (nicht nur einen SPA-Routenwechsel) - daher großzügig warten, aber ein Timeout hier nicht als
  // Fehler werten, da der Ablauf inhaltlich bereits abgeschlossen ist.
  await page.getByRole('button', { name: 'Anmelden' }).first().waitFor({ timeout: 10000 }).catch(() => {});
  await pause(LONG_PAUSE);
  console.log('Ablauf abgeschlossen.');
} catch (e) {
  await saveErrorScreenshot(page, 'unexpected');
  console.error('Aufnahme fehlgeschlagen:', e.message);
  process.exitCode = 1;
} finally {
  const videoPath = `${VIDEO_DIR}/teilnehmer-demo.webm`;
  await page.video()?.saveAs(videoPath).catch(() => {});
  await context.close();
  await browser.close();
  console.log(`Video gespeichert unter: ${videoPath}`);
  console.log('Bitte beenden...');

  // browser.close() beendet zwar den Chromium-Prozess, aber vereinzelt bleiben danach noch
  // offene Handles (z.B. vom Video-Encoder) im Node-Event-Loop haengen, sodass der Prozess
  // nicht von selbst zur Konsole zurueckkehrt - daher hier explizit erzwingen.
  process.exit(process.exitCode ?? 0);
}
