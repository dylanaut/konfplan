import { createRouter, createWebHistory } from 'vue-router';
import { useAuthStore } from '../stores/auth';

const Login = () => import('../views/Login.vue');
const ResetPassword = () => import('../views/ResetPassword.vue');
const TeilnehmerDashboard = () => import('../views/TeilnehmerDashboard.vue');
const ReferentDashboard = () => import('../views/ReferentDashboard.vue');
const AdminDashboard = () => import('../views/AdminDashboard.vue');
const FreieSlotsReferenten = () => import('../views/FreieSlotsReferenten.vue');
const LaufzettelTeilnehmer = () => import('../views/LaufzettelTeilnehmer.vue');
const LaufzettelReferent = () => import('../views/LaufzettelReferent.vue');
const Raumbelegungsplan = () => import('../views/Raumbelegungsplan.vue');
const UebersichtRaeume = () => import('../views/UebersichtRaeume.vue');
const Raumschilder = () => import('../views/Raumschilder.vue');
const FreieSlotsTeilnehmer = () => import('../views/FreieSlotsTeilnehmer.vue');
const StundenplanDashboard = () => import('../views/StundenplanDashboard.vue');
const TeilnehmerDashboardReport = () => import('../views/TeilnehmerDashboardReport.vue');
const PriosDashboard = () => import('../views/PriosDashboard.vue');
const LaufzettelAlle = () => import('../views/LaufzettelAlle.vue');

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
        path: '/admin/veranstaltung/:vid/freie-slots-referenten',
        name: 'FreieSlotsReferenten',
        component: FreieSlotsReferenten,
        meta: { requiresAuth: true, role: 'ADMIN' }
    },
    {
        path: '/veranstaltung/:vid/teilnehmer/:tid/laufzettel',
        name: 'LaufzettelTeilnehmer',
        component: LaufzettelTeilnehmer,
        meta: { requiresAuth: true } // Accessible by both ADMIN and TEILNEHMER
    },
    {
        path: '/veranstaltung/:vid/referent/:rid/laufzettel',
        name: 'LaufzettelReferent',
        component: LaufzettelReferent,
        meta: { requiresAuth: true } // Accessible by both ADMIN and REFERENT
    },
    {
        path: '/veranstaltung/:vid/raum/:rid/belegungsplan',
        name: 'Raumbelegungsplan',
        component: Raumbelegungsplan,
        meta: { requiresAuth: true, role: 'ADMIN' }
    },
    {
        path: '/admin/veranstaltung/:vid/uebersicht-raeume',
        name: 'UebersichtRaeume',
        component: UebersichtRaeume,
        meta: { requiresAuth: true, role: 'ADMIN' }
    },
    {
        path: '/admin/veranstaltung/:vid/raumschilder',
        name: 'Raumschilder',
        component: Raumschilder,
        meta: { requiresAuth: true, role: 'ADMIN' }
    },
    {
        path: '/admin/veranstaltung/:vid/freie-slots-teilnehmer',
        name: 'FreieSlotsTeilnehmer',
        component: FreieSlotsTeilnehmer,
        meta: { requiresAuth: true, role: 'ADMIN' }
    },
    {
        path: '/admin/veranstaltung/:vid/dashboard/stundenplan',
        name: 'StundenplanDashboard',
        component: StundenplanDashboard,
        meta: { requiresAuth: true, role: 'ADMIN' }
    },
    {
        path: '/veranstaltung/:vid/dashboard/teilnehmer',
        name: 'TeilnehmerDashboardReport',
        component: TeilnehmerDashboardReport,
        meta: { requiresAuth: true }
    },
    {
        path: '/admin/veranstaltung/:vid/dashboard/prios',
        name: 'PriosDashboard',
        component: PriosDashboard,
        meta: { requiresAuth: true, role: 'ADMIN' }
    },
    {
        path: '/admin/veranstaltung/:vid/laufzettel-alle',
        name: 'LaufzettelAlle',
        component: LaufzettelAlle,
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
