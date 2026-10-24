// Liest den 6-stelligen Brevo-Bestaetigungscode (Geraeteverifizierung beim Login, Step-up beim
// IP-Eintrag) per IMAP aus dem Postfach, an das Brevo die Code-Mails schickt. Gemeinsam genutzt
// von setup-session.mjs und update-ip.mjs.
//
// Konfiguration ueber BREVO_CODE_IMAP_HOST/_PORT/_USER/_PASSWORD (siehe setup-session.mjs).
import { ImapFlow } from 'imapflow';
import { simpleParser } from 'mailparser';

const IMAP_HOST = process.env.BREVO_CODE_IMAP_HOST;
const IMAP_PORT = process.env.BREVO_CODE_IMAP_PORT ? Number(process.env.BREVO_CODE_IMAP_PORT) : 993;
const IMAP_USER = process.env.BREVO_CODE_IMAP_USER;
const IMAP_PASSWORD = process.env.BREVO_CODE_IMAP_PASSWORD;
export const IMAP_CONFIGURED = Boolean(IMAP_HOST && IMAP_USER && IMAP_PASSWORD);

// Nutzer-Beobachtung (per Live-Test): ein bereits zugestellter Code bleibt ca. 90s gueltig,
// danach schickt Brevo einen NEUEN Code per E-Mail. Das Zeitfenster hier deckt das grosszuegig ab.
const CODE_POLL_INTERVAL_MS = 3000;
const CODE_POLL_TIMEOUT_MS = 90000;

function createImapClient() {
  return new ImapFlow({
    host: IMAP_HOST,
    port: IMAP_PORT,
    secure: true,
    auth: { user: IMAP_USER, pass: IMAP_PASSWORD },
    logger: false,
  });
}

// Muss VOR dem Ausloesen der Code-Mail aufgerufen werden - liefert die UID, ab der neu
// ankommende Mails als "waehrend dieses Versuchs zugestellt" gelten. Verhindert, dass eine
// bereits im Postfach liegende (evtl. laengst verbrauchte/abgelaufene) Code-Mail aus einem
// fruehreren Lauf faelschlich wiederverwendet wird.
export async function getStartingUid() {
  const client = createImapClient();
  await client.connect();
  try {
    const status = await client.status('INBOX', { uidNext: true });
    return status.uidNext;
  } finally {
    await client.logout().catch(() => {});
  }
}

export async function pollForVerificationCodeViaImap(sinceUid) {
  const client = createImapClient();
  await client.connect();
  try {
    const lock = await client.getMailboxLock('INBOX');
    try {
      const deadline = Date.now() + CODE_POLL_TIMEOUT_MS;
      while (Date.now() < deadline) {
        /* IMAP liefert fuer "N:*" immer mindestens die hoechste UID, auch wenn sie < N ist - herausfiltern. */
        const uids = ((await client.search({ uid: `${sinceUid}:*` }, { uid: true })) || []).filter((uid) => uid >= sinceUid);
        if (uids.length > 0) {
          const newestUid = Math.max(...uids);
          const { content } = await client.download(newestUid, undefined, { uid: true });
          const parsed = await simpleParser(content);
          const text = `${parsed.text ?? ''}\n${parsed.html ?? ''}`;
          const match = text.match(/\b(\d{6})\b/);
          if (match) {
            return match[1];
          }
        }
        await new Promise(resolve => setTimeout(resolve, CODE_POLL_INTERVAL_MS));
      }
      throw new Error(`Kein Bestätigungscode innerhalb von ${CODE_POLL_TIMEOUT_MS / 1000}s per E-Mail erhalten (IMAP).`);
    } finally {
      lock.release();
    }
  } finally {
    await client.logout().catch(() => {});
  }
}
