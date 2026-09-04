import { defineStore } from 'pinia';
import { ref } from 'vue';
import api from '../api/axios';

export const useMessageBoxStore = defineStore('messageBox', () => {
    const messages = ref([]);
    const unreadCount = ref(0);

    async function fetchUnreadCount() {
        try {
            const response = await api.get('/api/nachrichten/ungelesen-anzahl');
            unreadCount.value = response.data;
        } catch (error) {
            console.error('Ungelesene Nachrichten konnten nicht geladen werden.', error);
        }
    }

    async function fetchMessages() {
        try {
            const response = await api.get('/api/nachrichten');
            messages.value = response.data;
        } catch (error) {
            console.error('Nachrichten konnten nicht geladen werden.', error);
        }
    }

    async function markAsRead(id) {
        const message = messages.value.find(m => m.id === id);
        if (!message || message.gelesenAm) {
            return;
        }
        try {
            await api.put(`/api/nachrichten/${id}/gelesen`);
            message.gelesenAm = new Date().toISOString();
            unreadCount.value = Math.max(0, unreadCount.value - 1);
        } catch (error) {
            console.error('Nachricht konnte nicht als gelesen markiert werden.', error);
        }
    }

    return { messages, unreadCount, fetchUnreadCount, fetchMessages, markAsRead };
});
