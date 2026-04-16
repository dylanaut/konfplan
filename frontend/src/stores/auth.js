import { defineStore } from 'pinia';
import router from '../router';

export const useAuthStore = defineStore('auth', {
    state: () => ({
        token: localStorage.getItem('token') || null,
        userRole: localStorage.getItem('role') || null,
    }),
    getters: {
        isAuthenticated: (state) => !!state.token,
        isAdmin: (state) => state.userRole === 'ADMIN',
        isSpeaker: (state) => state.userRole === 'REFERENT',
        isParticipant: (state) => state.userRole === 'TEILNEHMER',
    },
    actions: {
        setToken(token, role) {
            this.token = token;
            this.userRole = role;
            localStorage.setItem('token', token);
            localStorage.setItem('role', role);
        },
        logout() {
            this.token = null;
            this.userRole = null;
            localStorage.removeItem('token');
            localStorage.removeItem('role');
            
            // Weiterleitung zur Login-Seite
            router.push('/login');
        }
    }
});
