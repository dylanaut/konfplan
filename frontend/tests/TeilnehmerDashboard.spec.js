import { test, expect } from '@playwright/test';

/**
 * E2E-Test für das Teilnehmer-Profil (TeilnehmerDashboard.vue): die E-Mail-Adresse ist nur
 * lesbar - Änderungen laufen seit der Keycloak-Migration über Keycloaks Account-Console, nicht
 * mehr über einen in-app-Dialog. Läuft komplett gegen gemockte APIs (page.route) und einen per
 * localStorage vorgetäuschten TEILNEHMER-Login, analog zu OrganisatorDashboardModals.spec.js.
 *
 * Wichtig: die Axios-Basis-URL ist http://localhost:9000 (siehe api/axios.js), unabhängig
 * von der Playwright-baseURL (Vite-Dev-Server).
 */

const PROFILE = {
  id: 100, version: 0, loginName: 'tom.teilnehmer', email: 'tom.alt@test.de',
  firstName: 'Tom', lastName: 'Teilnehmer', role: 'TEILNEHMER', isActive: true,
  veranstaltungIds: [], gruppen: ['9a'], prioritaeten: []
};

async function mockTeilnehmerApis(page) {
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

    console.warn('[Test] Unmocked API call:', method, path);
    return json({});
  });
}

test.describe('Teilnehmer-Dashboard - Profil', () => {

  test('zeigt die E-Mail-Adresse nur lesbar an - keine Änderung im Dashboard möglich', async ({ page }) => {
    await mockTeilnehmerApis(page);
    await page.goto('/teilnehmer');
    await expect(page.getByRole('heading', { name: 'Mein Profil' })).toBeVisible();

    await expect(page.locator('input[value="tom.alt@test.de"][disabled]')).toBeVisible();
    await expect(page.getByRole('button', { name: 'E-Mail ändern' })).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Profil speichern' })).toHaveCount(0);
  });
});

/**
 * E2E-Test für die "Mein Zeitplan"-Sektion (Issue: Laufzettel soll unmittelbar angezeigt werden,
 * sobald ein Plan erstellt wurde - kein Klick auf einen Button nötig, kein Wechsel auf eine
 * separate Seite). Analog zum ReferentDashboard.spec.js-Pendant.
 */
const EVENT_MIT_PLAN = {
  id: 60, name: 'Testevent', beginntAm: '2025-10-10T09:00:00', endetAm: '2025-10-10T16:00:00',
  deadlineTeilnehmer: null, planErstellt: true, organisatoren: [], maxPrioritaeten: null,
  teilnehmerAendernVerfuegbarkeit: false,
};

async function mockTeilnehmerApisWithPlan(page, event) {
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
    if (path === '/api/teilnehmer/veranstaltungen' && method === 'GET') return json([event]);
    if (path === '/api/neigungen' && method === 'GET') return json([]);

    let m = path.match(/^\/api\/teilnehmer\/veranstaltungen\/(\d+)\/vortraege$/);
    if (m && method === 'GET') return json([]);
    m = path.match(/^\/api\/prios\/(\d+)$/);
    if (m && method === 'GET') return json({});
    m = path.match(/^\/api\/teilnehmer\/veranstaltungen\/(\d+)\/zuweisungen$/);
    if (m && method === 'GET') return json([]);

    m = path.match(/^\/api\/reports\/(\d+)\/teilnehmer\/(\d+)\/laufzettel-data$/);
    if (m && method === 'GET') {
      return json({
        veranstaltung: { id: Number(m[1]) },
        teilnehmer: PROFILE,
        plan: [
          {
            slotBeginn: '2025-10-10T12:00:00', slotEnde: '2025-10-10T13:00:00',
            vortragTitel: 'Moderne Web-Architekturen', raumName: 'Aula', referentName: 'Dr. Eva Weiss',
          },
        ],
      });
    }

    console.warn('[Test] Unmocked API call:', method, path);
    return json({});
  });
}

test.describe('Teilnehmer-Dashboard - Mein Zeitplan', () => {

  test('zeigt den Laufzettel unmittelbar an, sobald ein Plan erstellt wurde - als erste Sektion', async ({ page }) => {
    await mockTeilnehmerApisWithPlan(page, EVENT_MIT_PLAN);
    await page.goto('/teilnehmer');

    await expect(page.getByRole('heading', { name: 'Mein Zeitplan' })).toBeVisible();

    // Kein Klick nötig: der Laufzettel-Inhalt ist sofort sichtbar.
    await expect(page.getByText('Moderne Web-Architekturen')).toBeVisible();
    await expect(page.getByText('Dr. Eva Weiss')).toBeVisible();
    await expect(page.getByText('12:00 - 13:00')).toBeVisible();

    // "Mein Zeitplan" steht vor "Mein Profil".
    const headings = await page.locator('h2').allTextContents();
    expect(headings.indexOf('Mein Zeitplan')).toBeLessThan(headings.indexOf('Mein Profil'));
  });
});
