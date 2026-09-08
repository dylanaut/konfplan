import { test, expect } from '@playwright/test';

// Frueher enthielten diese Tests zusaetzlich "expect(page).toHaveScreenshot(...)" - dafuer
// wurden aber nie Baseline-Bilder committet (weder hier noch je in der Historie), und CI fuehrt
// Playwright-Tests ohnehin nicht aus (.github/workflows/ci.yml). Die Screenshots liefen daher
// bei jedem lokalen Lauf ins Leere, ohne jemals einen echten visuellen Regressionswert zu haben -
// die DOM/Inhalts-Assertions unten decken das Rendering ausreichend ab.
test.describe('Report-Generierung', () => {

  test('sollte den Laufzettel für einen Teilnehmer korrekt rendern', async ({ page }) => {
    const veranstaltungId = 1;
    const teilnehmerId = 101;
    // Absolutes Glob, da die Axios-Basis-URL (http://localhost:9000) von der
    // Playwright-baseURL (Vite-Dev-Server) abweicht.
    const apiUrl = `**/api/reports/${veranstaltungId}/teilnehmer/${teilnehmerId}/laufzettel-data`;
    const routeUrl = `/veranstaltung/${veranstaltungId}/teilnehmer/${teilnehmerId}/laufzettel`;

    // Die Route ist auth-geschützt: Login-Status vor dem Laden der Seite simulieren.
    await page.addInitScript(() => {
      localStorage.setItem('token', 'test-token');
      localStorage.setItem('role', 'ORGANISATOR');
    });

    await page.route(apiUrl, async route => {
      const json = (await import('./fixtures/laufzettel-teilnehmer.json', { with: { type: 'json' } })).default;
      await route.fulfill({ json });
    });

    await page.goto(routeUrl);
    await expect(page.locator('h1').last()).toContainText('Laufzettel für Max Mustermann');
    await expect(page.locator('table tbody tr').first().locator('td').nth(1)).toContainText('Einführung in die Softwareentwicklung');
  });

  test('sollte den Laufzettel für einen Referenten korrekt rendern', async ({ page }) => {
    const veranstaltungId = 1;
    const referentId = 202;
    // Absolutes Glob, da die Axios-Basis-URL (http://localhost:9000) von der
    // Playwright-baseURL (Vite-Dev-Server) abweicht.
    const apiUrl = `**/api/reports/${veranstaltungId}/referent/${referentId}/laufzettel-data`;
    const routeUrl = `/veranstaltung/${veranstaltungId}/referent/${referentId}/laufzettel`;

    // Die Route ist auth-geschützt: Login-Status vor dem Laden der Seite simulieren.
    await page.addInitScript(() => {
      localStorage.setItem('token', 'test-token');
      localStorage.setItem('role', 'ORGANISATOR');
    });

    await page.route(apiUrl, async route => {
      const json = (await import('./fixtures/laufzettel-referent.json', { with: { type: 'json' } })).default;
      await route.fulfill({ json });
    });

    await page.goto(routeUrl);
    await expect(page.locator('h1').last()).toContainText('Laufzettel für Dr. Eva Weiss');
    await expect(page.locator('table tbody tr').first().locator('td').nth(1)).toContainText('Moderne Web-Architekturen');
  });

  test('sollte den Raumbelegungsplan korrekt rendern', async ({ page }) => {
    const veranstaltungId = 1;
    const raumId = 303;
    // Absolutes Glob, da die Axios-Basis-URL (http://localhost:9000) von der
    // Playwright-baseURL (Vite-Dev-Server) abweicht.
    const apiUrl = `**/api/reports/${veranstaltungId}/raum/${raumId}/belegungsplan-data`;
    const routeUrl = `/veranstaltung/${veranstaltungId}/raum/${raumId}/belegungsplan`;

    // Die Route ist auth-geschützt: Login-Status vor dem Laden der Seite simulieren.
    await page.addInitScript(() => {
      localStorage.setItem('token', 'test-token');
      localStorage.setItem('role', 'ORGANISATOR');
    });

    await page.route(apiUrl, async route => {
      const json = (await import('./fixtures/raumbelegungsplan.json', { with: { type: 'json' } })).default;
      await route.fulfill({ json });
    });

    await page.goto(routeUrl);
    await expect(page.locator('h1').last()).toContainText('Belegungsplan für Forum');
    await expect(page.locator('table tbody tr').first().locator('td').nth(1)).toContainText('Grundlagen des Projektmanagements');
  });

  test('sollte die Raumübersicht korrekt rendern', async ({ page }) => {
    const veranstaltungId = 1;
    // Absolutes Glob, da die Axios-Basis-URL (http://localhost:9000) von der
    // Playwright-baseURL (Vite-Dev-Server) abweicht.
    const apiUrl = `**/api/reports/${veranstaltungId}/raeume-data`;
    const routeUrl = `/organisator/veranstaltung/${veranstaltungId}/uebersicht-raeume`;

    // Die Route ist ORGANISATOR-geschützt: Login-Status vor dem Laden der Seite simulieren.
    await page.addInitScript(() => {
      localStorage.setItem('token', 'test-token');
      localStorage.setItem('role', 'ORGANISATOR');
    });

    await page.route(apiUrl, async route => {
      const json = (await import('./fixtures/uebersicht-raeume.json', { with: { type: 'json' } })).default;
      await route.fulfill({ json });
    });

    await page.goto(routeUrl);
    await expect(page.locator('h1').last()).toContainText('Raumübersicht');
    await expect(page.locator('table tbody tr').first().locator('td').nth(0)).toContainText('Forum');
  });

  test('sollte die Raumschilder korrekt rendern', async ({ page }) => {
    const veranstaltungId = 1;
    // Absolutes Glob, da die Axios-Basis-URL (http://localhost:9000) von der
    // Playwright-baseURL (Vite-Dev-Server) abweicht.
    const apiUrl = `**/api/reports/${veranstaltungId}/raumschilder-data`;
    const routeUrl = `/organisator/veranstaltung/${veranstaltungId}/raumschilder`;

    // Die Route ist ORGANISATOR-geschützt: Login-Status vor dem Laden der Seite simulieren.
    await page.addInitScript(() => {
      localStorage.setItem('token', 'test-token');
      localStorage.setItem('role', 'ORGANISATOR');
    });

    await page.route(apiUrl, async route => {
      const json = (await import('./fixtures/raumschilder.json', { with: { type: 'json' } })).default;
      await route.fulfill({ json });
    });

    await page.goto(routeUrl);
    // Scoped auf ".card-header" - VeranstaltungHeader.vue rendert selbst ebenfalls ein <h2> mit
    // dem Veranstaltungsnamen, ein ungescoptes "h2" waere daher mehrdeutig.
    await expect(page.locator('.card-header h2')).toContainText('Forum');
    await expect(page.locator('table tbody tr').first().locator('td').nth(1)).toContainText('Grundlagen des Projektmanagements');
  });

  test('sollte freie Slots für Referenten korrekt rendern', async ({ page }) => {
    const veranstaltungId = 1;
    // Absolutes Glob, da die Axios-Basis-URL (http://localhost:9000) von der
    // Playwright-baseURL (Vite-Dev-Server) abweicht.
    const apiUrl = `**/api/reports/${veranstaltungId}/freie-slots-referenten-data`;
    const routeUrl = `/organisator/veranstaltung/${veranstaltungId}/freie-slots-referenten`;

    // Die Route ist ORGANISATOR-geschützt: Login-Status vor dem Laden der Seite simulieren.
    await page.addInitScript(() => {
      localStorage.setItem('token', 'test-token');
      localStorage.setItem('role', 'ORGANISATOR');
    });

    await page.route(apiUrl, async route => {
      const json = (await import('./fixtures/freie-slots-referenten.json', { with: { type: 'json' } })).default;
      await route.fulfill({ json });
    });

    await page.goto(routeUrl);
    await expect(page.locator('h1').last()).toContainText('Freie Slots für Referenten');
    // Standard-Sortierung nach Nachname: "Weiss" vor "Zimmermann".
    await expect(page.locator('table tbody tr').first().locator('td').nth(0)).toContainText('Dr. Eva Weiss');
    await expect(page.locator('table tbody tr').first().locator('td').nth(2)).toContainText('3');

    // Nach Anzahl freier Slots aufsteigend sortieren: Tom Zimmermann (1) vor Dr. Eva Weiss (3).
    await page.getByRole('button', { name: 'Anzahl freier Slots' }).click();
    await expect(page.locator('table tbody tr').first().locator('td').nth(0)).toContainText('Tom Zimmermann');

    // Erneuter Klick kehrt die Richtung um.
    await page.getByRole('button', { name: 'Anzahl freier Slots' }).click();
    await expect(page.locator('table tbody tr').first().locator('td').nth(0)).toContainText('Dr. Eva Weiss');

    // Referenten-Spalte ist ebenfalls klickbar sortierbar: erster Klick waehlt Name/aufsteigend
    // (wie die Standard-Sortierung), zweiter Klick kehrt auf Name/absteigend um.
    await page.getByRole('button', { name: 'Referent' }).click();
    await expect(page.locator('table tbody tr').first().locator('td').nth(0)).toContainText('Dr. Eva Weiss');
    await page.getByRole('button', { name: 'Referent' }).click();
    await expect(page.locator('table tbody tr').first().locator('td').nth(0)).toContainText('Tom Zimmermann');
  });

  test('sollte freie Slots für Teilnehmer korrekt rendern', async ({ page }) => {
    const veranstaltungId = 1;
    // Absolutes Glob, da die Axios-Basis-URL (http://localhost:9000) von der
    // Playwright-baseURL (Vite-Dev-Server) abweicht.
    const apiUrl = `**/api/reports/${veranstaltungId}/freie-slots-teilnehmer-data`;
    const routeUrl = `/organisator/veranstaltung/${veranstaltungId}/freie-slots-teilnehmer`;

    // Die Route ist ORGANISATOR-geschützt: Login-Status vor dem Laden der Seite simulieren.
    await page.addInitScript(() => {
      localStorage.setItem('token', 'test-token');
      localStorage.setItem('role', 'ORGANISATOR');
    });

    await page.route(apiUrl, async route => {
      const json = (await import('./fixtures/freie-slots-teilnehmer.json', { with: { type: 'json' } })).default;
      await route.fulfill({ json });
    });

    await page.goto(routeUrl);
    await expect(page.locator('h1').last()).toContainText('Freie Slots für Teilnehmer');
    await expect(page.locator('table tbody tr')).toHaveCount(2);
    // Sortiert nach Nachname: "Beispiel" vor "Mustermann".
    await expect(page.locator('table tbody tr').first().locator('td').nth(0)).toContainText('Anna Beispiel');

    await page.getByLabel('Teilnehmer ohne freie Slots ausblenden').check();
    await expect(page.locator('table tbody tr')).toHaveCount(1);
    await expect(page.locator('table tbody tr').first().locator('td').nth(0)).toContainText('Anna Beispiel');

    await page.getByLabel('Teilnehmer ohne freie Slots ausblenden').uncheck();
    await expect(page.locator('table tbody tr')).toHaveCount(2);
  });

  test('sollte die Belegungen im Stundenplan korrekt anzeigen und die Teilnehmerliste per Klick öffnen', async ({ page }) => {
    const veranstaltungId = 1;
    // Absolutes Glob, da die Axios-Basis-URL (http://localhost:9000) von der
    // Playwright-baseURL (Vite-Dev-Server) abweicht.
    const apiUrl = `**/api/reports/${veranstaltungId}/stundenplan-data`;
    const routeUrl = `/organisator/veranstaltung/${veranstaltungId}/stundenplan`;

    // Die Route ist ORGANISATOR-geschützt: Login-Status vor dem Laden der Seite simulieren.
    await page.addInitScript(() => {
      localStorage.setItem('token', 'test-token');
      localStorage.setItem('role', 'ORGANISATOR');
    });

    await page.route(apiUrl, async route => {
      const json = (await import('./fixtures/stundenplan.json', { with: { type: 'json' } })).default;
      await route.fulfill({ json });
    });

    await page.goto(routeUrl);
    await expect(page.locator('.container-fluid h1')).toContainText('Testtag 2026');

    // Raum A: Wahlvortrag mit 2 Teilnehmern (< 4 -> roter Badge) und ohne Pflicht-Kennzeichnung.
    const raumA = page.locator('.card-vortrag', { hasText: 'Robotik Workshop' });
    await expect(raumA.locator('.badge.bg-danger')).toHaveText('2 TN');
    await expect(raumA.locator('.badge.bg-primary')).toHaveCount(0);

    // Raum B: Pflichtvortrag mit 4 Teilnehmern (>= 4 -> grüner Badge) und Pflicht-Kennzeichnung.
    const raumB = page.locator('.card-vortrag', { hasText: 'Berufsorientierung' });
    await expect(raumB.locator('.badge.bg-success')).toHaveText('4 TN');
    await expect(raumB.locator('.badge.bg-primary')).toHaveText('Pflicht');

    // Freie Räume: nach Gebäude-Kürzel gruppiert (alphabetisch: HG vor NG) und innerhalb
    // jeder Gruppe alphabetisch nach Raumname sortiert (A001 vor A101).
    await expect(page.getByText('Freie Räume (3):')).toBeVisible();
    await expect(page.locator('.alert-secondary')).toContainText('HG: A001, A101 • NG: A101');

    // Aufsicht: Teilnehmer ohne Programm im Slot.
    await expect(page.getByText('Aufsicht (1):')).toBeVisible();
    await expect(page.locator('.alert-warning')).toContainText('Erik Sample');

    // Autom. Auffüllung Statistik.
    await expect(page.getByText('Autom. Auffüllung')).toBeVisible();
    await expect(page.locator('h3.mb-0')).toHaveText('1');

    // Klick auf den TN-Badge von Raum A öffnet die Teilnehmerliste als Popup.
    await raumA.locator('.badge', { hasText: '2 TN' }).click();
    const popup = page.locator('.tn-popup-card');
    await expect(popup.locator('.card-header')).toContainText('Robotik Workshop');
    await expect(popup.locator('.list-group-item')).toHaveText(['Anna Muster', 'Ben Beispiel']);

    // Popup per Schließen-Button wieder schließen.
    await popup.locator('.btn-close').click();
    await expect(popup).toHaveCount(0);
  });

  test('sollte die Anmeldungen-Übersicht über alle Wahlvorträge absteigend sortiert rendern', async ({ page }) => {
    const veranstaltungId = 1;
    // Absolutes Glob, da die Axios-Basis-URL (http://localhost:9000) von der
    // Playwright-baseURL (Vite-Dev-Server) abweicht.
    const apiUrl = `**/api/reports/${veranstaltungId}/wahlvortraege-anmeldungen-uebersicht-data`;
    const routeUrl = `/organisator/veranstaltung/${veranstaltungId}/wahlvortraege-anmeldungen`;

    // Die Route ist ORGANISATOR-geschützt: Login-Status vor dem Laden der Seite simulieren.
    await page.addInitScript(() => {
      localStorage.setItem('token', 'test-token');
      localStorage.setItem('role', 'ORGANISATOR');
    });

    await page.route(apiUrl, async route => {
      const json = (await import('./fixtures/wahlvortraege-anmeldungen-uebersicht.json', { with: { type: 'json' } })).default;
      await route.fulfill({ json });
    });

    await page.goto(routeUrl);
    await expect(page.locator('h1').last()).toContainText('Anmeldungen je Wahlvortrag (2)');

    const zeilen = page.locator('.bar-row');
    await expect(zeilen).toHaveCount(2);
    // Absteigend nach Anzahl Anmeldungen sortiert.
    await expect(zeilen.nth(0)).toContainText('Informatiker');
    await expect(zeilen.nth(0)).toContainText('3 Anmeldungen');
    await expect(zeilen.nth(0)).toContainText('Ø Priorität 8.0');
    await expect(zeilen.nth(1)).toContainText('Physiker');
    await expect(zeilen.nth(1)).toContainText('1 Anmeldung');

    // Der Balken des "Informatiker"-Vortrags ist in 3 Prio-Segmente unterteilt,
    // mit Anzahl je Prio als Tooltip (title-Attribut).
    const segmente = zeilen.nth(0).locator('.bar-segment');
    await expect(segmente).toHaveCount(3);
    await expect(segmente.nth(0)).toHaveAttribute('title', 'Priorität 6: 1 Anmeldung');
    await expect(segmente.nth(1)).toHaveAttribute('title', 'Priorität 8: 1 Anmeldung');
    await expect(segmente.nth(2)).toHaveAttribute('title', 'Priorität 10: 1 Anmeldung');

    // Wahlvorträge ohne Anmeldungen erscheinen separat am Ende, nicht als Balken.
    await expect(page.getByText('Wahlvorträge ohne Anmeldungen (1)')).toBeVisible();
    await expect(page.locator('.list-group-item')).toHaveText(['Vortrag ohne Anmeldungen']);
  });

});
