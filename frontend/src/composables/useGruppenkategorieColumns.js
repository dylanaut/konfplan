// Gemeinsame Helfer für Tabellen/Reports, die je Gruppenkategorie (statt einer einzigen
// flachen Gruppen-Spalte, siehe #690) eine eigene, sortier- und filterbare Spalte anzeigen -
// verallgemeinert das ursprünglich in TeilnehmerTab.vue gebaute Muster.

const SORT_PREFIX = 'gk:';

export function gruppenkategorieSortKey(kategorieName) {
  return `${SORT_PREFIX}${kategorieName}`;
}

export function isGruppenkategorieSortKey(key) {
  return typeof key === 'string' && key.startsWith(SORT_PREFIX);
}

export function gruppenkategorieNameFromSortKey(key) {
  return key.slice(SORT_PREFIX.length);
}

export function getGruppenkategorieWerte(item, kategorieName) {
  return item?.gruppenwerteByKategorie?.[kategorieName] || [];
}

export function getGruppenkategorieSortValue(item, kategorieName) {
  return getGruppenkategorieWerte(item, kategorieName).join(', ');
}

export function matchesGruppenkategorieFilter(item, kategorieName, gewaehlterWert) {
  if (!gewaehlterWert) {
    return true;
  }
  return getGruppenkategorieWerte(item, kategorieName).includes(gewaehlterWert);
}
