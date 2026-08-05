import { test, expect } from '@playwright/test';

/**
 * E2E-Tests für den Teilnehmer-Self-Service-E-Mail-Änderungs-Flow (TeilnehmerDashboard.vue +
 * EmailChangeConfirm.vue). Läuft komplett gegen gemockte APIs (page.route) und einen per
 * localStorage vorgetäuschten TEILNEHMER-Login, analog zu AdminDashboardModals.spec.js.
 *
 * Wichtig: die Axios-Basis-URL ist http://localhost:9000 (siehe api/axios.js), unabhängig
 * von der Playwright-baseURL (Vite-Dev-Server).
 */

const PROFILE = {
  id: 100, version: 0, loginName: 'tom.teilnehmer', email: 'tom.alt@test.de',
  firstName: 'Tom', lastName: 'Teilnehmer', role: 'TEILNEHMER', isActive: true,
  veranstaltungIds: [], gruppen: ['9a'], prioritaeten: []
};

async function mockTeilnehmerApis(page, { emailChangeStatus = 200, emailChangeBody = '' } = {}) {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'test-token');
    localStorage.setItem('role', 'TEILNEHMER');
  });

  await page.route('http://localhost:9000/api/**', async (route) => {
    const req = route.request();
    const method = req.method();
    const path = new URL(req.url()).pathname;
    const json = (data, status = 200) => route.fulfill({ status, json: data ?? {} });

    if (path === '/api/teilnehmer/profile' && method === 'GET') return json(PROFILE);
    if (path === '/api/teilnehmer/veranstaltungen' && method === 'GET') return json([]);
    if (path === '/api/teilnehmer/email-change-request' && method === 'POST') {
      return route.fulfill({ status: emailChangeStatus, body: emailChangeBody });
    }

    console.warn('[Test] Unmocked API call:', method, path);
    return json({});
  });
}

async function gotoTeilnehmerDashboard(page) {
  await page.goto('/teilnehmer');
  await expect(page.getByRole('heading', { name: 'Mein Profil' })).toBeVisible();
}

test.describe('Teilnehmer-Dashboard - E-Mail-Änderung', () => {

  test('zeigt die aktuelle E-Mail-Adresse nur lesbar an, kein direktes Bearbeiten möglich', async ({ page }) => {
    await mockTeilnehmerApis(page);
    await gotoTeilnehmerDashboard(page);

    // Die E-Mail steht in einem deaktivierten Feld - Änderungen laufen ausschließlich über den
    // "E-Mail ändern"-Dialog, nicht über direktes Editieren + allgemeines Profil-Speichern.
    await expect(page.locator('input[value="tom.alt@test.de"][disabled]')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Profil speichern' })).toHaveCount(0);
  });

  test('öffnet das Änderungsformular, sendet die Anfrage und zeigt eine Erfolgsmeldung', async ({ page }) => {
    await mockTeilnehmerApis(page);
    await gotoTeilnehmerDashboard(page);

    await page.getByRole('button', { name: 'E-Mail ändern' }).click();
    await expect(page.locator('input[type="email"]')).toBeVisible();

    await page.locator('input[type="email"]').fill('tom.neu@test.de');
    await page.locator('input[type="password"]').fill('correctPassword');

    let dialogMessage = '';
    page.once('dialog', dialog => {
      dialogMessage = dialog.message();
      dialog.accept();
    });

    const [request] = await Promise.all([
      page.waitForRequest(req => req.url().endsWith('/api/teilnehmer/email-change-request') && req.method() === 'POST'),
      page.getByRole('button', { name: 'Bestätigungs-E-Mail senden' }).click()
    ]);
    expect(request.postDataJSON()).toEqual({ newEmail: 'tom.neu@test.de', currentPassword: 'correctPassword' });

    await expect.poll(() => dialogMessage).toContain('Bestätigungs-E-Mail wurde an die neue Adresse gesendet');
    // Formular schließt sich nach Erfolg und wird zurückgesetzt.
    await expect(page.locator('input[type="email"]')).toHaveCount(0);
  });

  test('Abbrechen schließt das Formular ohne einen Request auszulösen', async ({ page }) => {
    await mockTeilnehmerApis(page);
    await gotoTeilnehmerDashboard(page);

    let requestSent = false;
    page.on('request', (req) => {
      if (req.url().endsWith('/api/teilnehmer/email-change-request')) requestSent = true;
    });

    await page.getByRole('button', { name: 'E-Mail ändern' }).click();
    await page.locator('input[type="email"]').fill('tom.neu@test.de');
    await page.getByRole('button', { name: 'Abbrechen' }).click();

    await expect(page.locator('input[type="email"]')).toHaveCount(0);
    expect(requestSent).toBe(false);
  });

  test('zeigt eine Fehlermeldung bei falschem Passwort und lässt das Formular offen', async ({ page }) => {
    // Bewusst 403, nicht 401: der globale Response-Interceptor in axios.js behandelt jedes 401
    // als ungültiges Token und meldet sofort ab (siehe Kommentar in TeilnehmerResource).
    await mockTeilnehmerApis(page, { emailChangeStatus: 403, emailChangeBody: 'Aktuelles Passwort ist falsch.' });
    await gotoTeilnehmerDashboard(page);

    await page.getByRole('button', { name: 'E-Mail ändern' }).click();
    await page.locator('input[type="email"]').fill('tom.neu@test.de');
    await page.locator('input[type="password"]').fill('wrongPassword');

    let dialogMessage = '';
    page.once('dialog', dialog => {
      dialogMessage = dialog.message();
      dialog.accept();
    });
    await page.getByRole('button', { name: 'Bestätigungs-E-Mail senden' }).click();

    await expect.poll(() => dialogMessage).toContain('Aktuelles Passwort ist falsch.');
    // Formular bleibt bei einem Fehler offen, damit der Nutzer korrigieren kann.
    await expect(page.locator('input[type="email"]')).toBeVisible();
  });
});
