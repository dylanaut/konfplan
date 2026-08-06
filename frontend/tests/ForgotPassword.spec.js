import { test, expect } from '@playwright/test';

/**
 * E2E-Test für Login.vue's "Passwort vergessen?"-Formular gegen das neue
 * Rate-Limiting von POST /api/auth/forgot-password (ForgotPasswordRateLimiterService,
 * siehe AuthResource.forgotPassword): bei HTTP 429 zeigt handleForgot() eine eigene
 * Fehlermeldung mit der aus dem Retry-After-Header abgeleiteten Wartezeit, statt der
 * generischen "Emailversand fehlgeschlagen"-Meldung. Läuft komplett gegen eine gemockte
 * API, kein echtes Backend nötig.
 */

async function openForgotPasswordForm(page) {
  await page.goto('/login');
  await page.getByText('Passwort vergessen?').click();
  await expect(page.getByRole('heading', { name: 'Passwort zurücksetzen' })).toBeVisible();
}

test.describe('Login.vue - Passwort vergessen bei Rate-Limit (429)', () => {
  test('zeigt die Wartezeit aus dem Retry-After-Header an', async ({ page }) => {
    // access-control-expose-headers ist erforderlich, damit der Browser (Frontend/Backend
    // laufen auf unterschiedlichen Origins: 5173 vs. 9000) den custom Retry-After-Header per
    // fetch/XHR ueberhaupt lesen darf - simuliert hier das echte, jetzt per
    // quarkus.http.cors.exposed-headers gesetzte Backend-Verhalten.
    await page.route('http://localhost:9000/api/auth/forgot-password*', (route) =>
      route.fulfill({
        status: 429,
        headers: { 'retry-after': '300', 'access-control-expose-headers': 'Retry-After' },
      })
    );

    await openForgotPasswordForm(page);
    await page.locator('input[type="text"]').fill('irgendjemand');

    await Promise.all([
      page.waitForResponse('http://localhost:9000/api/auth/forgot-password*'),
      page.getByRole('button', { name: 'Link anfordern' }).click(),
    ]);

    await expect(page.getByText('Zu viele Anfragen. Bitte in 5 Minute(n) erneut versuchen.')).toBeVisible();
    // Die generische Erfolgsmeldung darf bei einem Fehler nicht zusätzlich erscheinen.
    await expect(page.getByText('Falls der Anmeldename registriert ist')).not.toBeVisible();
  });

  test('zeigt eine generische Wartemeldung ohne verwertbaren Retry-After-Header', async ({ page }) => {
    await page.route('http://localhost:9000/api/auth/forgot-password*', (route) =>
      route.fulfill({ status: 429 })
    );

    await openForgotPasswordForm(page);
    await page.locator('input[type="text"]').fill('irgendjemand');

    await Promise.all([
      page.waitForResponse('http://localhost:9000/api/auth/forgot-password*'),
      page.getByRole('button', { name: 'Link anfordern' }).click(),
    ]);

    await expect(page.getByText('Zu viele Anfragen. Bitte in einigen Minuten erneut versuchen.')).toBeVisible();
  });

  test('zeigt bei Erfolg weiterhin die normale Erfolgsmeldung', async ({ page }) => {
    await page.route('http://localhost:9000/api/auth/forgot-password*', (route) =>
      route.fulfill({ status: 202 })
    );

    await openForgotPasswordForm(page);
    await page.locator('input[type="text"]').fill('irgendjemand');

    await Promise.all([
      page.waitForResponse('http://localhost:9000/api/auth/forgot-password*'),
      page.getByRole('button', { name: 'Link anfordern' }).click(),
    ]);

    await expect(page.getByText('Anfrage gesendet. Bitte prüfen Sie ggf. Ihr Postfach.')).toBeVisible();
  });
});
