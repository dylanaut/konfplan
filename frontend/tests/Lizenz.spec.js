import { test, expect } from '@playwright/test';

/**
 * E2E-Tests für die Lizenzverwaltung (Administrator): Banner und Modal im OrganisatorDashboard.
 * Läuft komplett gegen gemockte APIs (page.route), analog zu OrganisatorDashboardModals.spec.js -
 * die Axios-Basis-URL ist http://localhost:9000, daher die volle Origin im Route-Muster.
 */

const ANFORDERUNG = { instanzId: '11111111-2222-3333-4444-555555555555', publicKey: 'MCowBQYDK2VwAyEAdGVzdA==', thumbprint: 'x'.repeat(43) };

const inTagen = (tage) => new Date(Date.now() + tage * 24 * 60 * 60 * 1000).toISOString();

const LIZENZIERT = (tage) => ({
  zustand: 'LIZENZIERT', grund: null, durchsetzung: true, lizenzId: 'LIC-1', kundenId: 'CUST-1',
  maxInstanzen: 3, features: ['planung'], typ: 'OFFLINE', gueltigBis: inTagen(tage)
});

const UNLIZENZIERT = { zustand: 'UNLIZENZIERT', grund: null, durchsetzung: true };

/** @param {import('@playwright/test').Page} page */
async function mockApis(page, rolle, startStatus, putAntwort, handlers = {}) {
  await page.addInitScript((r) => {
    localStorage.setItem('token', 'test-token');
    localStorage.setItem('role', r);
  }, rolle);

  let status = startStatus;
  await page.route('http://localhost:9000/api/**', async (route) => {
    const req = route.request();
    const method = req.method();
    const path = new URL(req.url()).pathname;
    const json = (data, code = 200) => route.fulfill({ status: code, json: data });

    if (path === '/api/lizenz' && method === 'GET') return json(status);
    if (path === '/api/lizenz/anforderung') return json(ANFORDERUNG);
    if (path === '/api/lizenz' && method === 'PUT') {
      const antwort = putAntwort(req.postData());
      if (antwort.status === 200) status = antwort.body;
      return json(antwort.body, antwort.status);
    }
    if (path === '/api/lizenz/aktivierung' && method === 'POST') {
      const a = handlers.aktivierung(JSON.parse(req.postData() || '{}'));
      if (a.status === 200) status = a.body;
      return json(a.body, a.status);
    }
    if (path === '/api/lizenz/revalidierung' && method === 'POST') {
      const a = handlers.revalidierung();
      if (a.status === 200) status = a.body;
      return json(a.body, a.status);
    }
    if (path === '/api/lizenz/aktivierung' && method === 'DELETE') {
      const a = handlers.deaktivierung(new URL(req.url()).searchParams.get('nurLokal') === 'true');
      if (a.status === 200) status = a.body;
      return json(a.body, a.status);
    }
    if (path === '/api/wartungshinweis') return json({});
    if (path === '/api/nachrichten/ungelesen-anzahl') return json(0);
    return json(method === 'GET' ? [] : {});
  });
}

