import { defineStore } from 'pinia';
import { ref } from 'vue';
import api from '../api/axios';
import { useToast } from 'vue-toastification';
import { extractErrorMessage } from '../utils/errorMessage';

export const usePasswortrichtlinieStore = defineStore('passwortrichtlinie', () => {
    const richtlinien = ref([]);
    const toast = useToast();

    async function fetchRichtlinien(veranstaltungId) {
        if (!veranstaltungId) {
            richtlinien.value = [];
            return;
        }
        try {
            const response = await api.get(`/api/organisator/veranstaltungen/${veranstaltungId}/passwortrichtlinien`);
            richtlinien.value = response.data;
        } catch (error) {
            toast.error('Passwortrichtlinien konnten nicht geladen werden.');
            console.error(error);
        }
    }

    async function saveRichtlinie(veranstaltungId, rolle, richtlinie) {
        try {
            await api.put(`/api/organisator/veranstaltungen/${veranstaltungId}/passwortrichtlinien/${rolle}`, richtlinie);
            await fetchRichtlinien(veranstaltungId);
            toast.success(`Passwortrichtlinie für '${rolle}' gespeichert.`);
            return true;
        } catch (error) {
            toast.error(extractErrorMessage(error, `Fehler beim Speichern der Passwortrichtlinie für '${rolle}'.`));
            console.error(error);
            return false;
        }
    }

    async function resetRichtlinie(veranstaltungId, rolle) {
        try {
            await api.delete(`/api/organisator/veranstaltungen/${veranstaltungId}/passwortrichtlinien/${rolle}`);
            await fetchRichtlinien(veranstaltungId);
            toast.success(`Passwortrichtlinie für '${rolle}' auf Standard zurückgesetzt.`);
        } catch (error) {
            toast.error(extractErrorMessage(error, `Fehler beim Zurücksetzen der Passwortrichtlinie für '${rolle}'.`));
            console.error(error);
        }
    }

    return {
        richtlinien,
        fetchRichtlinien,
        saveRichtlinie,
        resetRichtlinie,
    };
});
