import { createRouter, createWebHistory } from 'vue-router';
import { useAuthStore } from '../stores/auth';

const Login = () => import('../views/Login.vue');
const ResetPassword = () => import('../views/ResetPassword.vue');
const TeilnehmerDashboard = () => import('../views/TeilnehmerDashboard.vue');
const ReferentDashboard = () => import('../views/ReferentDashboard.vue');
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
        meta: { requiresAuth: false }
    },
    {
        path: '/reset-password',
        name: 'ResetPassword',
        component: ResetPassword,
        meta: { requiresAuth: false }
    },
    {
        path: '/teilnehmer',
        name: 'Teilnehmer',
        component: TeilnehmerDashboard,
        meta: { requiresAuth: true, role: 'TEILNEHMER' }
    },
    {
        path: '/referent',
        name: 'Referent',
        component: ReferentDashboard,
        meta: { requiresAuth: true, role: 'REFERENT' }
    },
    {
        path: '/admin',
        name: 'Admin',
        component: AdminDashboard,
        meta: { requiresAuth: true, role: 'ADMIN' }
    },
    {
        path: '/:pathMatch(.*)*',
        redirect: '/login'
    }
];

const router = createRouter({
    history: createWebHistory(),
    routes
});

router.beforeEach((to, from, next) => {
    const authStore = useAuthStore();
    const requiresAuth = to.meta?.requiresAuth ?? false;
    const requiredRole = to.meta?.role ?? null;

    if (requiresAuth && !authStore.isAuthenticated) {
        return next('/login');
    }

    if (requiresAuth && requiredRole && authStore.userRole !== requiredRole) {
        console.warn(`Zugriff verweigert: Rolle ${requiredRole} erforderlich.`);
        return next('/login');
    }

    next();
});

export default router;
