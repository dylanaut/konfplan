import { defineStore } from 'pinia';
import { ref } from 'vue';
import api from '../api/axios';
import { useToast } from 'vue-toastification';

export const useVeranlagungStore = defineStore('veranlagung', () => {
    const veranlagungen = ref([]);
    const toast = useToast();

    async function fetchVeranlagungen() {
        if (veranlagungen.value.length > 0) {
            return;
        }
        try {
            const response = await api.get('/api/veranlagungen');
            veranlagungen.value = response.data;
        } catch (error) {
            toast.error('Veranlagungen konnten nicht geladen werden.');
            console.error(error);
        }
    }

    return { veranlagungen, fetchVeranlagungen };
});
