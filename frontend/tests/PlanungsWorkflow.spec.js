import { test, expect } from '@playwright/test';

test.describe('Vortragsmanager Planungs-Workflow', () => {

  test('Vollständiger Workflow: Veranstaltung -> Gebäude -> Slots -> Personen -> Vorträge', async ({ page }) => {
    // 1. LOGIN
    await page.goto('http://localhost:5173/login');
    await page.fill('input[type="email"]', 'juergenkrey@yahoo.de');
    await page.fill('input[type="password"]', 'start123'); // Annahme Standardpasswort
    await page.click('button:has-text("Anmelden")');
    await expect(page).toHaveURL(/.*\/admin/);

    // 2. VERANSTALTUNG ANLEGEN
    await page.click('button:has-text("Neu")'); // Im Tab Veranstaltungen (Standard)
    await page.fill('label:has-text("Name der Veranstaltung") + input', 'Test Event 2025');
    await page.fill('label:has-text("Beginnt am") + input', '2025-05-20T09:00');
    await page.fill('label:has-text("Ort / Adresse") + input', 'Linz am Rhein');
    await page.click('button:has-text("Speichern")');
    await expect(page.locator('td:has-text("Test Event 2025")')).toBeVisible();

    // 3. VERANSTALTUNG AUSWÄHLEN
    await page.selectOption('select', { label: /Test Event 2025/ });

    // 4. GEBÄUDE ANLEGEN
    await page.click('nav button:has-text("gebäude")');
    await page.click('button:has-text("+ Neu")');
    await page.fill('label:has-text("Name des Gebäudes") + input', 'Hauptgebäude A');
    await page.fill('label:has-text("Straße") + input', 'Schulstraße');
    await page.fill('label:has-text("Postleitzahl") + input', '53545');
    await page.fill('label:has-text("Ort") + input', 'Linz');
    await page.click('button:has-text("Speichern")');
    await expect(page.locator('td:has-text("Hauptgebäude A")')).toBeVisible();

    // 5. SLOTS ANLEGEN (3 Slots a 45min, 15min Pause)
    await page.click('nav button:has-text("slots")');
    
    const slots = [
      { name: 'Slot 1', start: '2025-05-20T09:00', end: '2025-05-20T09:45' },
      { name: 'Slot 2', start: '2025-05-20T10:00', end: '2025-05-20T10:45' },
      { name: 'Slot 3', start: '2025-05-20T11:00', end: '2025-05-20T11:45' }
    ];

    for (const slot of slots) {
      await page.click('button:has-text("+ Neu")');
      await page.fill('label:has-text("Bezeichnung") + input', slot.name);
      await page.fill('label:has-text("Beginn") + input', slot.start);
      await page.fill('label:has-text("Ende") + input', slot.end);
      await page.click('button:has-text("Slot erstellen")');
    }
    await expect(page.locator('td:has-text("Slot 3")')).toBeVisible();

    // 6. REFERENTEN & VORTRÄGE ANLEGEN (Beispielhaft für einen)
    await page.click('nav button:has-text("benutzer")');
    await page.click('button:has-text("+ Neu")');
    await page.selectOption('select:has-text("Teilnehmer")', 'REFERENT');
    await page.fill('label:has-text("Vorname") + input', 'Max');
    await page.fill('label:has-text("Nachname") + input', 'Referent');
    await page.fill('label:has-text("E-Mail") + input', 'max@referent.de');
    await page.click('button:has-text("Benutzer erstellen")');

    await page.click('nav button:has-text("vorträge")');
    await page.click('button:has-text("+ Neu")');
    await page.fill('label:has-text("Titel") + input', 'Vortrag von Max');
    await page.selectOption('select:has-text("Referent")', { label: /Referent/ });
    await page.click('button:has-text("Speichern")');
    await expect(page.locator('td:has-text("Vortrag von Max")')).toBeVisible();

    // 7. TEILNEHMER ANLEGEN (Beispielhaft für einen)
    await page.click('nav button:has-text("benutzer")');
    await page.click('button:has-text("+ Neu")');
    await page.fill('label:has-text("Vorname") + input', 'Tom');
    await page.fill('label:has-text("Nachname") + input', 'Teilnehmer');
    await page.fill('label:has-text("E-Mail") + input', 'tom@student.de');
    await page.click('button:has-text("Benutzer erstellen")');

    // 8. KONTROLLE IN VORTRAGSPLANUNG
    await page.click('nav button:has-text("Vortragsplanung")');
    await expect(page.locator('div:has-text("Teilnehmer") + div')).toContainText('1'); // Da wir nur 1 angelegt haben
    await expect(page.locator('button:has-text("MiniZinc Optimierung starten")')).toBeEnabled();
  });

});
