import { defineStore } from 'pinia';
import { ref } from 'vue';
import api from '../api/axios';
import { useToast } from 'vue-toastification';
import { extractErrorMessage } from '../utils/errorMessage';

export const useGruppenkategorieStore = defineStore('gruppenkategorie', () => {
    const gruppenkategorien = ref([]);
    const toast = useToast();

    async function fetchGruppenkategorien(veranstaltungId) {
        if (!veranstaltungId) {
            gruppenkategorien.value = [];
            return;
        }
        try {
            const response = await api.get(`/api/organisator/veranstaltungen/${veranstaltungId}/gruppenkategorien`);
            gruppenkategorien.value = response.data;
        } catch (error) {
            toast.error('Gruppenkategorien konnten nicht geladen werden.');
            console.error(error);
        }
    }

    async function createGruppenkategorie(veranstaltungId, { name, mehrwertig, pflicht }) {
        try {
            await api.post(`/api/organisator/veranstaltungen/${veranstaltungId}/gruppenkategorien`, { name, mehrwertig, pflicht });
            await fetchGruppenkategorien(veranstaltungId);
            toast.success(`Gruppenkategorie '${name}' erfolgreich erstellt.`);
            return true;
        } catch (error) {
            toast.error(extractErrorMessage(error, `Fehler beim Erstellen der Gruppenkategorie '${name}'.`));
            console.error(error);
            return false;
        }
    }

    async function updateGruppenkategorie(veranstaltungId, kategorieId, { name, mehrwertig, pflicht }) {
        try {
            await api.put(`/api/organisator/gruppenkategorien/${kategorieId}`, { name, mehrwertig, pflicht });
            await fetchGruppenkategorien(veranstaltungId);
            toast.success(`Gruppenkategorie '${name}' aktualisiert.`);
            return true;
        } catch (error) {
            toast.error(extractErrorMessage(error, `Fehler beim Aktualisieren der Gruppenkategorie '${name}'.`));
            console.error(error);
            return false;
        }
    }

    async function deleteGruppenkategorie(veranstaltungId, kategorie) {
        try {
            await api.delete(`/api/organisator/gruppenkategorien/${kategorie.id}`);
            await fetchGruppenkategorien(veranstaltungId);
            toast.success(`Gruppenkategorie '${kategorie.name}' gelöscht.`);
        } catch (error) {
            toast.error(extractErrorMessage(error, `Fehler beim Löschen der Gruppenkategorie '${kategorie.name}'.`));
            console.error(error);
        }
    }

    async function addWert(veranstaltungId, kategorieId, wert) {
        try {
            await api.post(`/api/organisator/gruppenkategorien/${kategorieId}/werte`, wert, {
                headers: { 'Content-Type': 'text/plain' }
            });
            await fetchGruppenkategorien(veranstaltungId);
            toast.success(`Wert '${wert}' hinzugefügt.`);
        } catch (error) {
            toast.error(extractErrorMessage(error, `Fehler beim Hinzufügen des Werts '${wert}'.`));
            console.error(error);
        }
    }

    async function renameWert(veranstaltungId, wert, neuerWert) {
        try {
            await api.put(`/api/organisator/gruppenkategorien/werte/${wert.id}`, neuerWert, {
                headers: { 'Content-Type': 'text/plain' }
            });
            await fetchGruppenkategorien(veranstaltungId);
            toast.success(`Wert '${wert.wert}' in '${neuerWert}' umbenannt.`);
        } catch (error) {
            toast.error(extractErrorMessage(error, `Fehler beim Umbenennen des Werts '${wert.wert}'.`));
            console.error(error);
        }
    }

    async function removeWert(veranstaltungId, wert) {
        try {
            await api.delete(`/api/organisator/gruppenkategorien/werte/${wert.id}`);
            await fetchGruppenkategorien(veranstaltungId);
            toast.success(`Wert '${wert.wert}' gelöscht.`);
        } catch (error) {
            toast.error(extractErrorMessage(error, `Fehler beim Löschen des Werts '${wert.wert}'.`));
            console.error(error);
        }
    }

    return {
        gruppenkategorien,
        fetchGruppenkategorien,
        createGruppenkategorie,
        updateGruppenkategorie,
        deleteGruppenkategorie,
        addWert,
        renameWert,
        removeWert
    };
});
