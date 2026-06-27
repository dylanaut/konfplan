import { test, expect } from '@playwright/test';

test.describe('Report-Generierung', () => {

  test('sollte den Laufzettel für einen Teilnehmer korrekt rendern', async ({ page }) => {
    const veranstaltungId = 1;
    const teilnehmerId = 101;
    const apiUrl = `/api/reports/${veranstaltungId}/teilnehmer/${teilnehmerId}/laufzettel-data`;
    const routeUrl = `/veranstaltung/${veranstaltungId}/teilnehmer/${teilnehmerId}/laufzettel`;

    await page.route(apiUrl, async route => {
      const json = await import(`./fixtures/laufzettel-teilnehmer.json`);
      await route.fulfill({ json });
    });

    await page.goto(routeUrl);
    await expect(page.locator('h1')).toContainText('Laufzettel für Max Mustermann');
    await expect(page.locator('table tbody tr').first().locator('td').nth(1)).toContainText('Einführung in die Softwareentwicklung');
    await expect(page).toHaveScreenshot('laufzettel-teilnehmer.png');
  });

  test('sollte den Laufzettel für einen Referenten korrekt rendern', async ({ page }) => {
    const veranstaltungId = 1;
    const referentId = 202;
    const apiUrl = `/api/reports/${veranstaltungId}/referent/${referentId}/laufzettel-data`;
    const routeUrl = `/veranstaltung/${veranstaltungId}/referent/${referentId}/laufzettel`;

    await page.route(apiUrl, async route => {
      const json = await import(`./fixtures/laufzettel-referent.json`);
      await route.fulfill({ json });
    });

    await page.goto(routeUrl);
    await expect(page.locator('h1')).toContainText('Laufzettel für Dr. Eva Weiss');
    await expect(page.locator('table tbody tr').first().locator('td').nth(1)).toContainText('Moderne Web-Architekturen');
    await expect(page).toHaveScreenshot('laufzettel-referent.png');
  });

  test('sollte den Raumbelegungsplan korrekt rendern', async ({ page }) => {
    const veranstaltungId = 1;
    const raumId = 303;
    const apiUrl = `/api/reports/${veranstaltungId}/raum/${raumId}/belegungsplan-data`;
    const routeUrl = `/veranstaltung/${veranstaltungId}/raum/${raumId}/belegungsplan`;

    await page.route(apiUrl, async route => {
      const json = await import(`./fixtures/raumbelegungsplan.json`);
      await route.fulfill({ json });
    });

    await page.goto(routeUrl);
    await expect(page.locator('h1')).toContainText('Belegungsplan für Raum: Forum');
    await expect(page.locator('table tbody tr').first().locator('td').nth(1)).toContainText('Grundlagen des Projektmanagements');
    await expect(page).toHaveScreenshot('raumbelegungsplan.png');
  });

  test('sollte die Raumübersicht korrekt rendern', async ({ page }) => {
    const veranstaltungId = 1;
    const apiUrl = `/api/reports/${veranstaltungId}/raeume-data`;
    const routeUrl = `/admin/veranstaltung/${veranstaltungId}/uebersicht-raeume`;

    await page.route(apiUrl, async route => {
      const json = await import(`./fixtures/uebersicht-raeume.json`);
      await route.fulfill({ json });
    });

    await page.goto(routeUrl);
    await expect(page.locator('h1')).toContainText('Raumübersicht');
    await expect(page.locator('table tbody tr').first().locator('td').nth(0)).toContainText('Forum');
    await expect(page).toHaveScreenshot('uebersicht-raeume.png');
  });

  test('sollte die Raumschilder korrekt rendern', async ({ page }) => {
    const veranstaltungId = 1;
    const apiUrl = `/api/reports/${veranstaltungId}/raumschilder-data`;
    const routeUrl = `/admin/veranstaltung/${veranstaltungId}/raumschilder`;

    await page.route(apiUrl, async route => {
      const json = await import(`./fixtures/raumschilder.json`);
      await route.fulfill({ json });
    });

    await page.goto(routeUrl);
    await expect(page.locator('h2')).toContainText('Raum: Forum');
    await expect(page.locator('table tbody tr').first().locator('td').nth(1)).toContainText('Grundlagen des Projektmanagements');
    await expect(page).toHaveScreenshot('raumschilder.png');
  });

  test('sollte freie Slots für Referenten korrekt rendern', async ({ page }) => {
    const veranstaltungId = 1;
    const apiUrl = `/api/reports/${veranstaltungId}/freie-slots-referenten-data`;
    const routeUrl = `/admin/veranstaltung/${veranstaltungId}/freie-slots-referenten`;

    await page.route(apiUrl, async route => {
      const json = await import(`./fixtures/freie-slots.json`);
      await route.fulfill({ json });
    });

    await page.goto(routeUrl);
    await expect(page.locator('h1')).toContainText('Freie Slots für Referenten');
    await expect(page.locator('table tbody tr').first().locator('td').nth(0)).toContainText('Dr. Eva Weiss');
    await expect(page).toHaveScreenshot('freie-slots-referenten.png');
  });

  test('sollte freie Slots für Teilnehmer korrekt rendern', async ({ page }) => {
    const veranstaltungId = 1;
    const apiUrl = `/api/reports/${veranstaltungId}/freie-slots-teilnehmer-data`;
    const routeUrl = `/admin/veranstaltung/${veranstaltungId}/freie-slots-teilnehmer`;

    await page.route(apiUrl, async route => {
      const json = await import(`./fixtures/freie-slots.json`);
      await route.fulfill({ json });
    });

    await page.goto(routeUrl);
    await expect(page.locator('h1')).toContainText('Freie Slots für Teilnehmer');
    await expect(page.locator('table tbody tr').first().locator('td').nth(0)).toContainText('Max Mustermann');
    await expect(page).toHaveScreenshot('freie-slots-teilnehmer.png');
  });

});
