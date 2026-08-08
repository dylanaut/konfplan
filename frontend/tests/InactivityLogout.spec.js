import { test, expect } from '@playwright/test';

/**
 * E2E-Tests für useInactivityLogout() (App.vue): meldet den Nutzer nach
 * INACTIVITY_TIMEOUT_MS (30 Minuten, siehe composables/useInactivityLogout.js) ohne
 * Interaktion automatisch ab. Nutzt Playwrights Clock-API, um die verstrichene Zeit
 * deterministisch zu simulieren, statt real 30 Minuten zu warten.
 *
 * Läuft ohne echtes Backend: der ADMIN-Login wird per localStorage vorgetäuscht
 * (analog zu AdminDashboardModals.spec.js); alle API-Aufrufe der Admin-Seite selbst sind für
 * dieses Verhalten irrelevant und dürfen fehlschlagen (kein Backend nötig), solange die
 * authentifizierte Navigationsleiste (und damit App.vue) rendert.
 */

const INACTIVITY_TIMEOUT_MS = 30 * 60 * 1000;

async function loginAsAdmin(page) {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'test-token');
    localStorage.setItem('role', 'ADMIN');
  });
  await page.goto('/admin');
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

    await expect(page).toHaveURL(/\/admin$/);
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
    await expect(page).toHaveURL(/\/admin$/);

    // Jetzt den Rest des (zurückgesetzten) Timeouts ablaufen lassen -> Abmeldung erfolgt.
    await page.clock.fastForward(10000);
    await expect(page).toHaveURL(/\/$/);
  });
});
