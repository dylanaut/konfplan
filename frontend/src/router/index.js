import { createRouter, createWebHistory } from 'vue-router';
import { useAuthStore } from '../stores/auth';

// Import der Views (Lazy Loading für bessere Performance)
const Login = () => import('../views/Login.vue');
const ResetPassword = () => import('../views/ResetPassword.vue');
const ParticipantDashboard = () => import('../views/ParticipantDashboard.vue');
const SpeakerDashboard = () => import('../views/SpeakerDashboard.vue');
const AdminDashboard = () => import('../views/AdminDashboard.vue');

const routes = [
    {
        path: '/',
        redirect: '/login'
    },
    {
        path: '/login',
        name: 'Login',
        component: Login,
        meta: { requiresAuth: false } // Explizit auf false setzen
    },
    {
        path: '/reset-password',
        name: 'ResetPassword',
        component: ResetPassword,
        meta: { requiresAuth: false }
    },
    {
        path: '/participant',
        name: 'Participant',
        component: ParticipantDashboard,
        meta: { requiresAuth: true, role: 'PARTICIPANT' }
    },
    {
        path: '/speaker',
        name: 'Speaker',
        component: SpeakerDashboard,
        meta: { requiresAuth: true, role: 'SPEAKER' }
    },
    {
        path: '/admin',
        name: 'Admin',
        component: AdminDashboard,
        meta: { requiresAuth: true, role: 'ADMIN' }
    },
    // Catch-all Route für 404 Fehler (optional)
    {
        path: '/:pathMatch(.*)*',
        redirect: '/login'
    }
];

const router = createRouter({
    history: createWebHistory(),
    routes
});

// Der Navigation Guard (Sicherheitsprüfung)
router.beforeEach((to, from, next) => {
    const authStore = useAuthStore();

    // Sicherstellen, dass wir einen Standardwert haben, falls meta nicht definiert ist
    const requiresAuth = to.meta?.requiresAuth ?? false;
    const requiredRole = to.meta?.role ?? null;

    // 1. Prüfung: Muss der User eingeloggt sein?
    if (requiresAuth && !authStore.isAuthenticated) {
        // Nicht eingeloggt -> ab zum Login
        return next('/login');
    }

    // 2. Prüfung: Wenn eingeloggt, hat er die richtige Rolle für diese Seite?
    if (requiresAuth && requiredRole && authStore.userRole !== requiredRole) {
        // Rolle passt nicht -> zur entsprechenden Startseite oder Login
        console.warn(`Zugriff verweigert: Rolle ${requiredRole} erforderlich.`);
        return next('/login');
    }

    // 3. Wenn alles okay ist oder die Seite öffentlich ist -> weitergehen
    next();
});

export default router;