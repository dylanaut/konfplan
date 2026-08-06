import { test, expect } from '@playwright/test';

/**
 * Regression: api/axios.js's request interceptor used to attach ANY token found in
 * localStorage to every request, including calls to @PermitAll backend endpoints. Quarkus'
 * JWT security layer rejects a request carrying an invalid/expired Bearer token with 401
 * BEFORE the @PermitAll check even runs (verified live against a real backend) - which the
 * global response interceptor then misread as "session expired" and force-redirected away
 * from the page, breaking public link-based flows (password reset, email-change
 * confirmation) for anyone with a stale token sitting in localStorage (e.g. an expired
 * session from an earlier tab).
 *
 * These tests assert the actual fix directly: no Authorization header is sent for known
 * public endpoints, regardless of what's in localStorage. Runs against mocked APIs, no real
 * backend needed.
 */

async function withStaleToken(page) {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'garbage.invalid.token');
    localStorage.setItem('role', 'TEILNEHMER');
  });
}

test.describe('Öffentliche Endpunkte senden kein (evtl. veraltetes) Bearer-Token', () => {

  test('ResetPassword.vue sendet keinen Authorization-Header', async ({ page }) => {
    await withStaleToken(page);

    let capturedAuthHeader;
    await page.route('http://localhost:9000/api/auth/reset-password', (route) => {
      capturedAuthHeader = route.request().headers()['authorization'];
      return route.fulfill({ status: 400 });
    });

    await page.goto('/reset-password?token=irrelevant');
    await page.locator('#password').fill('whatever123');
    await page.locator('#confirmPassword').fill('whatever123');
    await page.getByRole('button', { name: 'Passwort speichern' }).click();

    await expect(page.getByText('Der Link ist abgelaufen oder ungültig.')).toBeVisible();
    expect(capturedAuthHeader).toBeUndefined();
    // Die Seite darf trotz 400-Antwort keinesfalls verlassen werden.
    await expect(page).toHaveURL(/\/reset-password/);
  });

  test('EmailChangeConfirm.vue sendet keinen Authorization-Header', async ({ page }) => {
    await withStaleToken(page);

    let capturedAuthHeader;
    await page.route('http://localhost:9000/api/teilnehmer/email-change-confirm*', (route) => {
      capturedAuthHeader = route.request().headers()['authorization'];
      return route.fulfill({ status: 400 });
    });

    await page.goto('/email-change-confirm?token=irrelevant');

    await expect(page.getByText('Der Link ist abgelaufen oder ungültig.')).toBeVisible();
    expect(capturedAuthHeader).toBeUndefined();
    await expect(page).toHaveURL(/\/email-change-confirm/);
  });

  test('Login.vue sendet keinen Authorization-Header', async ({ page }) => {
    await withStaleToken(page);

    let capturedAuthHeader;
    await page.route('http://localhost:9000/api/auth/login', (route) => {
      capturedAuthHeader = route.request().headers()['authorization'];
      return route.fulfill({ status: 401 });
    });

    await page.goto('/login');
    await page.locator('input[type="text"]').first().fill('someone');
    await page.locator('input[type="password"]').first().fill('irrelevant');

    await Promise.all([
      page.waitForResponse('http://localhost:9000/api/auth/login'),
      page.getByRole('button', { name: /anmelden|login/i }).click(),
    ]);

    expect(capturedAuthHeader).toBeUndefined();
  });
});
