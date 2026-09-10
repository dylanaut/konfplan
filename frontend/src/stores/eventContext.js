import { defineStore } from 'pinia';
import { ref } from 'vue';

// Merkt sich die zuletzt bearbeitete Veranstaltung im localStorage (browser-/gerätebezogen,
// nicht nutzerkontobezogen), damit das Organisator-Dashboard sie beim nächsten Login direkt
// wieder vorauswählt.
const STORAGE_KEY = 'konfplan.lastVeranstaltungId';

export const useEventContextStore = defineStore('eventContext', () => {
    const selectedEvent = ref(null);

    function setEvent(event) {
        selectedEvent.value = event;
        if (event?.id) {
            localStorage.setItem(STORAGE_KEY, String(event.id));
        }
    }

    function clearEvent() {
        selectedEvent.value = null;
        localStorage.removeItem(STORAGE_KEY);
    }

    function getLastVeranstaltungId() {
        const stored = localStorage.getItem(STORAGE_KEY);
        return stored ? Number(stored) : null;
    }

    return { selectedEvent, setEvent, clearEvent, getLastVeranstaltungId };
});