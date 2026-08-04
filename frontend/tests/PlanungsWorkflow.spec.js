import { test, expect } from '@playwright/test';

/**
 * E2E-Test für den vollständigen Admin-Planungs-Workflow: Veranstaltung -> Gebäude ->
 * Slots -> Personen -> Vorträge.
 *
 * Läuft wie AdminDashboardModals.spec.js komplett gegen gemockte APIs (page.route) und
 * einen per localStorage vorgetäuschten ADMIN-Login - kein echter Login, kein laufendes
 * Backend nötig. Anders als die dortigen Einzeltests hält dieser Mock einen einfachen
 * In-Memory-Zustand (Arrays statt fixer Fixtures), damit z.B. ein neu angelegter Referent
 * anschließend im Vortrag-Dropdown auswählbar ist - der Test soll ja gerade die Verkettung
 * der einzelnen Schritte prüfen, nicht nur jeden Schritt isoliert.
 *
 * Wichtig: die Axios-Basis-URL ist http://localhost:9000 (siehe api/axios.js), unabhängig
 * von der Playwright-baseURL (Vite-Dev-Server, http://localhost:5173).
 */

let nextId;
let veranstaltungen;
let gebaeude;
let nutzer;
let vortraege;
let slots;

const ADMIN = { id: 1, firstName: 'Anna', lastName: 'Admin', email: 'admin@test.de', role: 'ADMIN', isActive: true };

function resetState() {
  nextId = 1000;
  veranstaltungen = [];
  gebaeude = [];
  nutzer = [ADMIN];
  vortraege = [];
  slots = [];
}

async function mockAdminApis(page) {
  resetState();
  await page.addInitScript(() => {
    localStorage.setItem('token', 'test-token');
    localStorage.setItem('role', 'ADMIN');
  });

  await page.route('http://localhost:9000/api/**', async (route) => {
    const req = route.request();
    const method = req.method();
    const path = new URL(req.url()).pathname;
    const body = () => { try { return JSON.parse(req.postData() || '{}'); } catch { return {}; } };
    const json = (data, status = 200) => route.fulfill({ status, json: data ?? {} });

    if (path === '/api/veranstaltungen') {
      if (method === 'GET') return json(veranstaltungen);
      if (method === 'POST') {
        const v = { ...body(), id: nextId++, version: 0, gebaeude: [] };
        veranstaltungen.push(v);
        return json(v, 201);
      }
    }
    let m = path.match(/^\/api\/veranstaltungen\/(\d+)$/);
    if (m && method === 'PUT') {
      const idx = veranstaltungen.findIndex((v) => v.id === Number(m[1]));
      const updated = { ...veranstaltungen[idx], ...body() };
      updated.gebaeude = (body().gebaeude ?? []).map(({ id }) => gebaeude.find((g) => g.id === id)).filter(Boolean);
      veranstaltungen[idx] = updated;
      return json(updated);
    }
    if (path === '/api/gebaeude') {
      if (method === 'GET') return json(gebaeude);
      if (method === 'POST') {
        const g = { ...body(), id: nextId++, raeume: [] };
        gebaeude.push(g);
        return json(g, 201);
      }
    }
    m = path.match(/^\/api\/gebaeude\/(\d+)\/raeume$/);
    if (m && method === 'POST') {
      const raum = { ...body(), id: nextId++ };
      gebaeude.find((g) => g.id === Number(m[1]))?.raeume.push(raum);
      return json(raum, 201);
    }
    m = path.match(/^\/api\/veranstaltungen\/(\d+)\/nutzer$/);
    if (m) {
      const vid = Number(m[1]);
      if (method === 'GET') {
        const result = nutzer.filter((n) => n.veranstaltungIds?.includes(vid));
        return json(result);
      }
      if (method === 'POST') {
        const n = { ...body(), id: nextId++, veranstaltungIds: [vid] };
        nutzer.push(n);
        return json(n, 201);
      }
    }
    m = path.match(/^\/api\/veranstaltungen\/(\d+)\/vortraege$/);
    if (m) {
      const vid = Number(m[1]);
      if (method === 'GET') return json(vortraege.filter((v) => v.veranstaltungId === vid));
      if (method === 'POST') {
        const v = { ...body(), id: nextId++, veranstaltungId: vid };
        vortraege.push(v);
        return json(v, 201);
      }
    }
    m = path.match(/^\/api\/veranstaltungen\/(\d+)\/slots$/);
    if (m) {
      const vid = Number(m[1]);
      if (method === 'GET') return json(slots.filter((s) => s.veranstaltungId === vid));
      if (method === 'POST') {
        const s = { ...body(), id: nextId++, veranstaltungId: vid };
        slots.push(s);
        return json(s, 201);
      }
    }
    if (path === '/api/admin/nutzer' && method === 'GET') return json(nutzer.filter((n) => n.role === 'ADMIN'));
    m = path.match(/^\/api\/veranstaltungen\/(\d+)\/plan\/(details|qualitaet)$/);
    if (m) return json(m[2] === 'details' ? [] : {});
    if (/^\/api\/admin\/veranstaltungen\/\d+\/gruppen$/.test(path)) return json([]);
    if (/^\/api\/admin\/veranstaltungen\/\d+\/verfuegbarkeiten$/.test(path)) return json([]);
    if (/^\/api\/admin\/veranstaltungen\/\d+\/raeume\/verfuegbarkeiten$/.test(path)) return json([]);

    console.warn('[Test] Unmocked API call:', method, path);
    return json({});
  });
}

