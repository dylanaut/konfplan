import { defineStore } from 'pinia';
import { ref } from 'vue';

export const useEventContextStore = defineStore('eventContext', () => {
    const selectedEvent = ref(null);

    function setEvent(event) {
        selectedEvent.value = event;
    }

    function clearEvent() {
        selectedEvent.value = null;
    }

    return { selectedEvent, setEvent, clearEvent };
});