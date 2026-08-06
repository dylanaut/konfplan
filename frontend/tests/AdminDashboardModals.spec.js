import { test, expect } from '@playwright/test';

/**
 * E2E-Tests für die modalen Dialoge des AdminDashboards.
 *
 * Diese Tests laufen komplett gegen gemockte APIs (page.route) und einen per
 * localStorage vorgetäuschten ADMIN-Login (kein echter Login, kein echter Backend-
 * Aufruf nötig) - analog zum Muster in reports.spec.js. Das macht die Tests von
 * einem laufenden Quarkus-Backend unabhängig und damit CI-tauglich.
 *
 * Wichtig: die Axios-Basis-URL ist http://localhost:9000 (siehe api/axios.js),
 * unabhängig von der Playwright-baseURL (Vite-Dev-Server, http://localhost:5173).
 * Route-Muster müssen daher die volle Origin "http://localhost:9000/api/..."
 * verwenden - ein bloßes "**\/api/**" würde zusätzlich Vites eigenen Asset-Request
 * für "/src/api/axios.js" abfangen (enthält ebenfalls "/api/" im Pfad) und den
 * App-Bootstrap brechen.
 */

const VID = 1;

const VERANSTALTUNG = {
  id: VID,
  name: 'Testevent 2026',
  beginntAm: '2026-09-16T09:00:00',
  endetAm: '2026-09-16T17:00:00',
  deadlineReferenten: '2026-09-01T00:00:00',
  deadlineTeilnehmer: '2026-09-10T00:00:00',
  gebaeude: [{ id: 10, name: 'Testgebäude' }],
  organisatorIds: [100],
  version: 0
};

const GEBAEUDE = [{
  id: 10,
  name: 'Testgebäude',
  typ: 'SCHULE',
  strasse: 'Teststraße',
  hausnummer: '1',
  postleitzahl: '12345',
  ort: 'Teststadt',
  raeume: [{ id: 20, name: 'Raum A', kapazitaet: 30, etage: 'EG' }]
}];

const ALL_USERS = [
  { id: 100, firstName: 'Anna', lastName: 'Admin', email: 'admin@test.de', role: 'ADMIN', isActive: true, veranstaltungIds: [VID] },
  { id: 200, firstName: 'Rudi', lastName: 'Referent', email: 'ref@test.de', role: 'REFERENT', isActive: true, veranstaltungIds: [VID], organisation: 'TestOrg', jobRole: 'Tester', biography: 'Bio' },
  { id: 300, firstName: 'Tom', lastName: 'Teilnehmer', email: 'tn@test.de', role: 'TEILNEHMER', isActive: true, veranstaltungIds: [VID], gruppen: ['10a'], prioritaeten: [] }
];

const VORTRAEGE = [
  { id: 400, titel: 'Wahlvortrag Test', inhalt: 'Inhalt', istPflicht: false, vortrag_typ: 'WAHL', referent: { id: 200 }, wiederholbar: true, maxWiederholungen: 2, ausstattung: 'Beamer', berufsfeld: null, version: 0 },
  { id: 401, titel: 'Pflichtvortrag Test', inhalt: 'Inhalt', istPflicht: true, vortrag_typ: 'PFLICHT', referent: { id: 200 }, pflichtgruppe: '10a', pflichtraum: { id: 20 }, pflichtslot: { id: 500 }, version: 0 }
];

const SLOTS = [
  { id: 500, description: 'Slot 1', startTime: '2026-09-16T09:00:00', endTime: '2026-09-16T09:45:00' }
];

const GRUPPEN = ['10a'];

