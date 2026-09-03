import { test, expect } from '@playwright/test';

/**
 * E2E-Tests für useInactivityLogout() (App.vue): meldet den Nutzer nach
 * INACTIVITY_TIMEOUT_MS (30 Minuten, siehe composables/useInactivityLogout.js) ohne
 * Interaktion automatisch ab. Nutzt Playwrights Clock-API, um die verstrichene Zeit
 * deterministisch zu simulieren, statt real 30 Minuten zu warten.
 *
 * Läuft ohne echtes Backend: der ORGANISATOR-Login wird per localStorage vorgetäuscht
 * (analog zu OrganisatorDashboardModals.spec.js). Die Organisator-Seiten-APIs selbst sind für dieses
 * Verhalten inhaltlich irrelevant, MÜSSEN aber trotzdem gemockt werden (nicht einfach real gegen
 * das - hier nicht mitlaufende - Backend fehlschlagen lassen): ein echtes 401 loest ueber
 * Axios' Response-Interceptor (api/axios.js) einen echten, nicht von Playwrights Fake-Clock
 * erfassten keycloak.login()-Redirect aus, der nicht-deterministisch mit den
 * Fake-Clock-Assertions unten um die Wette laeuft.
 */

const INACTIVITY_TIMEOUT_MS = 30 * 60 * 1000;

async function loginAsAdmin(page) {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'test-token');
    localStorage.setItem('role', 'ORGANISATOR');
  });
  // Pauschal leere Erfolgsantworten statt echter Backend-Aufrufe - Inhalt ist fuer diesen Test
  // irrelevant, aber ein 401 darf hier unter keinen Umstaenden auftreten (siehe Kommentar oben).
  await page.route('http://localhost:9000/api/**', route => route.fulfill({ status: 200, json: [] }));
  await page.goto('/organisator');
  await expect(page.getByRole('button', { name: 'Logout' })).toBeVisible();
}

test.describe('Inactivity-Auto-Logout', () => {
  test('meldet ohne jede Interaktion nach Ablauf des Inaktivitäts-Timeouts automatisch ab', async ({ page }) => {
    await page.clock.install();
    await loginAsAdmin(page);

    await page.clock.fastForward(INACTIVITY_TIMEOUT_MS + 1000);

    await expect(page).toHaveURL(/\/$/);
    await expect(page.getByText('Sitzung wegen Inaktivität automatisch beendet.')).toBeVisible();
  });

  test('bleibt vor Ablauf des Timeouts angemeldet', async ({ page }) => {
    await page.clock.install();
    await loginAsAdmin(page);

    await page.clock.fastForward(INACTIVITY_TIMEOUT_MS - 5000);

    await expect(page).toHaveURL(/\/organisator$/);
    await expect(page.getByRole('button', { name: 'Logout' })).toBeVisible();
  });

  test('Aktivität setzt den Inaktivitäts-Timer zurück', async ({ page }) => {
    await page.clock.install();
    await loginAsAdmin(page);

    // Kurz vor dem Timeout eine Nutzeraktivität simulieren (Mausbewegung).
    await page.clock.fastForward(INACTIVITY_TIMEOUT_MS - 5000);
    await page.mouse.move(100, 100);

    // Ohne Reset wäre die Gesamtzeit hier bereits deutlich über dem Timeout - durch den Reset
    // darf trotzdem noch keine Abmeldung erfolgt sein.
    await page.clock.fastForward(INACTIVITY_TIMEOUT_MS - 5000);
    await expect(page).toHaveURL(/\/organisator$/);

    // Jetzt den Rest des (zurückgesetzten) Timeouts ablaufen lassen -> Abmeldung erfolgt.
    await page.clock.fastForward(10000);
    await expect(page).toHaveURL(/\/$/);
  });
});