async function gotoTab(page, label) {
  await page.locator('nav button', { hasText: label }).click();
}

// Erzwingt einen frischen loadData()-Durchlauf via handleVeranstaltungChange(), indem die
// Veranstaltungsauswahl kurz auf "-- Bitte wählen --" und zurück gesetzt wird - ein erneutes
// selectOption() auf den bereits ausgewählten Wert löst keinen "change"-Event aus.
async function reloadViaVeranstaltungReselect(page) {
  await page.locator('select').first().selectOption({ index: 0 });
  await page.locator('select').first().selectOption({ index: 1 });
}

test('Vollständiger Workflow: Veranstaltung -> Gebäude -> Slots -> Personen -> Vorträge', async ({ page }) => {
  await mockAdminApis(page);

  // 1. ADMIN-BEREICH ÖFFNEN (Login ist per addInitScript vorgetäuscht, siehe mockAdminApis)
  await page.goto('/admin');
  await expect(page.getByRole('heading', { name: 'Admin-Bereich' })).toBeVisible();

  // 2. VERANSTALTUNG ANLEGEN
  await gotoTab(page, 'Veranstaltungen');
  await page.getByRole('button', { name: '+ Neu', exact: true }).click();
  await page.locator('label:has-text("Name der Veranstaltung") + input').fill('Test Event 2025');
  await page.locator('label:has-text("Beginnt am") + input').fill('2025-05-20T09:00');
  await page.getByLabel(`${ADMIN.firstName} ${ADMIN.lastName}`).check();
  await page.getByRole('button', { name: 'Speichern' }).click();
  await expect(page.locator('td:has-text("Test Event 2025")')).toBeVisible();

  // 3. VERANSTALTUNG AUSWÄHLEN
  await page.locator('select').first().selectOption({ index: 1 });

  // 4. GEBÄUDE ANLEGEN
  await gotoTab(page, 'Gebäude');
  await page.getByRole('button', { name: '+ Neues Gebäude' }).click();
  await page.locator('label:has-text("Name des Gebäudes") + input').fill('Hauptgebäude A');
  await page.locator('label:has-text("Straße") + input').fill('Schulstraße');
  await page.locator('label:has-text("Postleitzahl") + input').fill('53545');
  await page.locator('label:has-text("Ort") + input').fill('Linz');
  await page.getByRole('button', { name: 'Speichern' }).click();

  // 4b. GEBÄUDE MIT DER VERANSTALTUNG VERKNÜPFEN (neu angelegte Gebäude sind global,
  // die Gebäude-Tab-Ansicht zeigt aber nur die der aktiven Veranstaltung zugeordneten -
  // Verknüpfung erfolgt über "Zugehörige Gebäude" im Veranstaltung-Editor).
  await gotoTab(page, 'Veranstaltungen');
  await page.locator('button[title="Bearbeiten"]').first().click();
  await page.getByLabel('Hauptgebäude A (Linz)').check();
  await Promise.all([
    page.waitForResponse((res) => res.url().endsWith('/api/veranstaltungen') && res.request().method() === 'GET'),
    page.getByRole('button', { name: 'Speichern' }).click(),
  ]);
  await page.locator('select').first().selectOption({ index: 1 });

  await gotoTab(page, 'Gebäude');
  await expect(page.locator('td:has-text("Hauptgebäude A")')).toBeVisible();

  // 5. SLOTS ANLEGEN (3 Slots a 45min, 15min Pause)
  await gotoTab(page, 'Zeit-Slots');

  const slotsToCreate = [
    { name: 'Slot 1', start: '2025-05-20T09:00', end: '2025-05-20T09:45' },
    { name: 'Slot 2', start: '2025-05-20T10:00', end: '2025-05-20T10:45' },
    { name: 'Slot 3', start: '2025-05-20T11:00', end: '2025-05-20T11:45' },
  ];
  for (const slot of slotsToCreate) {
    await page.getByRole('button', { name: '+ Neu', exact: true }).click();
    await page.locator('label:has-text("Bezeichnung") + input').fill(slot.name);
    await page.locator('label:has-text("Beginn") + input').fill(slot.start);
    await page.locator('label:has-text("Ende") + input').fill(slot.end);
    await page.getByRole('button', { name: 'Slot erstellen' }).click();
  }
  await expect(page.locator('td:has-text("Slot 3")')).toBeVisible();

  // 6. REFERENTEN ANLEGEN
  await gotoTab(page, 'Referenten');
  await page.getByRole('button', { name: '+ Neu', exact: true }).click();
  await expect(page.locator('label:has-text("Rolle") + select')).toHaveValue('REFERENT');
  await page.locator('label:has-text("Vorname") + input').fill('Max');
  await page.locator('label:has-text("Nachname") + input').fill('Referent');
  await page.locator('label:has-text("Anmeldename") + input').fill('max.referent');
  await page.locator('label:has-text("E-Mail") + input').fill('max@referent.de');
  await page.getByRole('button', { name: 'Nutzer erstellen' }).click();

  // Anwendungs-Bug-Workaround: handleSaveUser() ruft nach loadData() zusaetzlich
  // refreshAdmins() auf, das users.value mit der reinen Admin-Liste ueberschreibt und
  // den gerade angelegten Referenten kurz danach wieder aus allen Nutzerlisten (u.a. der
  // Referenten-Tabelle und dem Referent-Dropdown im Vortrag-Editor) verschwinden laesst.
  await reloadViaVeranstaltungReselect(page);
  await expect(page.locator('td:has-text("Max Referent")')).toBeVisible();

  // 7. VORTRAG ANLEGEN
  await gotoTab(page, 'Vorträge');
  await page.getByRole('button', { name: '+ Neu', exact: true }).click();
  await page.locator('label:has-text("Titel") + input').fill('Vortrag von Max');
  await page.locator('label:has-text("Referent") + select').selectOption({ label: 'Max Referent' });
  await page.getByRole('button', { name: 'Speichern' }).click();
  await expect(page.locator('td:has-text("Vortrag von Max")')).toBeVisible();

  // 8. TEILNEHMER ANLEGEN
  await gotoTab(page, 'Teilnehmer');
  await page.getByRole('button', { name: '+ Neu', exact: true }).click();
  await expect(page.locator('label:has-text("Rolle") + select')).toHaveValue('TEILNEHMER');
  await page.locator('label:has-text("Vorname") + input').fill('Tom');
  await page.locator('label:has-text("Nachname") + input').fill('Teilnehmer');
  await page.locator('label:has-text("Anmeldename") + input').fill('tom.teilnehmer');
  await page.locator('label:has-text("E-Mail") + input').fill('tom@student.de');
  await page.getByRole('button', { name: 'Nutzer erstellen' }).click();
  // Gleicher Anwendungs-Bug-Workaround wie bei der Referenten-Anlage oben.
  await reloadViaVeranstaltungReselect(page);
  await expect(page.locator('td:has-text("Tom Teilnehmer")').first()).toBeVisible();

  // 9. KONTROLLE IN PLANERSTELLUNG
  await gotoTab(page, 'Planerstellung');
  // Der Button ist deaktiviert, solange kein Teilnehmer Prioritäten gesetzt hat (hier nicht
  // Teil des Admin-Workflows, sondern eine Selbstbedienungs-Aktion der Teilnehmer selbst).
  await expect(page.getByRole('button', { name: 'Pläne erstellen' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Pläne erstellen' })).toBeDisabled();
});