/** Registriert alle vom AdminDashboard beim Laden benötigten API-Mocks. */
async function mockAdminApis(page) {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'test-token');
    localStorage.setItem('role', 'ADMIN');
  });

  // Absolute Origin nötig: Axios ruft immer http://localhost:9000 auf (siehe api/axios.js),
  // unabhängig von der Vite-Dev-Server-Origin. Ein bloßes "**/api/**" würde außerdem versehentlich
  // Vites eigenen Asset-Request für "/src/api/axios.js" abfangen und den App-Bootstrap brechen.
  await page.route('http://localhost:9000/api/**', async (route) => {
    const req = route.request();
    const method = req.method();
    const path = new URL(req.url()).pathname;
    const body = () => { try { return JSON.parse(req.postData() || '{}'); } catch { return {}; } };
    const json = (data, status = 200) => route.fulfill({ status, json: data ?? {} });
    const noContent = () => route.fulfill({ status: 204, body: '' });

    if (path === '/api/veranstaltungen') {
      if (method === 'GET') return json([VERANSTALTUNG]);
      if (method === 'POST') return json({ ...body(), id: 999 }, 201);
    }
    if (path === `/api/veranstaltungen/${VID}`) {
      if (method === 'PUT') return json(body());
      if (method === 'DELETE') return noContent();
    }
    if (path === '/api/gebaeude') {
      if (method === 'GET') return json(GEBAEUDE);
      if (method === 'POST') return json({ ...body(), id: 888 }, 201);
    }
    if (/^\/api\/gebaeude\/\d+$/.test(path)) {
      if (method === 'PUT') return json(body());
      if (method === 'DELETE') return noContent();
    }
    if (/^\/api\/gebaeude\/\d+\/raeume$/.test(path) && method === 'POST') {
      return json({ ...body(), id: 777 }, 201);
    }
    if (/^\/api\/gebaeude\/\d+\/raeume\/\d+$/.test(path)) {
      if (method === 'PUT') return json(body());
      if (method === 'DELETE') return noContent();
    }
    if (path === '/api/admin/nutzer') {
      if (method === 'GET') return json(ALL_USERS);
      if (method === 'POST') return json({ ...body(), id: 666 }, 201);
    }
    if (/^\/api\/admin\/nutzer\/\d+$/.test(path)) {
      if (method === 'PUT') return json(body());
      if (method === 'DELETE') return noContent();
    }
    if (/^\/api\/admin\/nutzer\/\d+\/einladen\/\d+$/.test(path) && method === 'POST') {
      return json({});
    }
    if (/^\/api\/admin\/nutzer\/\d+\/reset-password$/.test(path) && method === 'POST') {
      return json({});
    }
    if (path === `/api/veranstaltungen/${VID}/nutzer`) {
      if (method === 'GET') return json(ALL_USERS);
      if (method === 'POST') return json({ ...body(), id: 555 }, 201);
    }
    if (new RegExp(`^/api/veranstaltungen/${VID}/nutzer/\\d+$`).test(path)) {
      if (method === 'PUT') return json(body());
      if (method === 'DELETE') return noContent();
    }
    if (path === `/api/veranstaltungen/${VID}/vortraege`) {
      if (method === 'GET') return json(VORTRAEGE);
      if (method === 'POST') return json({ ...body(), id: 444 }, 201);
    }
    if (new RegExp(`^/api/veranstaltungen/${VID}/vortraege/\\d+$`).test(path)) {
      if (method === 'PUT') return json(body());
      if (method === 'DELETE') return noContent();
    }
    if (path === `/api/veranstaltungen/${VID}/slots`) {
      if (method === 'GET') return json(SLOTS);
      if (method === 'POST') return json({ ...body(), id: 333 }, 201);
    }
    if (new RegExp(`^/api/veranstaltungen/${VID}/slots/\\d+$`).test(path)) {
      if (method === 'PUT') return json(body());
      if (method === 'DELETE') return noContent();
    }
    if (path === `/api/veranstaltungen/${VID}/plan/details`) return json([]);
    if (path === `/api/veranstaltungen/${VID}/plan/qualitaet`) return json({});
    if (path === `/api/admin/veranstaltungen/${VID}/gruppen`) return json(GRUPPEN);
    if (path === `/api/admin/veranstaltungen/${VID}/verfuegbarkeiten`) return json([]);
    if (path === `/api/admin/veranstaltungen/${VID}/raeume/verfuegbarkeiten`) return json([]);
    if (path === '/api/admin/protokolle') return json([]);

    // Sicherheitsnetz: unerwarteter/nicht gemockter Endpunkt - nicht hängen lassen.
    console.warn('[Test] Unmocked API call:', method, path);
    return json({});
  });
}

