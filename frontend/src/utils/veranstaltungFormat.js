const DAY_OPTIONS = { day: '2-digit', month: '2-digit', year: 'numeric' };
const TIME_OPTIONS = { hour: '2-digit', minute: '2-digit' };

export function formatZeitraum(veranstaltung) {
  if (!veranstaltung?.beginntAm || !veranstaltung?.endetAm) return '';

  const start = new Date(veranstaltung.beginntAm);
  const end = new Date(veranstaltung.endetAm);
  const startTag = start.toLocaleDateString('de-DE', DAY_OPTIONS);
  const startZeit = start.toLocaleTimeString('de-DE', TIME_OPTIONS);
  const endeZeit = end.toLocaleTimeString('de-DE', TIME_OPTIONS);

  if (start.toDateString() === end.toDateString()) {
    return `${startTag}, ${startZeit} - ${endeZeit}`;
  }
  const endeTag = end.toLocaleDateString('de-DE', DAY_OPTIONS);
  return `${startTag}, ${startZeit} - ${endeTag}, ${endeZeit}`;
}
