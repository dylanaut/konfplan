import { test, expect } from '@playwright/test';

/**
 * E2E-Test für die "Mein Zeitplan"-Sektion im ReferentDashboard (Issue: Laufzettel soll
 * unmittelbar angezeigt werden, sobald ein Plan erstellt wurde - kein Klick auf einen Button
 * nötig, kein Wechsel auf eine separate Seite). Läuft komplett gegen gemockte APIs (page.route)
 * und einen per localStorage vorgetäuschten REFERENT-Login, analog zu TeilnehmerDashboard.spec.js.
 *
 * Wichtig: die Axios-Basis-URL ist http://localhost:9000 (siehe api/axios.js), unabhängig
 * von der Playwright-baseURL (Vite-Dev-Server).
 */

const REFERENT = {
  id: 300, version: 0, loginName: 'hans.referent', firstName: 'Hans', lastName: 'Referent',
  organisation: '', jobRole: '', email: '',
};

const EVENT_MIT_PLAN = {
  id: 50, name: 'Testevent', beginntAm: '2025-10-10T09:00:00', endetAm: '2025-10-10T16:00:00',
  deadlineReferenten: null, planErstellt: true,
};

const EVENT_OHNE_PLAN = {
  id: 51, name: 'Zukunftsevent', beginntAm: '2025-11-11T09:00:00', endetAm: '2025-11-11T16:00:00',
  deadlineReferenten: null, planErstellt: false,
};

async function mockReferentApis(page, events) {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'test-token');
    localStorage.setItem('role', 'REFERENT');
  });

  await page.route('http://localhost:9000/api/**', async (route) => {
    const req = route.request();
    const method = req.method();
    const path = new URL(req.url()).pathname;
    const json = (data, status = 200) => route.fulfill({ status, json: data ?? {} });

    if (path === '/api/referenten/profile' && method === 'GET') return json(REFERENT);
    if (path === '/api/referenten/veranstaltungen' && method === 'GET') return json(events);
    if (path === '/api/referenten/vortraege' && method === 'GET') return json([]);
    if (path === '/api/slots' && method === 'GET') return json([]);
    if (path === '/api/neigungen' && method === 'GET') return json([]);

    let m = path.match(/^\/api\/referenten\/veranstaltungen\/(\d+)\/verfuegbarkeiten$/);
    if (m && method === 'GET') return json({ verfuegbareSlotIds: [] });

    m = path.match(/^\/api\/reports\/(\d+)\/referent\/(\d+)\/laufzettel-data$/);
    if (m && method === 'GET') {
      return json({
        veranstaltung: { id: Number(m[1]) },
        referent: REFERENT,
        plan: [
          {
            slotBeginn: '2025-10-10T12:00:00', slotEnde: '2025-10-10T13:00:00',
            vortragTitel: 'Moderne Web-Architekturen', raumName: 'Aula',
          },
        ],
      });
    }

    console.warn('[Test] Unmocked API call:', method, path);
    return json({});
  });
}

test.describe('Referent-Dashboard - Mein Zeitplan', () => {

  test('zeigt den Laufzettel unmittelbar an, sobald ein Plan erstellt wurde - als erste Sektion', async ({ page }) => {
    await mockReferentApis(page, [EVENT_MIT_PLAN]);
    await page.goto('/referent');

    await expect(page.getByRole('heading', { name: 'Mein Zeitplan' })).toBeVisible();

    // Kein Klick nötig: der Laufzettel-Inhalt ist sofort sichtbar.
    await expect(page.getByText('Moderne Web-Architekturen')).toBeVisible();
    await expect(page.getByText('Aula')).toBeVisible();
    await expect(page.getByText('12:00 - 13:00')).toBeVisible();

    // "Mein Zeitplan" steht vor "Persönliches Profil".
    const headings = await page.locator('h2').allTextContents();
    expect(headings.indexOf('Mein Zeitplan')).toBeLessThan(headings.indexOf('Persönliches Profil'));
  });

  test('zeigt einen Platzhalter, solange noch kein Plan erstellt wurde', async ({ page }) => {
    await mockReferentApis(page, [EVENT_OHNE_PLAN]);
    await page.goto('/referent');

    await expect(page.getByRole('heading', { name: 'Mein Zeitplan' })).toBeVisible();
    await expect(page.getByText('Plan noch nicht verfügbar')).toBeVisible();
  });
});
