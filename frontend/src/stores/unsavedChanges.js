import { defineStore } from 'pinia';
import { ref } from 'vue';

/**
 * Hält die Dirty-Check-Funktion der aktuell aktiven editierbaren Ansicht (Teilnehmer-,
 * Referenten- oder Organisator-Dashboard), damit App.vue vor einem manuellen Logout prüfen
 * kann, ob ungespeicherte Änderungen verloren gingen. Da Vue Router immer nur eines dieser
 * Dashboards gleichzeitig mountet, genügt ein einzelner Slot statt einer Registry.
 */
export const useUnsavedChangesStore = defineStore('unsavedChanges', () => {
    const dirtyCheck = ref(null);

    function registerDirtyCheck(fn) {
        dirtyCheck.value = fn;
    }

    function clearDirtyCheck() {
        dirtyCheck.value = null;
    }

    function hasUnsavedChanges() {
        return dirtyCheck.value ? dirtyCheck.value() : false;
    }

    return { registerDirtyCheck, clearDirtyCheck, hasUnsavedChanges };
});