/** Navigiert zum Admin-Dashboard und wählt die Test-Veranstaltung aus, sodass alle Tabs sichtbar sind. */
async function gotoAdminWithEvent(page) {
  await page.goto('/admin');
  await expect(page.getByRole('heading', { name: 'Admin-Bereich' })).toBeVisible();
  await page.locator('select').first().selectOption({ index: 1 });
  await expect(page.locator('select').first()).toHaveValue(String(VID));
}

async function gotoTab(page, label) {
  await page.locator('nav button', { hasText: label }).click();
}

test.describe('AdminDashboard - Modale Dialoge', () => {
  test.beforeEach(async ({ page }) => {
    await mockAdminApis(page);
    await gotoAdminWithEvent(page);
  });

  test.describe('Veranstaltung-Editor-Modal', () => {
    test('öffnet mit leerem Formular und deaktiviertem Speichern-Button ohne Organisator', async ({ page }) => {
      await gotoTab(page, 'Veranstaltungen');
      await page.getByRole('button', { name: '+ Neu', exact: true }).click();

      await expect(page.getByText('Neue Veranstaltung anlegen')).toBeVisible();
      await expect(page.locator('label:has-text("Name der Veranstaltung") + input')).toHaveValue('');
      await expect(page.getByRole('button', { name: 'Speichern' })).toBeDisabled();
    });

    test('erstellt eine neue Veranstaltung nach Ausfüllen der Pflichtfelder und Auswahl eines Organisators', async ({ page }) => {
      await gotoTab(page, 'Veranstaltungen');
      await page.getByRole('button', { name: '+ Neu', exact: true }).click();

      await page.locator('label:has-text("Name der Veranstaltung") + input').fill('Neue Testveranstaltung');
      await page.locator('label:has-text("Beginnt am") + input').fill('2026-10-01T09:00');
      await page.locator('#admin-100').check();

      const saveBtn = page.getByRole('button', { name: 'Speichern' });
      await expect(saveBtn).toBeEnabled();

      const [request] = await Promise.all([
        page.waitForRequest(req => req.url().endsWith('/api/veranstaltungen') && req.method() === 'POST'),
        saveBtn.click()
      ]);
      expect(request.postDataJSON().name).toBe('Neue Testveranstaltung');
      await expect(page.getByText('Neue Veranstaltung anlegen')).toHaveCount(0);
    });

    test('öffnet mit vorausgefüllten Daten beim Bearbeiten', async ({ page }) => {
      await gotoTab(page, 'Veranstaltungen');
      await page.locator('button[title="Bearbeiten"]').first().click();

      await expect(page.getByText('Veranstaltung bearbeiten')).toBeVisible();
      await expect(page.locator('label:has-text("Name der Veranstaltung") + input')).toHaveValue(VERANSTALTUNG.name);
    });

    test('schließt über Abbrechen ohne einen Speichern-Aufruf auszulösen', async ({ page }) => {
      await gotoTab(page, 'Veranstaltungen');
      await page.getByRole('button', { name: '+ Neu', exact: true }).click();
      await expect(page.getByText('Neue Veranstaltung anlegen')).toBeVisible();

      await page.getByRole('button', { name: 'Abbrechen' }).click();
      await expect(page.getByText('Neue Veranstaltung anlegen')).toHaveCount(0);
    });
  });

  test.describe('Gebäude-Editor-Modal', () => {
    test('erstellt ein neues Gebäude nach Ausfüllen der Pflichtfelder', async ({ page }) => {
      await gotoTab(page, 'Gebäude');
      await page.getByRole('button', { name: '+ Neues Gebäude' }).click();
      await expect(page.getByText('Neues Gebäude anlegen')).toBeVisible();

      await page.locator('label:has-text("Name des Gebäudes") + input').fill('Neubau');
      await page.locator('label:has-text("Straße") + input').fill('Hauptstraße');
      await page.locator('label:has-text("Postleitzahl") + input').fill('54321');
      await page.locator('label:has-text("Ort") + input').fill('Musterstadt');

      const [request] = await Promise.all([
        page.waitForRequest(req => req.url().endsWith('/api/gebaeude') && req.method() === 'POST'),
        page.getByRole('button', { name: 'Speichern' }).click()
      ]);
      expect(request.postDataJSON().name).toBe('Neubau');
      await expect(page.getByText('Neues Gebäude anlegen')).toHaveCount(0);
    });

    test('öffnet vorausgefüllt beim Bearbeiten eines bestehenden Gebäudes', async ({ page }) => {
      await gotoTab(page, 'Gebäude');
      await page.locator('button[title="Gebäude bearbeiten"]').first().click();

      await expect(page.getByText('Gebäude bearbeiten')).toBeVisible();
      await expect(page.locator('label:has-text("Name des Gebäudes") + input')).toHaveValue('Testgebäude');
      await expect(page.locator('label:has-text("Gebäudetyp") + select')).toHaveValue('SCHULE');
    });

    test('schließt über das ✕-Symbol ohne zu speichern', async ({ page }) => {
      await gotoTab(page, 'Gebäude');
      await page.getByRole('button', { name: '+ Neues Gebäude' }).click();
      await expect(page.getByText('Neues Gebäude anlegen')).toBeVisible();

      await page.locator('button:has-text("✕")').click();
      await expect(page.getByText('Neues Gebäude anlegen')).toHaveCount(0);
    });
  });

  test.describe('Raum-Editor-Modal', () => {
    test('erstellt einen neuen Raum im gewählten Gebäude', async ({ page }) => {
      await gotoTab(page, 'Gebäude');
      await page.locator('button[title="Raum hinzufügen"]').first().click();
      await expect(page.getByText('Neuen Raum anlegen')).toBeVisible();

      await page.locator('label:has-text("Name des Raums") + input').fill('Raum B');
      await page.locator('label:has-text("Kapazität") + input').fill('25');

      const [request] = await Promise.all([
        page.waitForRequest(req => /\/api\/gebaeude\/\d+\/raeume$/.test(req.url()) && req.method() === 'POST'),
        page.getByRole('button', { name: 'Speichern' }).click()
      ]);
      expect(request.postDataJSON().name).toBe('Raum B');
      await expect(page.getByText('Neuen Raum anlegen')).toHaveCount(0);
    });

    test('Gebäude-Auswahl ist beim Bearbeiten eines bestehenden Raums deaktiviert', async ({ page }) => {
      await gotoTab(page, 'Gebäude');
      await page.locator('button:has-text("Testgebäude")').click(); // Gebäude-Zeile aufklappen
      await page.locator('button[title="Raum bearbeiten"]').first().click();

      await expect(page.getByText('Raum bearbeiten')).toBeVisible();
      await expect(page.locator('label:has-text("Name des Raums") + input')).toHaveValue('Raum A');
      await expect(page.locator('label:has-text("Zugehöriges Gebäude") + select')).toBeDisabled();
    });
  });

  test.describe('Nutzer-Editor-Modal', () => {
    test('erstellt einen neuen Teilnehmer mit Gruppenzuordnung', async ({ page }) => {
      await gotoTab(page, 'Teilnehmer');
      await page.getByRole('button', { name: '+ Neu', exact: true }).click();

      await expect(page.getByRole('dialog')).toBeVisible();
      await expect(page.getByText('Neuen Nutzer anlegen')).toBeVisible();
      await expect(page.locator('label:has-text("Rolle") + select')).toHaveValue('TEILNEHMER');

      await page.locator('label:has-text("Vorname") + input').fill('Lisa');
      await page.locator('label:has-text("Nachname") + input').fill('Lernend');
      await page.locator('label:has-text("Anmeldename") + input').fill('lisa.lernend');
      await page.locator('label:has-text("E-Mail") + input').fill('lisa@test.de');
      await page.locator('#gruppe-10a').check();

      const [request] = await Promise.all([
        page.waitForRequest(req => req.url().endsWith(`/api/veranstaltungen/${VID}/nutzer`) && req.method() === 'POST'),
        page.getByRole('button', { name: 'Nutzer erstellen' }).click()
      ]);
      const payload = request.postDataJSON();
      expect(payload.email).toBe('lisa@test.de');
      expect(payload.gruppen).toContain('10a');
      await expect(page.getByText('Neuen Nutzer anlegen')).toHaveCount(0);
    });

    test('erstellt einen neuen Administrator', async ({ page }) => {
      await gotoTab(page, 'Organisatoren');
      await page.getByRole('button', { name: '+ Neu', exact: true }).click();

      await page.locator('label:has-text("Vorname") + input').fill('Otto');
      await page.locator('label:has-text("Nachname") + input').fill('Organisator');
      await page.locator('label:has-text("Anmeldename") + input').fill('otto.organisator');
      await page.locator('label:has-text("E-Mail") + input').fill('otto@test.de');

      const [request] = await Promise.all([
        page.waitForRequest(req => req.url().endsWith('/api/admin/nutzer') && req.method() === 'POST'),
        page.getByRole('button', { name: 'Nutzer erstellen' }).click()
      ]);
      expect(request.postDataJSON().role).toBe('ADMIN');
      await expect(page.getByText('Neuen Nutzer anlegen')).toHaveCount(0);
    });

    test('Rolle ist beim Bearbeiten deaktiviert und zeigt rollenspezifische Felder für Referenten', async ({ page }) => {
      await gotoTab(page, 'Referenten');
      await page.locator('button[title="Bearbeiten"]').first().click();

      await expect(page.getByText('Nutzer bearbeiten')).toBeVisible();
      await expect(page.locator('label:has-text("Vorname") + input')).toHaveValue('Rudi');
      await expect(page.locator('label:has-text("Rolle") + select')).toBeDisabled();
      await expect(page.getByText('Referenten-Profil')).toBeVisible();
      await expect(page.locator('label:has-text("Job-Rolle") + input')).toHaveValue('Tester');
    });

    test('schließt über das Dialog-schließen-Symbol ohne zu speichern', async ({ page }) => {
      await gotoTab(page, 'Teilnehmer');
      await page.getByRole('button', { name: '+ Neu', exact: true }).click();
      await expect(page.getByRole('dialog')).toBeVisible();

      await page.getByLabel('Dialog schließen').click();
      await expect(page.getByRole('dialog')).toHaveCount(0);
    });
  });

  test.describe('Vortrag-Editor-Modal', () => {
    test('erstellt einen neuen Wahlvortrag', async ({ page }) => {
      await gotoTab(page, 'Vorträge');
      await page.getByRole('button', { name: '+ Neu', exact: true }).click();

      await expect(page.getByText('Neuen Vortrag anlegen')).toBeVisible();
      await expect(page.locator('label:has-text("Typ") + select')).toHaveValue('WAHL');

      await page.locator('label:has-text("Titel") + input').fill('Neuer Wahlvortrag');
      await page.locator('label:has-text("Referent") + select').selectOption('200');

      const [request] = await Promise.all([
        page.waitForRequest(req => req.url().endsWith(`/api/veranstaltungen/${VID}/vortraege`) && req.method() === 'POST'),
        page.getByRole('button', { name: 'Speichern' }).click()
      ]);
      expect(request.postDataJSON().titel).toBe('Neuer Wahlvortrag');
      await expect(page.getByText('Neuen Vortrag anlegen')).toHaveCount(0);
    });

    test('zeigt eine Inline-Fehlermeldung und bleibt geöffnet, wenn das Speichern fehlschlägt', async ({ page }) => {
      await page.route(`http://localhost:9000/api/veranstaltungen/${VID}/vortraege`, async (route) => {
        if (route.request().method() === 'POST') {
          return route.fulfill({ status: 400, json: { message: 'Titel bereits vergeben.' } });
        }
        return route.fallback();
      });

      await gotoTab(page, 'Vorträge');
      await page.getByRole('button', { name: '+ Neu', exact: true }).click();
      await page.locator('label:has-text("Titel") + input').fill('Doppelter Titel');
      await page.locator('label:has-text("Referent") + select').selectOption('200');
      await page.getByRole('button', { name: 'Speichern' }).click();

      await expect(page.getByText('Titel bereits vergeben.')).toBeVisible();
      // Modal bleibt offen, da handleSaveVortrag showVortragModal bei Fehlern nicht schließt.
      await expect(page.getByText('Neuen Vortrag anlegen')).toBeVisible();
    });

    test('Typ und Referent sind beim Bearbeiten eines Pflichtvortrags nicht änderbar', async ({ page }) => {
      await gotoTab(page, 'Vorträge');
      await page.locator('tr', { hasText: 'Pflichtvortrag Test' }).locator('button[title="Bearbeiten"]').click();

      await expect(page.getByText('Vortrag bearbeiten')).toBeVisible();
      await expect(page.locator('label:has-text("Typ") + select')).toBeDisabled();
      await expect(page.locator('label:has-text("Referent") + select')).toBeDisabled();
      await expect(page.getByText('Pflicht-Zuweisung')).toBeVisible();
    });
  });

  test.describe('Zeit-Slot-Editor-Modal', () => {
    test('erstellt einen neuen Zeit-Slot', async ({ page }) => {
      await gotoTab(page, 'Zeit-Slots');
      await page.getByRole('button', { name: '+ Neu', exact: true }).click();
      await expect(page.getByText('Neuen Zeit-Slot anlegen')).toBeVisible();

      await page.locator('label:has-text("Bezeichnung") + input').fill('Slot 2');
      await page.locator('label:has-text("Beginn") + input').fill('2026-09-16T10:00');
      await page.locator('label:has-text("Ende") + input').fill('2026-09-16T10:45');

      const [request] = await Promise.all([
        page.waitForRequest(req => req.url().endsWith(`/api/veranstaltungen/${VID}/slots`) && req.method() === 'POST'),
        page.getByRole('button', { name: 'Slot erstellen' }).click()
      ]);
      expect(request.postDataJSON().description).toBe('Slot 2');
      await expect(page.getByText('Neuen Zeit-Slot anlegen')).toHaveCount(0);
    });

    test('öffnet vorausgefüllt beim Bearbeiten eines bestehenden Slots', async ({ page }) => {
      await gotoTab(page, 'Zeit-Slots');
      await page.locator('button[title="Bearbeiten"]').first().click();

      await expect(page.getByText('Zeit-Slot bearbeiten')).toBeVisible();
      await expect(page.locator('label:has-text("Bezeichnung") + input')).toHaveValue('Slot 1');
    });
  });

  test.describe('Einladungs-Modal', () => {
    test('zeigt verfügbare zukünftige Veranstaltungen und schließt über Abbrechen', async ({ page }) => {
      await gotoTab(page, 'Referenten');
      await page.locator('button[title="Einladen"]').first().click();

      await expect(page.getByText('Nutzer einladen')).toBeVisible();
      await expect(page.locator('label:has-text("Veranstaltung auswählen") + select option', { hasText: VERANSTALTUNG.name })).toHaveCount(1);
      await expect(page.locator('button.btn-primary', { hasText: 'Einladen' })).toBeDisabled();

      await page.getByRole('button', { name: 'Abbrechen' }).click();
      await expect(page.getByText('Nutzer einladen')).toHaveCount(0);
    });

    test('sendet die Einladung nach Auswahl einer Veranstaltung', async ({ page }) => {
      await gotoTab(page, 'Referenten');
      await page.locator('button[title="Einladen"]').first().click();

      await page.locator('label:has-text("Veranstaltung auswählen") + select').selectOption({ index: 1 });
      // Scoped auf .btn-primary, da der auslösende Icon-Button (title="Einladen") hinter dem
      // Modal-Overlay weiterhin im DOM steht und sonst denselben Accessible-Namen liefert.
      const einladenBtn = page.locator('button.btn-primary', { hasText: 'Einladen' });
      await expect(einladenBtn).toBeEnabled();

      const [request] = await Promise.all([
        page.waitForRequest(req => /\/api\/admin\/nutzer\/\d+\/einladen\/\d+$/.test(req.url()) && req.method() === 'POST'),
        einladenBtn.click()
      ]);
      expect(request.url()).toContain(`/einladen/${VID}`);
    });
  });

  // Regression #57/#58: Rettungsweg für Admin-Konten ohne (funktionierende) E-Mail-Adresse,
  // die sich sonst über "Passwort vergessen" nicht selbst wiederherstellen könnten.
  test.describe('Passwort-Reset-Modal', () => {
    test('öffnet mit leerem Feld und deaktiviertem Zurücksetzen-Button unter 8 Zeichen', async ({ page }) => {
      await gotoTab(page, 'Organisatoren');
      await page.locator('button[title="Passwort zurücksetzen"]').first().click();

      await expect(page.getByText('Passwort zurücksetzen')).toBeVisible();
      await expect(page.locator('span.font-bold', { hasText: 'Anna Admin' })).toBeVisible();
      const resetBtn = page.locator('button.btn-primary', { hasText: 'Zurücksetzen' });
      await expect(resetBtn).toBeDisabled();

      await page.locator('input[type="password"]').fill('kurz');
      await expect(resetBtn).toBeDisabled();

      await page.getByRole('button', { name: 'Abbrechen' }).click();
      await expect(page.getByText('Passwort zurücksetzen')).toHaveCount(0);
    });

    test('setzt das Passwort nach Eingabe von mindestens 8 Zeichen zurück', async ({ page }) => {
      await gotoTab(page, 'Organisatoren');
      await page.locator('button[title="Passwort zurücksetzen"]').first().click();

      await page.locator('input[type="password"]').fill('einNeuesPasswort123');
      const resetBtn = page.locator('button.btn-primary', { hasText: 'Zurücksetzen' });
      await expect(resetBtn).toBeEnabled();

      const [request, dialog] = await Promise.all([
        page.waitForRequest(req => /\/api\/admin\/nutzer\/\d+\/reset-password$/.test(req.url()) && req.method() === 'POST'),
        page.waitForEvent('dialog'),
        resetBtn.click()
      ]);
      expect(request.url()).toContain('/nutzer/100/reset-password');
      expect(request.postDataJSON()).toEqual({ newPassword: 'einNeuesPasswort123' });
      expect(dialog.message()).toContain('Passwort erfolgreich zurückgesetzt');
      await dialog.accept();
    });
  });

  test.describe('CSV-Import-Ergebnis-Modal', () => {
    test('zeigt die Erfolgsmeldung an und schließt nach 3 Sekunden automatisch', async ({ page }) => {
      await page.route(`http://localhost:9000/api/veranstaltungen/${VID}/teilnehmer/import`, route =>
        route.fulfill({ status: 200, body: 'Import erfolgreich: 3 Teilnehmer angelegt.' })
      );

      await gotoTab(page, 'Teilnehmer');
      const [fileChooser] = await Promise.all([
        page.waitForEvent('filechooser'),
        page.getByRole('button', { name: 'Import', exact: true }).click()
      ]);
      await fileChooser.setFiles({
        name: 'teilnehmer.csv',
        mimeType: 'text/csv',
        buffer: Buffer.from('Vorname;Nachname;Email;Gruppen\nMax;Mustermann;max@test.de;10a')
      });

      await expect(page.getByText('CSV Import Ergebnis')).toBeVisible();
      await expect(page.getByText('Erfolgreich:')).toContainText('3');
      await expect(page.getByText('CSV Import Ergebnis')).toBeHidden({ timeout: 5000 });
    });

    test('zeigt gesammelte Fehlermeldungen an und bleibt offen bis manuell geschlossen', async ({ page }) => {
      await page.route(`http://localhost:9000/api/admin/veranstaltungen/${VID}/teilnehmer/verfuegbarkeiten/import`, route =>
        route.fulfill({
          status: 200,
          json: { anzahlErfolgreich: 0, fehler: ["Nutzer mit E-Mail 'unbekannt@test.de' nicht gefunden."] }
        })
      );

      await gotoTab(page, 'Teilnehmer');
      const [fileChooser] = await Promise.all([
        page.waitForEvent('filechooser'),
        page.getByRole('button', { name: 'Verfügbarkeiten Import' }).click()
      ]);
      await fileChooser.setFiles({
        name: 'verf.csv',
        mimeType: 'text/csv',
        buffer: Buffer.from('email;verfuegbare_slots\nunbekannt@test.de;1')
      });

      await expect(page.getByText('CSV Import Ergebnis')).toBeVisible();
      await expect(page.getByText("Nutzer mit E-Mail 'unbekannt@test.de' nicht gefunden.")).toBeVisible();
      // Da Fehler > 0 vorliegen, darf sich das Modal NICHT automatisch schließen.
      await page.waitForTimeout(3200);
      await expect(page.getByText('CSV Import Ergebnis')).toBeVisible();

      await page.getByRole('button', { name: 'Schließen' }).click();
      await expect(page.getByText('CSV Import Ergebnis')).toHaveCount(0);
    });

    // Regression #39: handleGlobalUpload rief nach loadData() zusätzlich refreshAdmins()
    // auf, das users.value mit der reinen Admin-Liste überschrieb und damit gerade per CSV
    // importierte Referenten/Teilnehmer sofort wieder aus der Tabelle verschwinden ließ. Der
    // Mock unten simuliert genau diesen Zustandswechsel: erst nach dem Import liefert
    // GET .../nutzer den neuen Referenten - würde die App fälschlich noch refreshAdmins()
    // (nur GET /api/admin/nutzer, ohne den neuen Referenten) danach aufrufen, bliebe die
    // Tabelle leer und der Test schlägt fehl.
    test('importierter Referent erscheint sofort in der Referenten-Tabelle, ohne dass die Veranstaltung neu ausgewählt werden muss', async ({ page }) => {
      let referentImported = false;
      const NEW_REFERENT = {
        id: 999, firstName: 'Erika', lastName: 'Musterfrau', email: 'erika.musterfrau@test.de',
        role: 'REFERENT', isActive: true, veranstaltungIds: [VID]
      };

      await page.route(`http://localhost:9000/api/veranstaltungen/${VID}/referenten/import`, route => {
        referentImported = true;
        return route.fulfill({ status: 200, json: { anzahlErfolgreich: 1, fehler: [] } });
      });
      await page.route(`http://localhost:9000/api/veranstaltungen/${VID}/nutzer`, (route) => {
        if (route.request().method() === 'GET' && referentImported) {
          return route.fulfill({ status: 200, json: [...ALL_USERS, NEW_REFERENT] });
        }
        return route.fallback();
      });

      await gotoTab(page, 'Referenten');
      const [fileChooser] = await Promise.all([
        page.waitForEvent('filechooser'),
        page.getByRole('button', { name: 'Import', exact: true }).click()
      ]);
      await fileChooser.setFiles({
        name: 'referenten.csv',
        mimeType: 'text/csv',
        buffer: Buffer.from('Vorname;Nachname;Email;LoginName\nErika;Musterfrau;erika.musterfrau@test.de;erika.musterfrau')
      });

      await expect(page.getByText('CSV Import Ergebnis')).toBeVisible();
      // Wichtig: erst nachdem ALLE Folge-Requests von handleGlobalUpload abgeklungen sind
      // prüfen - sonst würde toBeVisible() (das bis zum Timeout retried) einen nur
      // kurzzeitig sichtbaren Zwischenzustand fälschlich als Erfolg werten, selbst wenn ein
      // späterer refreshAdmins()-Aufruf den Referenten danach wieder aus der Liste entfernt.
      // (Alle Antworten sind gemockt und damit praktisch verzögerungsfrei; 500ms sind ein
      // großzügiger Puffer für die Kette der await-Aufrufe in handleGlobalUpload plus
      // Vue-Rerender.)
      await page.waitForTimeout(500);
      await expect(page.locator('td:has-text("Musterfrau")')).toBeVisible();
    });
  });

  test.describe('Lösch-Bestätigungen (window.confirm)', () => {
    test('Abbrechen des Bestätigungsdialogs verhindert das Löschen einer Veranstaltung', async ({ page }) => {
      let deleteCalled = false;
      await page.route(`http://localhost:9000/api/veranstaltungen/${VID}`, async (route) => {
        if (route.request().method() === 'DELETE') {
          deleteCalled = true;
        }
        return route.fallback();
      });

      page.once('dialog', dialog => dialog.dismiss());
      await gotoTab(page, 'Veranstaltungen');
      await page.locator('tbody tr').first().locator('button.text-red-600').click();

      await page.waitForTimeout(300);
      expect(deleteCalled).toBe(false);
    });

    test('Bestätigen des Dialogs löst die Löschung eines Zeit-Slots aus', async ({ page }) => {
      page.once('dialog', dialog => dialog.accept());
      await gotoTab(page, 'Zeit-Slots');

      const [request] = await Promise.all([
        page.waitForRequest(req => new RegExp(`/api/veranstaltungen/${VID}/slots/\\d+$`).test(req.url()) && req.method() === 'DELETE'),
        page.locator('tbody tr').first().locator('button.text-red-600').click()
      ]);
      expect(request.method()).toBe('DELETE');
    });
  });
});
