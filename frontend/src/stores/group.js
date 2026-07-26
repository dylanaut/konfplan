import { defineStore } from 'pinia';
import { ref } from 'vue';
import api from '../api/axios';
import { useToast } from 'vue-toastification';

export const useGroupStore = defineStore('group', () => {
    const gruppen = ref([]);
    const toast = useToast();

    async function fetchGruppen(veranstaltungId) {
        if (!veranstaltungId) {
            gruppen.value = [];
            return;
        }
        try {
            const response = await api.get(`/api/admin/veranstaltungen/${veranstaltungId}/gruppen`);
            gruppen.value = response.data;
        } catch (error) {
            toast.error('Gruppen konnten nicht geladen werden.');
            console.error(error);
        }
    }

    async function addGruppe(veranstaltungId, gruppenName) {
        try {
            await api.post(`/api/admin/veranstaltungen/${veranstaltungId}/gruppen`, gruppenName, {
                headers: { 'Content-Type': 'text/plain' }
            });
            await fetchGruppen(veranstaltungId);
            toast.success(`Gruppe '${gruppenName}' erfolgreich erstellt.`);
        } catch (error) {
            toast.error(error.response?.data || `Fehler beim Erstellen der Gruppe '${gruppenName}'.`);
            console.error(error);
        }
    }

    async function renameGruppe(veranstaltungId, alterName, neuerName) {
        try {
            await api.put(`/api/admin/veranstaltungen/${veranstaltungId}/gruppen`, null, {
                params: { alterName, neuerName }
            });
            await fetchGruppen(veranstaltungId);
            toast.success(`Gruppe '${alterName}' in '${neuerName}' umbenannt.`);
        } catch (error) {
            toast.error(error.response?.data || `Fehler beim Umbenennen der Gruppe.`);
            console.error(error);
        }
    }

    async function deleteGruppe(veranstaltungId, gruppenName) {
        try {
            await api.delete(`/api/admin/veranstaltungen/${veranstaltungId}/gruppen/${gruppenName}`);
            await fetchGruppen(veranstaltungId);
            toast.success(`Gruppe '${gruppenName}' gelöscht.`);
        } catch (error) {
            toast.error(error.response?.data || `Fehler beim Löschen der Gruppe '${gruppenName}'.`);
            console.error(error);
        }
    }

    return { gruppen, fetchGruppen, addGruppe, renameGruppe, deleteGruppe };
});