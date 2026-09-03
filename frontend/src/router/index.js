import { createRouter, createWebHistory } from 'vue-router';
import { useAuthStore } from '../stores/auth';

const Redirecting = () => import('../views/Redirecting.vue');
const TeilnehmerDashboard = () => import('../views/TeilnehmerDashboard.vue');
const ReferentDashboard = () => import('../views/ReferentDashboard.vue');
const OrganisatorDashboard = () => import('../views/OrganisatorDashboard.vue');
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
const WahlvortraegeUebersicht = () => import('../views/report/WahlvortraegeUebersicht.vue');
const VortragAnmeldungen = () => import('../views/report/VortragAnmeldungen.vue');

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
        path: '/organisator',
        name: 'Organisator',
        component: OrganisatorDashboard,
        meta: { requiresAuth: true, role: 'ORGANISATOR' }
    },
    {
        path: '/organisator/veranstaltung/:vid/freie-slots-referenten',
        name: 'FreieSlotsReferenten',
        component: FreieSlotsReferenten,
        meta: { requiresAuth: true, role: 'ORGANISATOR' }
    },
    {
        path: '/veranstaltung/:vid/teilnehmer/:tid/laufzettel',
        name: 'LaufzettelTeilnehmer',
        component: LaufzettelTeilnehmer,
        meta: { requiresAuth: true } // Accessible by both ORGANISATOR/ADMINISTRATOR and TEILNEHMER
    },
    {
        path: '/veranstaltung/:vid/referent/:rid/laufzettel',
        name: 'LaufzettelReferent',
        component: LaufzettelReferent,
        meta: { requiresAuth: true } // Accessible by both ORGANISATOR/ADMINISTRATOR and REFERENT
    },
    {
        path: '/veranstaltung/:vid/raum/:rid/belegungsplan',
        name: 'Raumbelegungsplan',
        component: Raumbelegungsplan,
        meta: { requiresAuth: true, role: 'ORGANISATOR' }
    },
    {
        path: '/organisator/veranstaltung/:vid/uebersicht-raeume',
        name: 'UebersichtRaeume',
        component: UebersichtRaeume,
        meta: { requiresAuth: true, role: 'ORGANISATOR' }
    },
    {
        path: '/organisator/veranstaltung/:vid/raumschilder',
        name: 'Raumschilder',
        component: Raumschilder,
        meta: { requiresAuth: true, role: 'ORGANISATOR' }
    },
    {
        path: '/organisator/veranstaltung/:vid/freie-slots-teilnehmer',
        name: 'FreieSlotsTeilnehmer',
        component: FreieSlotsTeilnehmer,
        meta: { requiresAuth: true, role: 'ORGANISATOR' }
    },
    {
        path: '/veranstaltung/:vid/dashboard/teilnehmer',
        name: 'TeilnehmerZuordnungen',
        component: TeilnehmerZuordnungen,
        meta: { requiresAuth: true }
    },
    {
        path: '/organisator/veranstaltung/:vid/dashboard/prios',
        name: 'Prioritaeten',
        component: Prioritaeten,
        meta: { requiresAuth: true, role: 'ORGANISATOR' }
    },
    {
        path: '/organisator/veranstaltung/:vid/laufzettel-alle',
        name: 'LaufzettelAlle',
        component: LaufzettelAlle,
        meta: { requiresAuth: true, role: 'ORGANISATOR' }
    },
    {
        path: '/organisator/veranstaltung/:vid/laufzettel-alle-referenten',
        name: 'LaufzettelAlleReferenten',
        component: LaufzettelAlleReferenten,
        meta: { requiresAuth: true, role: 'ORGANISATOR' }
    },
    {
        path: '/organisator/veranstaltung/:vid/abstimmungsfragebogen-alle',
        name: 'AbstimmungsfragebogenAlle',
        component: AbstimmungsfragebogenAlle,
        meta: { requiresAuth: true, role: 'ORGANISATOR' }
    },
    {
        path: '/organisator/veranstaltung/:vid/stundenplan',
        name: 'Stundenplan',
        component: Stundenplan,
        props: true,
        meta: { requiresAuth: true, role: 'ORGANISATOR' }
    },
    {
        path: '/organisator/veranstaltung/:vid/anwesenheiten',
        name: 'Anwesenheiten',
        component: Anwesenheiten,
        meta: { requiresAuth: true, role: 'ORGANISATOR' }
    },
    {
        path: '/organisator/veranstaltung/:vid/wahlvortraege-uebersicht',
        name: 'WahlvortraegeUebersicht',
        component: WahlvortraegeUebersicht,
        meta: { requiresAuth: true, role: 'ORGANISATOR' }
    },
    {
        path: '/organisator/veranstaltung/:vid/vortrag/:vortragId/anmeldungen',
        name: 'VortragAnmeldungen',
        component: VortragAnmeldungen,
        meta: { requiresAuth: true, role: 'ORGANISATOR' }
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

// ORGANISATOR-Routen sollen auch fuer ADMINISTRATOR zugaenglich sein (Administrator hat
// dieselben Rechte, siehe stores/auth.js isOrganisator) - daher hier ueber die semantischen
// Store-Getter statt per strikter String-Gleichheit auf authStore.userRole pruefen.
const ROLE_CHECKS = {
    ORGANISATOR: (authStore) => authStore.isOrganisator,
    ADMINISTRATOR: (authStore) => authStore.isAdministrator,
    REFERENT: (authStore) => authStore.isSpeaker,
    TEILNEHMER: (authStore) => authStore.isParticipant
};

router.beforeEach((to, from, next) => {
    const authStore = useAuthStore();
    const requiresAuth = to.meta?.requiresAuth ?? false;
    const requiredRole = to.meta?.role ?? null;

    if (requiresAuth && !authStore.isAuthenticated) {
        // from.matched.length === 0 bedeutet: erste Navigation nach einem (Neu-)Laden der App,
        // kein "davor" innerhalb der SPA - "nicht angemeldet" ist dort der Normalfall (z.B.
        // direkt aufgerufener Link), keine abgelaufene Sitzung. Erst bei einer echten
        // In-App-Navigation (u.a. Browser-Zurueck auf eine vorher schon besuchte Seite) ist der
        // Hinweis auf eine abgelaufene/beendete Sitzung hilfreich statt verwirrend.
        const cameFromWithinApp = from.matched.length > 0;
        authStore.requireLogin(window.location.origin + to.fullPath, { silent: !cameFromWithinApp });
        return next(false);
    }

    if (requiresAuth && requiredRole && !(ROLE_CHECKS[requiredRole]?.(authStore) ?? false)) {
        console.warn(`Zugriff verweigert: Rolle ${requiredRole} erforderlich.`);
        return next('/');
    }

    next();
});

export default router;