test.describe('Lizenzverwaltung', () => {
  test('Administrator ohne Lizenz sieht Sperr-Banner, der Link öffnet die Lizenzverwaltung', async ({ page }) => {
    await mockApis(page, 'ADMINISTRATOR', UNLIZENZIERT, () => ({ status: 400, body: {} }));
    await page.goto('/organisator');

    const banner = page.getByTestId('lizenz-banner');
    await expect(banner).toContainText('keine Lizenz installiert');
    await expect(banner).toContainText('gesperrt');

    await banner.getByRole('link', { name: 'Lizenz verwalten' }).click();
    await expect(page.getByRole('heading', { name: 'Lizenz', exact: true })).toBeVisible();
    await expect(page.getByTestId('lizenz-zustand')).toHaveText('Keine Lizenz');
    await expect(page.getByTestId('lizenz-instanz-id')).toHaveText(ANFORDERUNG.instanzId);
  });

  test('ungültige Lizenz zeigt die Fehlermeldung, gültige wird installiert und das Banner verschwindet', async ({ page }) => {
    await mockApis(page, 'ADMINISTRATOR', UNLIZENZIERT, (body) => body === 'gueltig.test.lizenz'
      ? { status: 200, body: LIZENZIERT(200) }
      : { status: 400, body: { error: 'Die Lizenzsignatur ist ungültig.' } });
    await page.goto('/organisator');
    await page.getByTestId('lizenz-button').click();

    await page.getByTestId('lizenz-text').fill('kaputt');
    await page.getByTestId('lizenz-installieren').click();
    await expect(page.getByTestId('lizenz-fehler')).toHaveText('Die Lizenzsignatur ist ungültig.');
    await expect(page.getByTestId('lizenz-zustand')).toHaveText('Keine Lizenz');

    await page.getByTestId('lizenz-text').fill('gueltig.test.lizenz');
    await page.getByTestId('lizenz-installieren').click();
    await expect(page.getByTestId('lizenz-erfolg')).toBeVisible();
    await expect(page.getByTestId('lizenz-zustand')).toHaveText('Lizenziert');
    await expect(page.getByText('CUST-1')).toBeVisible();
  });

  test('eingeschränkte Lizenz nennt den Grund', async ({ page }) => {
    await mockApis(page, 'ADMINISTRATOR', { zustand: 'EINGESCHRAENKT', grund: 'ABGELAUFEN', durchsetzung: true }, () => ({ status: 400, body: {} }));
    await page.goto('/organisator');
    await page.getByTestId('lizenz-button').click();

    await expect(page.getByTestId('lizenz-zustand')).toHaveText('Eingeschränkt');
    await expect(page.getByTestId('lizenz-status')).toContainText('Die Lizenz ist abgelaufen.');
    await expect(page.getByTestId('lizenz-banner')).toContainText('nicht gültig');
  });

  test('bald ablaufende Lizenz zeigt eine Warnung, auch ohne scharfe Schreibsperre', async ({ page }) => {
    await mockApis(page, 'ADMINISTRATOR', { ...LIZENZIERT(10), durchsetzung: false }, () => ({ status: 400, body: {} }));
    await page.goto('/organisator');

    await expect(page.getByTestId('lizenz-banner')).toContainText('läuft in 10 Tagen ab');
  });

  test('ohne scharfe Sperre und ohne Lizenz erscheint kein Banner', async ({ page }) => {
    await mockApis(page, 'ADMINISTRATOR', { ...UNLIZENZIERT, durchsetzung: false }, () => ({ status: 400, body: {} }));
    await page.goto('/organisator');

    await expect(page.getByTestId('lizenz-button')).toBeVisible();
    await expect(page.getByTestId('lizenz-banner')).toHaveCount(0);
  });

  test('gültige Lizenz mit langer Laufzeit zeigt kein Banner', async ({ page }) => {
    await mockApis(page, 'ADMINISTRATOR', LIZENZIERT(200), () => ({ status: 400, body: {} }));
    await page.goto('/organisator');

    await expect(page.getByTestId('lizenz-button')).toBeVisible();
    await expect(page.getByTestId('lizenz-banner')).toHaveCount(0);
  });

  test('Organisator sieht weder Lizenz-Button noch Banner', async ({ page }) => {
    await mockApis(page, 'ORGANISATOR', UNLIZENZIERT, () => ({ status: 400, body: {} }));
    await page.goto('/organisator');

    await expect(page.getByRole('heading', { name: 'Organisator-Bereich' })).toBeVisible();
    await expect(page.getByTestId('lizenz-button')).toHaveCount(0);
    await expect(page.getByTestId('lizenz-banner')).toHaveCount(0);
  });

  test.describe('Online-Lizenz', () => {
    const ONLINE = (extra = {}) => ({
      ...LIZENZIERT(20), typ: 'ONLINE', onlineMoeglich: true, onlineAktiviert: true,
      naechsteRevalidierung: inTagen(5), ...extra
    });
    const OHNE_LIZENZ_ONLINE = { ...UNLIZENZIERT, onlineMoeglich: true, onlineAktiviert: false };
    const KEIN = () => ({ status: 400, body: {} });

    test('ohne konfigurierten Lizenzserver gibt es keinen Online-Bereich', async ({ page }) => {
      await mockApis(page, 'ADMINISTRATOR', UNLIZENZIERT, KEIN);
      await page.goto('/organisator');
      await page.getByTestId('lizenz-button').click();

      await expect(page.getByTestId('lizenz-zustand')).toBeVisible();
      await expect(page.getByTestId('lizenz-online')).toHaveCount(0);
    });

    test('Online-Aktivierung: falscher Schlüssel zeigt die Serverfehler, richtiger aktiviert', async ({ page }) => {
      await mockApis(page, 'ADMINISTRATOR', OHNE_LIZENZ_ONLINE, KEIN, {
        aktivierung: (body) => body.lizenzschluessel === 'GUT'
          ? { status: 200, body: ONLINE() }
          : { status: 400, body: { error: 'Unbekannter Lizenzschlüssel.' } }
      });
      await page.goto('/organisator');
      await page.getByTestId('lizenz-button').click();

      await expect(page.getByTestId('lizenz-aktivieren')).toBeDisabled();
      await page.getByTestId('lizenz-schluessel').fill('SCHLECHT');
      await page.getByTestId('lizenz-aktivieren').click();
      await expect(page.getByTestId('lizenz-fehler')).toHaveText('Unbekannter Lizenzschlüssel.');
      await expect(page.getByTestId('lizenz-zustand')).toHaveText('Keine Lizenz');

      await page.getByTestId('lizenz-schluessel').fill('GUT');
      await page.getByTestId('lizenz-aktivieren').click();
      await expect(page.getByTestId('lizenz-erfolg')).toHaveText('Die Instanz wurde online aktiviert.');
      await expect(page.getByTestId('lizenz-zustand')).toHaveText('Lizenziert');
      // Statt des Aktivierungsfelds jetzt Prüfen/Entfernen.
      await expect(page.getByTestId('lizenz-schluessel')).toHaveCount(0);
      await expect(page.getByTestId('lizenz-revalidieren')).toBeVisible();
    });

    test('fällige Revalidierung (GRACE) wird im Modal und im Banner angezeigt, die Anwendung bleibt nutzbar', async ({ page }) => {
      await mockApis(page, 'ADMINISTRATOR', ONLINE({ zustand: 'GRACE', naechsteRevalidierung: inTagen(-1), revalidierungFehler: 'Der Lizenzserver ist nicht erreichbar.' }), KEIN);
      await page.goto('/organisator');

      await expect(page.getByTestId('lizenz-banner')).toContainText('Lizenzserver nicht erreichbar');
      await expect(page.getByTestId('lizenz-banner')).toContainText('voll nutzbar');
      await page.getByTestId('lizenz-button').click();
      await expect(page.getByTestId('lizenz-zustand')).toHaveText('Prüfung fällig');
      await expect(page.getByTestId('lizenz-grace')).toContainText('voll nutzbar');
      await expect(page.getByTestId('lizenz-revalidierungsfehler')).toHaveText('Der Lizenzserver ist nicht erreichbar.');
    });

    test('Online-Lizenz zeigt keine Ablaufwarnung, obwohl ihr Ende der Grace-Period nah ist', async ({ page }) => {
      await mockApis(page, 'ADMINISTRATOR', ONLINE({ gueltigBis: inTagen(10) }), KEIN);
      await page.goto('/organisator');

      await expect(page.getByTestId('lizenz-button')).toBeVisible();
      await expect(page.getByTestId('lizenz-banner')).toHaveCount(0);
    });

    test('Jetzt prüfen erneuert die Lizenz, bei Serverfehler erscheint die Meldung', async ({ page }) => {
      let versuche = 0;
      await mockApis(page, 'ADMINISTRATOR', ONLINE(), KEIN, {
        revalidierung: () => ++versuche === 1
          ? { status: 502, body: { error: 'Der Lizenzserver ist nicht erreichbar.', code: 'NICHT_ERREICHBAR' } }
          : { status: 200, body: ONLINE({ naechsteRevalidierung: inTagen(7) }) }
      });
      await page.goto('/organisator');
      await page.getByTestId('lizenz-button').click();

      await page.getByTestId('lizenz-revalidieren').click();
      await expect(page.getByTestId('lizenz-fehler')).toHaveText('Der Lizenzserver ist nicht erreichbar.');

      await page.getByTestId('lizenz-revalidieren').click();
      await expect(page.getByTestId('lizenz-erfolg')).toHaveText('Die Lizenz wurde beim Lizenzserver erneuert.');
      await expect(page.getByTestId('lizenz-fehler')).toHaveCount(0);
    });

    test('Entfernen: ohne Server wird "nur lokal" angeboten, danach ist die Instanz unlizenziert', async ({ page }) => {
      await mockApis(page, 'ADMINISTRATOR', ONLINE(), KEIN, {
        deaktivierung: (nurLokal) => nurLokal
          ? { status: 200, body: OHNE_LIZENZ_ONLINE }
          : { status: 502, body: { error: 'Der Lizenzserver ist nicht erreichbar.', code: 'NICHT_ERREICHBAR' } }
      });
      page.on('dialog', (dialog) => dialog.accept());
      await page.goto('/organisator');
      await page.getByTestId('lizenz-button').click();

      await expect(page.getByTestId('lizenz-nur-lokal')).toHaveCount(0);
      await page.getByTestId('lizenz-entfernen').click();
      await expect(page.getByTestId('lizenz-fehler')).toHaveText('Der Lizenzserver ist nicht erreichbar.');
      await expect(page.getByTestId('lizenz-nur-lokal')).toBeVisible();

      await page.getByTestId('lizenz-nur-lokal').click();
      await expect(page.getByTestId('lizenz-zustand')).toHaveText('Keine Lizenz');
      await expect(page.getByTestId('lizenz-aktivieren')).toBeVisible();
    });

    test('Abbrechen im Bestätigungsdialog entfernt nichts', async ({ page }) => {
      let aufrufe = 0;
      await mockApis(page, 'ADMINISTRATOR', ONLINE(), KEIN, {
        deaktivierung: () => { aufrufe++; return { status: 200, body: OHNE_LIZENZ_ONLINE }; }
      });
      page.on('dialog', (dialog) => dialog.dismiss());
      await page.goto('/organisator');
      await page.getByTestId('lizenz-button').click();

      await page.getByTestId('lizenz-entfernen').click();
      await expect(page.getByTestId('lizenz-zustand')).toHaveText('Lizenziert');
      expect(aufrufe).toBe(0);
    });
  });
});
