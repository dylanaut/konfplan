<template>
  <nav class="bg-white rounded-xl border border-gray-100 shadow-sm p-2 space-y-1 no-print h-fit">
    <button @click="collapsed = !collapsed"
            class="w-full flex items-center justify-center gap-2 text-gray-400 hover:text-gray-600 p-1.5 rounded-lg hover:bg-gray-50 transition">
      <PanelLeftCloseIcon v-if="!collapsed" class="w-4 h-4"/>
      <PanelLeftOpenIcon v-else class="w-4 h-4"/>
    </button>

    <div v-for="group in groups" :key="group.name">
      <div v-if="tabsOf(group).length > 0">
        <button v-if="!collapsed" @click="toggleGroup(group.name)"
                class="w-full flex items-center justify-between gap-2 px-2 py-1.5 mt-2 text-[10px] font-black text-gray-400 uppercase tracking-widest hover:text-gray-600">
          {{ group.name }}
          <ChevronDownIcon v-if="!openGroups[group.name]" class="w-3 h-3 shrink-0"/>
          <ChevronUpIcon v-else class="w-3 h-3 shrink-0"/>
        </button>
        <div v-if="collapsed || openGroups[group.name]" class="space-y-0.5">
          <button v-for="tab in tabsOf(group)" :key="tab"
                  @click="emit('tab-click', tab)"
                  :title="tabLabels[tab] || tab"
                  :class="[
                    activeTab === tab ? 'bg-indigo-50 text-indigo-700 font-bold' : 'text-gray-500 hover:bg-gray-50 hover:text-gray-700',
                    collapsed ? 'justify-center' : '',
                    'w-full flex items-center gap-2 px-2 py-1.5 rounded-lg text-xs transition truncate'
                  ]">
            <component :is="tabIcons[tab]" class="w-3.5 h-3.5 shrink-0"/>
            <span v-if="!collapsed" class="truncate">{{ tabLabels[tab] || tab }}</span>
          </button>
        </div>
      </div>
    </div>
  </nav>
</template>

<script setup>
import { reactive, ref } from 'vue';
import {
  ChevronDown as ChevronDownIcon,
  ChevronUp as ChevronUpIcon,
  PanelLeftClose as PanelLeftCloseIcon,
  PanelLeftOpen as PanelLeftOpenIcon,
  Calendar as CalendarIcon,
  FolderInput as FolderInputIcon,
  Building2 as Building2Icon,
  Users as UsersIcon,
  User as UserIcon,
  Presentation as PresentationIcon,
  BookOpen as BookOpenIcon,
  Clock as ClockIcon,
  CalendarClock as CalendarClockIcon,
  ClipboardCheck as ClipboardCheckIcon,
  ScrollText as ScrollTextIcon,
  MessageSquare as MessageSquareIcon,
  KeyRound as KeyRoundIcon,
} from '@lucide/vue';

const tabIcons = {
  veranstaltungen: CalendarIcon,
  veranstaltungImport: FolderInputIcon,
  gebaeude: Building2Icon,
  organisatoren: UsersIcon,
  teilnehmer: UserIcon,
  referenten: PresentationIcon,
  vortraege: BookOpenIcon,
  slots: ClockIcon,
  planung: CalendarClockIcon,
  ergebnisse: ClipboardCheckIcon,
  protokoll: ScrollTextIcon,
  feedback: MessageSquareIcon,
  onboarding: KeyRoundIcon,
};

const props = defineProps({
  activeTab: { type: String, required: true },
  visibleTabs: { type: Array, required: true },
  tabLabels: { type: Object, required: true },
});
const emit = defineEmits(['tab-click']);

// Gruppierung der Organisator-Tabs - rein navigatorisch, unabhaengig von visibleTabs (welche Tabs
// ueberhaupt zur Auswahl stehen haengt weiterhin von OrganisatorDashboard.vue's Veranstaltungs-Logik ab).
const groups = [
  { name: 'Stammdaten', tabs: ['veranstaltungen', 'veranstaltungImport', 'gebaeude', 'organisatoren', 'teilnehmer', 'referenten', 'vortraege'] },
  { name: 'Planung', tabs: ['slots', 'planung', 'ergebnisse'] },
  { name: 'Administration', tabs: ['onboarding', 'protokoll', 'feedback'] },
];

const collapsed = ref(false);
const openGroups = reactive({ Stammdaten: true, Planung: true, Administration: true });

const toggleGroup = (name) => { openGroups[name] = !openGroups[name]; };
const tabsOf = (group) => group.tabs.filter(t => props.visibleTabs.includes(t));
</script>
