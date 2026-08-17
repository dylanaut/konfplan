import { defineStore } from 'pinia';
import { ref } from 'vue';
import api from '../api/axios';
import { useToast } from 'vue-toastification';

export const useNeigungStore = defineStore('neigung', () => {
    const neigungen = ref([]);
    const toast = useToast();

    async function fetchNeigungen() {
        if (neigungen.value.length > 0) {
            return;
        }
        try {
            const response = await api.get('/api/neigungen');
            neigungen.value = response.data;
        } catch (error) {
            toast.error('Neigungen konnten nicht geladen werden.');
            console.error(error);
        }
    }

    return { neigungen, fetchNeigungen };
});
