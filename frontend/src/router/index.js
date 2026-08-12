import { createRouter, createWebHistory } from 'vue-router';
import { useAuthStore } from '../stores/auth';

const Redirecting = () => import('../views/Redirecting.vue');
const TeilnehmerDashboard = () => import('../views/TeilnehmerDashboard.vue');
const ReferentDashboard = () => import('../views/ReferentDashboard.vue');
const AdminDashboard = () => import('../views/AdminDashboard.vue');
const FreieSlotsReferenten = () => import('../views/report/FreieSlotsReferenten.vue');
const LaufzettelTeilnehmer = () => import('../views/report/LaufzettelTeilnehmer.vue');
const LaufzettelReferent = () => import('../views/report/LaufzettelReferent.vue');
const Raumbelegungsplan = () => import('../views/report/Raumbelegungsplan.vue');
const UebersichtRaeume = () => import('../views/report/UebersichtRaeume.vue');
const Raumschilder = () => import('../views/report/Raumschilder.vue');
const FreieSlotsTeilnehmer = () => import('../views/report/FreieSlotsTeilnehmer.vue');
const TeilnehmerZuordnungen = () => import('../views/report/TeilnehmerZuordnungen.vue');
const Prioritaeten = () => import('../views/report/Prioritaeten.vue');
const LaufzettelAlle = () => import('../views/report/LaufzettelAlle.vue');
const AbstimmungsfragebogenAlle = () => import('../views/report/AbstimmungsfragebogenAlle.vue');
const LaufzettelAlleReferenten = () => import('../views/report/LaufzettelAlleReferenten.vue');
const Stundenplan = () => import('../views/report/Stundenplan.vue');
const Anwesenheiten = () => import('../views/report/Anwesenheiten.vue');

const routes = [
    {
        path: '/',
        name: 'Redirecting',
        component: Redirecting,
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
        path: '/veranstaltung/:vid/dashboard/teilnehmer',
        name: 'TeilnehmerZuordnungen',
        component: TeilnehmerZuordnungen,
        meta: { requiresAuth: true }
    },
    {
        path: '/admin/veranstaltung/:vid/dashboard/prios',
        name: 'Prioritaeten',
        component: Prioritaeten,
        meta: { requiresAuth: true, role: 'ADMIN' }
    },
    {
        path: '/admin/veranstaltung/:vid/laufzettel-alle',
        name: 'LaufzettelAlle',
        component: LaufzettelAlle,
        meta: { requiresAuth: true, role: 'ADMIN' }
    },
    {
        path: '/admin/veranstaltung/:vid/laufzettel-alle-referenten',
        name: 'LaufzettelAlleReferenten',
        component: LaufzettelAlleReferenten,
        meta: { requiresAuth: true, role: 'ADMIN' }
    },
    {
        path: '/admin/veranstaltung/:vid/abstimmungsfragebogen-alle',
        name: 'AbstimmungsfragebogenAlle',
        component: AbstimmungsfragebogenAlle,
        meta: { requiresAuth: true, role: 'ADMIN' }
    },
    {
        path: '/admin/veranstaltung/:vid/stundenplan',
        name: 'Stundenplan',
        component: Stundenplan,
        props: true,
        meta: { requiresAuth: true, role: 'ADMIN' }
    },
    {
        path: '/admin/veranstaltung/:vid/anwesenheiten',
        name: 'Anwesenheiten',
        component: Anwesenheiten,
        meta: { requiresAuth: true, role: 'ADMIN' }
    },
    {
        path: '/:pathMatch(.*)*',
        redirect: '/'
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
        authStore.login({ redirectUri: window.location.origin + to.fullPath });
        return next(false);
    }

    if (requiresAuth && requiredRole && authStore.userRole !== requiredRole) {
        console.warn(`Zugriff verweigert: Rolle ${requiredRole} erforderlich.`);
        return next('/');
    }

    next();
});

export default router;
