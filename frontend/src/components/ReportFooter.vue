<template>
  <footer class="report-footer" :class="seite ? 'report-footer-static' : 'report-footer-fixed print-footer'">
    <span class="report-footer-left">{{ veranstaltungName }}</span>
    <span class="report-footer-center">{{ reportTitel }}</span>
    <span class="report-footer-right">
      <template v-if="seite">Seite {{ seite }} · </template>Gedruckt am {{ druckdatum }} · © KonfPlan
    </span>
  </footer>
</template>

<script setup>
import { computed } from 'vue';

const props = defineProps({
  veranstaltungName: { type: String, default: '' },
  reportTitel: { type: String, default: '' },
  /** Seitenindex (1-basiert) - nur angeben, wenn der Report pro Entität (Raum/Referent/...) eine
   * eigene Druckseite erzeugt (page-break-after je Entität) - nur dann ist die Seitenzahl
   * zuverlässig bekannt. Bei durchlaufenden Tabellen ohne solche Struktur bleibt die Seitenzahl
   * unbekannt (Browser bieten dafür keine zuverlässige CSS-Seitenzähl-Unterstützung ohne
   * zusätzliche Pagination-Bibliothek) und wird daher weggelassen.
   *
   * Ist seite gesetzt, wird die Fußzeile NICHT position:fixed gerendert, sondern als normales,
   * einmaliges Element am Ende des jeweiligen Entitäts-Blocks: mehrere gleichzeitig sichtbare
   * position:fixed-Elemente mit unterschiedlichem Inhalt würden beim Drucken auf jeder Seite
   * überlagert erscheinen, da Chrome fixierte Elemente unabhängig von ihrer Position im DOM auf
   * jeder Druckseite wiederholt - für einen je Seite unterschiedlichen Text ungeeignet. */
  seite: { type: Number, default: null },
});

const druckdatum = computed(() => new Date().toLocaleDateString('de-DE'));
</script>

<style>
/* Bewusst nicht scoped, damit @media print global greift (wie bei den bestehenden
   Report-Druck-Styles in frontend/src/views/report/*.vue). */
.report-footer {
  display: none;
}

@media print {
  .report-footer {
    display: flex !important;
    justify-content: space-between;
    align-items: baseline;
    gap: 1rem;
    font-size: 0.75rem;
    color: #6c757d;
    padding: 0 0.25rem;
  }

  /* Ohne Seitenzahl bekannt: eine einzige Instanz im Dokument, wiederholt sich per
     position:fixed auf jeder Druckseite (bestehendes, bewährtes Verhalten). */
  .report-footer-fixed {
    position: fixed;
    bottom: 0;
    left: 0;
    width: 100%;
  }

  /* Mit Seitenzahl: eine eigene Instanz je Entitäts-Block, im normalen Textfluss am Ende dieses
     Blocks platziert (siehe seite-Prop-Kommentar oben). */
  .report-footer-static {
    margin-top: 0.75rem;
    border-top: 1px solid #dee2e6;
    padding-top: 0.25rem;
  }

  .report-footer-left {
    text-align: left;
    flex: 1 1 0;
  }

  .report-footer-center {
    text-align: center;
    flex: 1 1 0;
  }

  .report-footer-right {
    text-align: right;
    flex: 1 1 0;
    white-space: nowrap;
  }
}
</style>
