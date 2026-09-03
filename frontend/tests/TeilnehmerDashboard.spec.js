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
