/**
 * Extrahiert eine lesbare Fehlermeldung aus einem Axios-Error.
 *
 * Das Backend liefert Fehler-Bodies uneinheitlich: die meisten Resource-Methoden
 * setzen einen reinen String (`.entity(e.getMessage())`), der BusinessExceptionMapper
 * liefert dagegen JSON `{ error: "..." }`. `error.response.data.message` (wie es die
 * Dashboards früher ausgelesen haben) existiert in keinem der beiden Fälle, daher kam
 * nie die eigentliche Server-Meldung an, sondern nur das generische Axios-Fallback
 * "Request failed with status code ...".
 */
export function extractErrorMessage(error, fallback = 'Unbekannter Fehler.') {
  const data = error?.response?.data;
  if (typeof data === 'string' && data.trim() !== '') {
    return data;
  }
  if (data && typeof data === 'object') {
    if (typeof data.error === 'string') {
      return data.error;
    }
    if (typeof data.message === 'string') {
      return data.message;
    }
  }
  return error?.message || fallback;
}
