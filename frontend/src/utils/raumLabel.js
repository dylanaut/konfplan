export function raumLabel(raum) {
  if (!raum?.name) return '';
  return raum.gebaeudeKuerzel ? `${raum.gebaeudeKuerzel} ${raum.name}` : raum.name;
}
