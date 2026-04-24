<template>
  <div class="max-w-7xl mx-auto space-y-6 pb-20 text-sm">

    <!-- Page Header & Veranstaltungsauswahl -->
    <div
        class="flex flex-col md:flex-row md:items-center justify-between gap-4 bg-white p-4 rounded-xl shadow-sm border border-gray-100">
      <div class="flex-1">
        <h1 class="text-xl font-bold text-gray-900">Admin-Bereich</h1>
        <div class="mt-2 flex items-center gap-3">
          <label class="text-[10px] font-bold text-gray-500 uppercase tracking-wider">Aktive Veranstaltung:</label>
          <select v-model="selectedVid" @change="handleVeranstaltungChange"
                  class="input-field max-w-md border-indigo-200 focus:ring-indigo-500 py-1 text-xs">
            <option :value="null">-- Bitte wählen / Keine Auswahl --</option>
            <option v-for="v in veranstaltungen" :key="v.id" :value="v.id">
              {{ v.name }} ({{ formatDate(v.beginntAm) }})
            </option>
          </select>
        </div>
      </div>
      <div v-if="selectedVid" class="flex gap-2">
        <button @click="downloadTuerschilder"
                class="flex items-center gap-2 bg-indigo-600 text-white px-3 py-1.5 rounded-lg hover:bg-indigo-700 transition shadow-md text-xs font-bold">
          <FileTextIcon class="w-3.5 h-3.5"/>
          Türschilder
        </button>
        <button @click="downloadExport"
                class="flex items-center gap-2 bg-gray-800 text-white px-3 py-1.5 rounded-lg hover:bg-gray-700 transition shadow-md text-xs font-bold">
          <DownloadIcon class="w-3.5 h-3.5"/>
          CSV Export
        </button>
      </div>
    </div>

    <!-- Tab-Navigation -->
    <div class="border-b border-gray-200">
      <nav class="-mb-px flex space-x-6 overflow-x-auto">
        <button v-for="tab in visibleTabs" :key="tab"
                @click="activeTab = tab"
                :class="[activeTab === tab ? 'border-indigo-500 text-indigo-600' : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300', 'whitespace-nowrap py-3 px-1 border-b-2 font-medium text-xs capitalize']">
          {{ tabLabels[tab] || tab }}
        </button>
      </nav>
    </div>

    <!-- START-ZUSTAND (Empty State) -->
    <div v-if="!selectedVid && !['veranstaltungen', 'gebaeude', 'administratoren'].includes(activeTab)"
         class="bg-indigo-50 p-8 rounded-2xl text-center border-2 border-dashed border-indigo-200 animate-fade-in">
      <div class="text-indigo-400 mb-3 flex justify-center">
        <CalendarIcon class="w-10 h-10"/>
      </div>
      <h2 class="text-lg font-bold text-indigo-900">Keine Veranstaltung ausgewählt</h2>
      <p class="text-xs text-indigo-600 mt-1 mb-5">Bitte wählen Sie oben eine Veranstaltung aus oder legen Sie eine neue an.</p>
      <div class="flex justify-center gap-3">
        <button @click="activeTab = 'veranstaltungen'"
                class="bg-white text-indigo-700 px-4 py-2 rounded-xl font-bold border border-indigo-200 shadow-sm text-xs">Zu
          den Veranstaltungen
        </button>
        <button @click="openVeranstaltungEditor(null)" class="btn-primary flex items-center gap-2 text-xs">
          <PlusCircleIcon class="w-4 h-4"/>
          Neu anlegen
        </button>
      </div>
    </div>

    <!-- TABS CONTENT -->

    <!-- TAB: ERGEBNISSE -->
    <section v-if="activeTab === 'ergebnisse' && selectedVid" class="space-y-4 animate-fade-in">
      <div class="grid grid-cols-1 md:grid-cols-4 gap-4">
        <div class="bg-white p-3 rounded-xl shadow-sm border border-gray-100">
          <div class="text-[9px] text-gray-500 uppercase font-bold">Ø Priorität</div>
          <div class="text-xl font-black text-indigo-600">{{ qualitaet.durchschnittsPrio?.toFixed(2) || '0.00' }}</div>
        </div>
      </div>
      <!-- Belegungsplan Tabelle -->
      <div class="bg-white shadow rounded-xl overflow-hidden border border-gray-100">
        <table class="min-w-full divide-y divide-gray-200">
          <thead class="bg-gray-50 text-[9px] uppercase font-bold text-gray-500">
          <tr>
            <th class="px-4 py-1.5 text-left font-bold">Vortrag</th>
            <th class="px-4 py-1.5 text-left font-bold">Zeit/Raum</th>
            <th class="px-4 py-1.5 text-center font-bold">Belegung</th>
            <th class="px-4 py-1.5 text-left font-bold">Teilnehmer</th>
          </tr>
          </thead>
          <tbody class="divide-y divide-gray-100 text-xs">
          <tr v-for="b in belegungsPlan" :key="b.vortragTitel + b.slotZeit" class="hover:bg-gray-50 transition">
            <td class="px-4 py-2 font-bold">{{ b.vortragTitel }}</td>
            <td class="px-4 py-2">{{ b.slotZeit }} | {{ b.raumName }}</td>
            <td class="px-4 py-2 text-center">{{ b.teilnehmerNamen.length }} / {{ b.kapazitaet }}</td>
            <td class="px-4 py-2 text-[10px] text-gray-500">{{ b.teilnehmerNamen.join(', ') }}</td>
          </tr>
          </tbody>
        </table>
      </div>
    </section>

    <!-- TAB: VERANSTALTUNGEN -->
    <section v-if="activeTab === 'veranstaltungen'" class="space-y-4">
      <div class="flex justify-between items-center bg-white p-3 rounded-xl border border-gray-100 shadow-sm">
        <h2 class="text-lg font-bold text-gray-800">Veranstaltungen</h2>
        <div class="flex gap-2">
          <input v-model="filters.veranstaltungen" placeholder="Suchen..." class="input-field text-xs py-1 px-2"/>
          <button @click="triggerUpload('/api/veranstaltungen/import')"
                  :disabled="!canImportVeranstaltung"
                  :class="{'opacity-50 cursor-not-allowed': !canImportVeranstaltung}"
                  class="btn-secondary flex items-center gap-2 text-xs py-1 px-3">
            <UploadIcon class="w-3.5 h-3.5"/>
            Import
          </button>
          <button @click="openVeranstaltungEditor(null)" class="btn-primary text-xs py-1 px-3">+ Neu</button>
        </div>
      </div>
      <div class="bg-white shadow rounded-xl overflow-hidden">
        <table class="min-w-full divide-y divide-gray-200">
          <thead class="bg-gray-50 text-[9px] uppercase font-bold text-gray-500">
          <tr>
            <th @click="toggleSort('veranstaltungen', 'name')" class="px-4 py-1.5 text-left cursor-pointer hover:text-indigo-600 transition font-bold">Name <ArrowUpDownIcon class="w-3 h-3 inline ml-0.5"/></th>
            <th class="px-4 py-1.5 text-left font-bold">Datum</th>
            <th class="px-4 py-1.5 text-right font-bold">Aktionen</th>
          </tr>
          </thead>
          <tbody class="text-xs">
          <template v-for="v in paginatedVeranstaltungen" :key="v.id">
            <tr :class="selectedVid === v.id ? 'bg-indigo-50 border-l-4 border-l-indigo-500' : ''">
              <td class="px-4 py-2 font-bold">{{ v.name }}</td>
              <td class="px-4 py-2">{{ formatDate(v.beginntAm) }}</td>
              <td class="px-4 py-2 text-right space-x-2">
                <button @click="selectedVid = v.id; handleVeranstaltungChange()" class="text-indigo-600 font-bold hover:underline">Wählen</button>
                <button @click="openVeranstaltungEditor(v)" class="text-gray-600" title="Bearbeiten">
                  <PencilIcon class="w-3.5 h-3.5 inline"/>
                </button>
                <button @click="deleteVeranstaltung(v.id)" class="text-red-600">
                  <Trash2Icon class="w-3.5 h-3.5 inline"/>
                </button>
              </td>
            </tr>
            <tr v-if="selectedVid === v.id" class="bg-gray-50/50">
              <td colspan="3" class="px-4 py-4 space-y-6">
                <div class="flex flex-col gap-6">
                  <!-- Vorträge & Referenten -->
                  <div class="space-y-2">
                    <button @click="expandedSections.vortraege = !expandedSections.vortraege"
                            class="w-full flex items-center justify-between text-[10px] font-black text-indigo-700 uppercase tracking-widest border-b border-indigo-100 pb-1 hover:bg-indigo-50 transition-colors">
                      <div class="flex items-center gap-2">
                        <FileTextIcon class="w-3 h-3"/> Vorträge & Referenten
                      </div>
                      <ChevronDownIcon v-if="!expandedSections.vortraege" class="w-3 h-3"/>
                      <ChevronUpIcon v-else class="w-3 h-3"/>
                    </button>
                    <div v-if="expandedSections.vortraege" class="bg-white rounded-lg border border-gray-200 overflow-hidden shadow-sm animate-fade-in">
                      <table class="min-w-full divide-y divide-gray-200 text-[10px]">
                        <thead class="bg-gray-50 text-[8px] uppercase font-bold text-gray-500">
                          <tr><th class="px-3 py-1.5 text-left">Titel</th><th class="px-3 py-1.5 text-left">Referent</th></tr>
                        </thead>
                        <tbody class="divide-y divide-gray-100">
                          <tr v-for="talk in vortraege" :key="talk.id" class="hover:bg-indigo-50/30 transition">
                            <td class="px-3 py-1.5 font-semibold text-gray-800">{{ talk.titel }}</td>
                            <td class="px-3 py-1.5 text-gray-600" :title="talk.referent?.email">{{ talk.referent?.lastName }}, {{ talk.referent?.firstName }}</td>
                          </tr>
                        </tbody>
                      </table>
                    </div>
                  </div>

                  <!-- Teilnehmer & Prioritäten -->
                  <div class="space-y-2">
                    <button @click="expandedSections.teilnehmer = !expandedSections.teilnehmer"
                            class="w-full flex items-center justify-between text-[10px] font-black text-indigo-700 uppercase tracking-widest border-b border-indigo-100 pb-1 hover:bg-indigo-50 transition-colors">
                      <div class="flex items-center gap-2">
                        <UsersIcon class="w-3 h-3"/> Teilnehmer & Prioritäten
                      </div>
                      <ChevronDownIcon v-if="!expandedSections.teilnehmer" class="w-3 h-3"/>
                      <ChevronUpIcon v-else class="w-3 h-3"/>
                    </button>
                    <div v-if="expandedSections.teilnehmer" class="bg-white rounded-lg border border-gray-200 overflow-hidden shadow-sm animate-fade-in">
                      <table class="min-w-full divide-y divide-gray-200 text-[10px]">
                        <thead class="bg-gray-50 text-[8px] uppercase font-bold text-gray-500">
                          <tr><th class="px-3 py-1.5 text-left">Name</th><th class="px-3 py-1.5 text-left">Prios</th></tr>
                        </thead>
                        <tbody class="divide-y divide-gray-100">
                          <tr v-for="part in teilnehmer" :key="part.id" class="hover:bg-indigo-50/30 transition">
                            <td class="px-3 py-1.5 font-semibold text-gray-800" :title="part.email">{{ part.lastName }}, {{ part.firstName }}</td>
                            <td class="px-3 py-1.5 text-gray-500">{{ part.prioritaeten?.map(p => `${p.vortragId}:${p.prioWert}`).join(', ') || '-' }}</td>
                          </tr>
                        </tbody>
                      </table>
                    </div>
                  </div>
                </div>
              </td>
            </tr>
          </template>
          </tbody>
        </table>
        <PaginationControls v-model:currentPage="pages.veranstaltungen" :totalItems="filteredVeranstaltungen.length" :pageSize="pageSize"/>
      </div>
    </section>

    <!-- TAB: VERFÜGBARKEITEN -->
    <section v-if="activeTab === 'verfuegbarkeiten' && selectedVid" class="space-y-6 animate-fade-in">
       <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <div class="bg-white p-4 rounded-xl shadow-sm border border-gray-100 space-y-4">
             <h3 class="font-bold text-gray-800 flex items-center gap-2"><UserIcon class="w-4 h-4"/> Referenten-Verfügbarkeit</h3>
             <div class="overflow-x-auto border border-gray-200 rounded-lg">
                <table class="min-w-full divide-y divide-gray-200 text-[10px]">
                   <thead class="bg-gray-50">
                      <tr>
                         <th class="px-3 py-2 text-left font-bold text-gray-500">Referent</th>
                         <th v-for="slot in sortedSlots" :key="slot.id" class="px-2 py-2 text-center text-[8px] font-bold text-gray-500">
                            {{ formatTime(slot.startTime) }}
                         </th>
                      </tr>
                   </thead>
                   <tbody class="divide-y divide-gray-100">
                      <tr v-for="s in referenten" :key="s.id">
                         <td class="px-3 py-2 font-medium" :title="s.email">{{ s.lastName }}</td>
                         <td v-for="slot in sortedSlots" :key="slot.id" class="px-2 py-2 text-center">
                            <input type="checkbox" :checked="isAvailable(s.id, slot.id)" @change="toggleAvailability(s.id, slot.id, $event.target.checked)" class="rounded text-indigo-600 focus:ring-indigo-500 h-3 w-3" />
                         </td>
                      </tr>
                   </tbody>
                </table>
             </div>
          </div>

          <div class="bg-white p-4 rounded-xl shadow-sm border border-gray-100 space-y-4">
             <h3 class="font-bold text-gray-800 flex items-center gap-2"><UsersIcon class="w-4 h-4"/> Teilnehmer-Verfügbarkeit</h3>
             <div class="overflow-x-auto border border-gray-200 rounded-lg">
                <table class="min-w-full divide-y divide-gray-200 text-[10px]">
                   <thead class="bg-gray-50">
                      <tr>
                         <th class="px-3 py-2 text-left font-bold text-gray-500">Teilnehmer</th>
                         <th v-for="slot in sortedSlots" :key="slot.id" class="px-2 py-2 text-center text-[8px] font-bold text-gray-500">
                            {{ formatTime(slot.startTime) }}
                         </th>
                      </tr>
                   </thead>
                   <tbody class="divide-y divide-gray-100">
                      <tr v-for="p in teilnehmer" :key="p.id">
                         <td class="px-3 py-2 font-medium" :title="p.email">{{ p.lastName }}</td>
                         <td v-for="slot in sortedSlots" :key="slot.id" class="px-2 py-2 text-center">
                            <input type="checkbox" :checked="isAvailable(p.id, slot.id)" @change="toggleAvailability(p.id, slot.id, $event.target.checked)" class="rounded text-indigo-600 focus:ring-indigo-500 h-3 w-3" />
                         </td>
                      </tr>
                   </tbody>
                </table>
             </div>
          </div>
       </div>
    </section>

    <!-- TAB: GEBAEUDE -->
    <section v-if="activeTab === 'gebaeude'" class="space-y-4">
      <div class="flex justify-between items-center bg-white p-3 rounded-xl border border-gray-100 shadow-sm">
        <h2 class="text-lg font-bold text-gray-800">Gebäude</h2>
        <div class="flex gap-2">
          <input v-model="filters.gebaeude" placeholder="Suchen..." class="input-field text-xs py-1 px-2"/>
          <button @click="triggerUpload('/api/gebaeude/import')" class="btn-secondary flex items-center gap-2 text-xs py-1 px-3">
            <UploadIcon class="w-3.5 h-3.5"/>
            Import
          </button>
          <button @click="openGebaeudeEditor(null)" class="btn-primary text-xs py-1 px-3">+ Neu</button>
        </div>
      </div>
      <div class="bg-white shadow rounded-xl overflow-hidden">
        <table class="min-w-full divide-y divide-gray-200">
          <thead class="bg-gray-50 text-[9px] uppercase font-bold text-gray-500">
          <tr>
            <th @click="toggleSort('gebaeude', 'name')" class="px-4 py-1.5 text-left cursor-pointer hover:text-indigo-600 transition font-bold">Name <ArrowUpDownIcon class="w-3 h-3 inline ml-0.5"/></th>
            <th class="px-4 py-1.5 text-left font-bold">Adresse</th>
            <th class="px-4 py-1.5 text-left font-bold">Typ</th>
            <th class="px-4 py-1.5 text-right font-bold">Aktionen</th>
          </tr>
          </thead>
          <tbody class="text-xs">
          <template v-for="g in paginatedGebaeude" :key="g.id">
            <tr class="bg-white hover:bg-gray-50 transition border-t border-gray-100">
              <td class="px-4 py-2 font-bold">{{ g.name }}</td>
              <td class="px-4 py-2 text-gray-600">{{ g.strasse }} {{ g.hausnummer }}, {{ g.ort }}</td>
              <td class="px-4 py-2">{{ g.typ }}</td>
              <td class="px-4 py-2 text-right space-x-2">
                <button @click="openGebaeudeEditor(g)" class="text-indigo-600" title="Bearbeiten">
                  <PencilIcon class="w-3.5 h-3.5 inline"/>
                </button>
                <button @click="deleteGebaeude(g.id)" class="text-red-600 ml-2">
                  <Trash2Icon class="w-3.5 h-3.5 inline"/>
                </button>
              </td>
            </tr>
            <tr v-if="g.raeume && g.raeume.length > 0" class="bg-gray-50">
              <td colspan="4" class="px-4 py-2">
                <div class="flex items-center justify-between text-[8px] font-bold text-gray-500 uppercase mb-1">
                  Räume in {{ g.name }}
                  <button @click="openRaumEditor(null, g.id)" class="btn-primary-xs text-[9px] px-2 py-0.5">+ Raum</button>
                </div>
                <div class="border border-gray-200 rounded-lg overflow-hidden bg-white">
                  <table class="min-w-full divide-y divide-gray-200">
                    <thead class="bg-gray-100 text-[8px] uppercase font-bold text-gray-500">
                    <tr>
                      <th @click="toggleSort('raeume', 'name')" class="px-3 py-1 text-left cursor-pointer hover:text-indigo-600 transition">Raum</th>
                      <th @click="toggleSort('raeume', 'kapazitaet')" class="px-3 py-1 text-left cursor-pointer hover:text-indigo-600 transition">Kapazität</th>
                      <th @click="toggleSort('raeume', 'etage')" class="px-3 py-1 text-left cursor-pointer hover:text-indigo-600 transition">Etage</th>
                      <th class="px-3 py-1 text-right">Aktionen</th>
                    </tr>
                    </thead>
                    <tbody class="divide-y divide-gray-50 text-[10px]">
                    <tr v-for="r in sortRaeume(g.raeume)" :key="r.id" class="hover:bg-gray-50 transition">
                      <td class="px-3 py-1 font-medium text-gray-900">{{ r.name }}</td>
                      <td class="px-3 py-1 text-gray-600">{{ r.kapazitaet }}</td>
                      <td class="px-3 py-1 text-gray-600">{{ r.etage || '-' }}</td>
                      <td class="px-3 py-1 text-right space-x-2">
                        <button @click="openRaumEditor(r, g.id)" class="text-indigo-600" title="Bearbeiten">
                          <PencilIcon class="w-3.5 h-3.5 inline"/>
                        </button>
                        <button @click="deleteRaum(r)" class="text-red-600">
                          <Trash2Icon class="w-3.5 h-3.5 inline"/>
                        </button>
                      </td>
                    </tr>
                    </tbody>
                  </table>
                </div>
              </td>
            </tr>
          </template>
          </tbody>
        </table>
        <PaginationControls v-model:currentPage="pages.gebaeude" :totalItems="filteredGebaeude.length" :pageSize="pageSize"/>
      </div>
    </section>

    <!-- TAB: ADMINISTRATOREN -->
    <section v-if="activeTab === 'administratoren'" class="space-y-4 animate-fade-in">
      <div class="flex justify-between items-center bg-white p-3 rounded-xl border border-gray-100 shadow-sm">
        <h2 class="text-lg font-bold text-gray-800">Organisatoren</h2>
        <div class="flex gap-2">
          <input v-model="filters.admins" placeholder="Suchen..." class="input-field text-xs py-1 px-2"/>
          <button @click="triggerUpload('/api/admin/admins/import')" class="btn-secondary flex items-center gap-2 text-xs py-1 px-3">
            <UploadIcon class="w-3.5 h-3.5"/>
            Import
          </button>
          <button @click="openUserModal({role: 'ADMIN'})" class="btn-primary text-xs py-1 px-3">+ Neu</button>
        </div>
      </div>
      <div class="bg-white shadow rounded-xl overflow-hidden border border-gray-100">
        <table class="min-w-full divide-y divide-gray-200">
          <thead class="bg-gray-50 text-[9px] uppercase font-bold text-gray-500">
          <tr>
            <th @click="toggleSort('admins', 'lastName')" class="px-4 py-1.5 text-left cursor-pointer hover:text-indigo-600 transition font-bold">Name <ArrowUpDownIcon class="w-3 h-3 inline ml-0.5"/></th>
            <th class="px-4 py-1.5 text-right font-bold">Aktionen</th>
          </tr>
          </thead>
          <tbody class="text-xs">
          <tr v-for="a in paginatedAdmins" :key="a.id" class="hover:bg-gray-50 transition">
            <td class="px-4 py-2 font-bold" :title="a.email">{{ a.lastName }}, {{ a.firstName }}</td>
            <td class="px-4 py-2 text-right space-x-3">
              <button @click="openUserModal(a)" class="text-indigo-600" title="Bearbeiten">
                <PencilIcon class="w-3.5 h-3.5 inline"/>
              </button>
              <button @click="deleteUser(a.id)" class="text-red-600">
                <Trash2Icon class="w-3.5 h-3.5 inline"/>
              </button>
            </td>
          </tr>
          </tbody>
        </table>
        <PaginationControls v-model:currentPage="pages.admins" :totalItems="filteredAdmins.length" :pageSize="pageSize"/>
      </div>
    </section>

    <!-- KONTEXT-TABS -->
    <template v-if="selectedVid">
      <!-- TEILNEHMER -->
      <section v-if="activeTab === 'teilnehmer'" class="space-y-4 animate-fade-in">
        <div class="flex justify-between items-center bg-white p-3 rounded-xl border border-gray-100 shadow-sm">
          <h2 class="text-lg font-bold text-gray-800">Teilnehmer</h2>
          <div class="flex gap-2">
            <input v-model="filters.teilnehmer" placeholder="Suchen..." class="input-field text-xs py-1 px-2"/>
            <button @click="triggerUpload(`/api/veranstaltungen/${selectedVid}/teilnehmer/import`)"
                    class="btn-secondary text-xs py-1 px-3">Import
            </button>
            <button @click="openUserModal({role: 'TEILNEHMER'})" class="btn-primary text-xs py-1 px-3">+ Neu</button>
          </div>
        </div>
        <div class="bg-white shadow rounded-xl overflow-hidden border border-gray-100">
          <table class="min-w-full divide-y divide-gray-200 text-xs">
            <thead class="bg-gray-50 text-[9px] uppercase font-bold text-gray-500">
            <tr>
              <th @click="toggleSort('teilnehmer', 'lastName')" class="px-4 py-1.5 text-left cursor-pointer hover:text-indigo-600 transition font-bold">Name <ArrowUpDownIcon class="w-3 h-3 inline ml-0.5"/></th>
              <th @click="toggleSort('teilnehmer', 'gruppe')" class="px-4 py-1.5 text-left cursor-pointer hover:text-indigo-600 transition font-bold">Gruppe <ArrowUpDownIcon class="w-3 h-3 inline ml-0.5"/></th>
              <th class="px-4 py-1.5 text-right font-bold">Aktionen</th>
            </tr>
            </thead>
            <tbody class="divide-y divide-gray-100">
            <tr v-for="u in paginatedParticipants" :key="u.id" class="hover:bg-gray-50">
              <td class="px-4 py-2 font-bold" :title="u.email">{{ u.lastName }}, {{ u.firstName }}</td>
              <td class="px-4 py-2 text-gray-500">{{ u.gruppe }}</td>
              <td class="px-4 py-2 text-right">
                <button @click="openInviteModal(u)" class="text-indigo-600 ml-3" title="Einladen">
                  <MailIcon class="w-3.5 h-3.5 inline"/>
                </button>
                <button @click="openUserModal(u)" class="text-indigo-600 ml-3" title="Bearbeiten">
                  <PencilIcon class="w-3.5 h-3.5 inline"/>
                </button>
                <button @click="deleteUser(u.id)" class="text-red-600 ml-3">
                  <Trash2Icon class="w-3.5 h-3.5 inline"/>
                </button>
              </td>
            </tr>
            </tbody>
          </table>
          <PaginationControls v-model:currentPage="pages.teilnehmer" :totalItems="filteredParticipants.length" :pageSize="pageSize"/>
        </div>
      </section>

      <!-- REFERENTEN -->
      <section v-if="activeTab === 'referenten'" class="space-y-4 animate-fade-in">
        <div class="flex justify-between items-center bg-white p-3 rounded-xl border border-gray-100 shadow-sm">
          <h2 class="text-lg font-bold text-gray-800">Referenten</h2>
          <div class="flex gap-2">
            <input v-model="filters.referenten" placeholder="Suchen..." class="input-field text-xs py-1 px-2"/>
            <button @click="triggerUpload(`/api/veranstaltungen/${selectedVid}/referenten/import`)"
                    class="btn-secondary text-xs py-1 px-3">Import
            </button>
            <button @click="openUserModal({role: 'REFERENT'})" class="btn-primary text-xs py-1 px-3">+ Neu</button>
          </div>
        </div>
        <div class="bg-white shadow rounded-xl overflow-hidden border border-gray-100">
          <table class="min-w-full divide-y divide-gray-200 text-xs">
            <thead class="bg-gray-50 text-[9px] uppercase font-bold text-gray-500">
            <tr>
              <th @click="toggleSort('referenten', 'lastName')" class="px-4 py-1.5 text-left cursor-pointer hover:text-indigo-600 transition font-bold">Name <ArrowUpDownIcon class="w-3 h-3 inline ml-0.5"/></th>
              <th class="px-4 py-1.5 text-right font-bold">Aktionen</th>
            </tr>
            </thead>
            <tbody class="divide-y divide-gray-100">
            <tr v-for="u in paginatedSpeakers" :key="u.id" class="hover:bg-gray-50">
              <td class="px-4 py-2 font-bold" :title="u.email">{{ u.lastName }}, {{ u.firstName }}</td>
              <td class="px-4 py-2 text-right">
                <button @click="openInviteModal(u)" class="text-indigo-600 ml-3" title="Einladen">
                  <MailIcon class="w-3.5 h-3.5 inline"/>
                </button>
                <button @click="openUserModal(u)" class="text-indigo-600 ml-3" title="Bearbeiten">
                  <PencilIcon class="w-3.5 h-3.5 inline"/>
                </button>
                <button @click="deleteUser(u.id)" class="text-red-600 ml-3">
                  <Trash2Icon class="w-3.5 h-3.5 inline"/>
                </button>
              </td>
            </tr>
            </tbody>
          </table>
          <PaginationControls v-model:currentPage="pages.referenten" :totalItems="filteredSpeakers.length" :pageSize="pageSize"/>
        </div>
      </section>

      <!-- VORTRÄGE -->
      <section v-if="activeTab === 'vortraege'" class="space-y-4 animate-fade-in">
        <div class="flex justify-between items-center bg-white p-3 rounded-xl border border-gray-100 shadow-sm">
          <h2 class="text-lg font-bold text-gray-800">Vorträge</h2>
          <div class="flex gap-2">
            <input v-model="filters.vortraege" placeholder="Suchen..." class="input-field text-xs py-1 px-2"/>
            <button @click="triggerUpload('/api/veranstaltungen/{vid}/vortraege/import')"
                    :disabled="!canImportVortraege"
                    class="btn-secondary flex items-center gap-2 text-xs py-1 px-3">
              <UploadIcon class="w-3.5 h-3.5"/>
              Import
            </button>
            <button @click="openVortragEditor(null)" class="btn-primary text-xs py-1 px-3">+ Neu</button>
          </div>
        </div>
        <div class="bg-white shadow rounded-xl overflow-hidden border border-gray-100">
          <table class="min-w-full divide-y divide-gray-200 text-xs">
            <thead class="bg-gray-50 text-[9px] uppercase font-bold text-gray-500">
            <tr>
              <th @click="toggleSort('vortraege', 'titel')" class="px-4 py-1.5 text-left cursor-pointer hover:text-indigo-600 transition font-bold">Titel <ArrowUpDownIcon class="w-3 h-3 inline ml-0.5"/></th>
              <th @click="toggleSort('vortraege', 'referentName')" class="px-4 py-1.5 text-left cursor-pointer hover:text-indigo-600 transition font-bold">Referent <ArrowUpDownIcon class="w-3 h-3 inline ml-0.5"/></th>
              <th class="px-4 py-1.5 text-right font-bold">Aktionen</th>
            </tr>
            </thead>
            <tbody class="divide-y divide-gray-100">
            <tr v-for="v in paginatedVortraege" :key="v.id" class="hover:bg-gray-50">
              <td class="px-4 py-2 font-bold">
                {{ v.titel }}
                <span v-if="v.istPflicht" class="text-[9px] text-red-600 ml-1">PFLICHT</span>
              </td>
              <td class="px-4 py-2">{{ v.referentName }}</td>
              <td class="px-4 py-2 text-right">
                <button @click="openVortragEditor(v)" class="text-indigo-600" title="Bearbeiten">
                  <PencilIcon class="w-3.5 h-3.5 inline"/>
                </button>
                <button @click="deleteVortrag(v.id)" class="text-red-600 ml-3">
                  <Trash2Icon class="w-3.5 h-3.5 inline"/>
                </button>
              </td>
            </tr>
            </tbody>
          </table>
          <PaginationControls v-model:currentPage="pages.vortraege" :totalItems="filteredVortraege.length" :pageSize="pageSize"/>
        </div>
      </section>

      <!-- SLOTS -->
      <section v-if="activeTab === 'slots'" class="space-y-4 animate-fade-in">
        <div class="flex justify-between items-center bg-white p-3 rounded-xl border border-gray-100 shadow-sm">
          <h2 class="text-lg font-bold text-gray-800">Zeit-Slots</h2>
          <div class="flex gap-2">
            <button @click="triggerUpload(`/api/veranstaltungen/${selectedVid}/slots/import`)" class="btn-secondary text-xs py-1 px-3">Import</button>
            <button @click="openSlotEditor(null)" class="btn-primary text-xs py-1 px-3">+ Neu</button>
          </div>
        </div>
        <div class="bg-white shadow rounded-xl overflow-hidden border border-gray-100">
          <table class="min-w-full divide-y divide-gray-200 text-xs">
            <thead class="bg-gray-50 text-[9px] uppercase font-bold text-gray-500">
            <tr>
              <th @click="toggleSort('slots', 'startTime')" class="px-4 py-1.5 text-left cursor-pointer hover:text-indigo-600 transition font-bold">Zeit <ArrowUpDownIcon class="w-3 h-3 inline ml-0.5"/></th>
              <th class="px-4 py-1.5 text-left font-bold">Beschreibung</th>
              <th class="px-4 py-1.5 text-right font-bold">Aktionen</th>
            </tr>
            </thead>
            <tbody class="divide-y divide-gray-100">
            <tr v-for="s in sortedSlots" :key="s.id" class="hover:bg-gray-50">
              <td class="px-4 py-2 font-bold">{{ formatDateTime(s.startTime) }} - {{ formatTime(s.endTime) }}</td>
              <td class="px-4 py-2">{{ s.description }}</td>
              <td class="px-4 py-2 text-right space-x-3">
                <button @click="openSlotEditor(s)" class="text-indigo-600" title="Bearbeiten">
                  <PencilIcon class="w-3.5 h-3.5 inline"/>
                </button>
                <button @click="deleteSlot(s.id)" class="text-red-600">
                  <Trash2Icon class="w-3.5 h-3.5 inline"/>
                </button>
              </td>
            </tr>
            </tbody>
          </table>
        </div>
      </section>

      <!-- RÄUME -->
      <section v-if="activeTab === 'raeume'" class="space-y-4 animate-fade-in">
        <div class="flex justify-between items-center bg-white p-3 rounded-xl border border-gray-100 shadow-sm">
          <h2 class="text-lg font-bold text-gray-800">Räume</h2>
          <button @click="openRaumEditor(null)" class="btn-primary text-xs py-1 px-3">+ Neu</button>
        </div>
        <div class="bg-white shadow rounded-xl overflow-hidden border border-gray-100">
          <table class="min-w-full divide-y divide-gray-200 text-xs">
            <thead class="bg-gray-50 text-[9px] uppercase font-bold text-gray-500">
            <tr>
              <th @click="toggleSort('raeume', 'name')" class="px-4 py-1.5 text-left cursor-pointer hover:text-indigo-600 transition font-bold">Name <ArrowUpDownIcon class="w-3 h-3 inline ml-0.5"/></th>
              <th @click="toggleSort('raeume', 'kapazitaet')" class="px-4 py-1.5 text-center cursor-pointer hover:text-indigo-600 transition font-bold">Kapazität <ArrowUpDownIcon class="w-3 h-3 inline ml-0.5"/></th>
              <th @click="toggleSort('raeume', 'etage')" class="px-4 py-1.5 text-left cursor-pointer hover:text-indigo-600 transition font-bold">Etage <ArrowUpDownIcon class="w-3 h-3 inline ml-0.5"/></th>
              <th class="px-4 py-1.5 text-right font-bold">Aktionen</th>
            </tr>
            </thead>
            <tbody class="divide-y divide-gray-100">
            <tr v-for="r in sortRaeume(raeume)" :key="r.id" class="hover:bg-gray-50">
              <td class="px-4 py-2 font-bold">{{ r.name }} ({{ r.gebaeude?.name }})</td>
              <td class="px-4 py-2 text-center">{{ r.kapazitaet }}</td>
              <td class="px-4 py-2">{{ r.etage || '-' }}</td>
              <td class="px-4 py-2 text-right">
                <button @click="openRaumEditor(r)" class="text-indigo-600" title="Bearbeiten">
                  <PencilIcon class="w-3.5 h-3.5 inline"/>
                </button>
                <button @click="deleteRaum(r)" class="text-red-600 ml-3">
                  <Trash2Icon class="w-3.5 h-3.5 inline"/>
                </button>
              </td>
            </tr>
            </tbody>
          </table>
        </div>
      </section>

      <!-- PLANUNG -->
      <section v-if="activeTab === 'planung'" class="space-y-4 animate-fade-in">
        <div
            class="bg-indigo-900 text-white p-6 rounded-2xl shadow-xl flex flex-col md:flex-row items-center justify-between gap-6">
          <div class="space-y-3 flex-1">
            <h2 class="text-2xl font-black">Planung & Optimierung</h2>
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-3 bg-white/10 p-3 rounded-xl border border-white/10">
              <div>
                <label class="block text-[9px] uppercase font-bold text-indigo-300 mb-0.5">MiniZinc Solver</label>
                <select v-model="solverConfig.solver"
                        class="w-full bg-indigo-800 border-none rounded text-xs text-white focus:ring-2 focus:ring-green-400 py-1">
                  <option value="OR-tools">Google OR-Tools</option>
                  <option value="Gecode">Gecode</option>
                  <option value="COIN-BC">COIN-BC</option>
                </select>
              </div>
              <div>
                <label class="block text-[9px] uppercase font-bold text-indigo-300 mb-0.5">Timeout (Sek.)</label>
                <input v-model.number="solverConfig.timeout" type="number"
                       class="w-full bg-indigo-800 border-none rounded text-xs text-white focus:ring-2 focus:ring-green-400 py-1 px-2"/>
              </div>
            </div>
          </div>
          <button @click="startOptimization" :disabled="isOptimizing"
                  class="bg-green-500 hover:bg-green-400 disabled:bg-gray-600 text-white px-8 py-4 rounded-xl font-black text-lg shadow-2xl transition-all transform hover:scale-105 flex items-center gap-3">
            <ZapIcon v-if="!isOptimizing" class="w-5 h-5"/>
            <LoaderIcon v-else class="animate-spin w-5 h-5"/>
            {{ isOptimizing ? 'Optimierung...' : 'Optimieren' }}
          </button>
        </div>
      </section>
    </template>

    <!-- Global File Input -->
    <input type="file" ref="fileInput" class="hidden" @change="handleGlobalUpload" accept=".csv"/>

    <!-- Modals -->
    <VeranstaltungEditorModal :isVisible="showVeranstaltungModal" :veranstaltung="selectedVeranstaltung"
                              :admins="admins" :allGebaeude="gebaeude" @close="showVeranstaltungModal = false"
                              @save="handleSaveVeranstaltung"/>
    <GebaeudeEditorModal :isVisible="showGebaeudeModal" :gebaeude="selectedGebaeude" @close="showGebaeudeModal = false"
                         @save="handleSaveGebaeude"/>
    <RaumEditorModal :isVisible="showRaumModal" :raum="selectedRaum" :slots="eventSlots" :gebaeude="gebaeude"
                     @close="showRaumModal = false" @save="handleSaveRaum"/>
    <UserEditorModal :isVisible="showUserModal" :user="selectedUser" :eventSlots="eventSlots"
                     @close="showUserModal = false" @save="handleSaveUser"/>
    <AdminVortragEditorModal :isVisible="showVortragModal" :vortrag="selectedVortrag" :referenten="referenten"
                             :raeume="raeume" :slots="eventSlots" @close="showVortragModal = false"
                             @save="handleSaveVortrag"/>
    <EventSlotEditorModal :isVisible="showSlotModal" :slot="selectedSlot" @close="showSlotModal = false"
                          @save="handleSaveSlot"/>
    <InviteUserModal :isVisible="showInviteModal" :user="selectedUserForInvite" :futureEvents="futureEvents"
                     @close="showInviteModal = false" @invite="handleInviteUser"/>

    <!-- CSV Import Feedback Modal -->
    <div v-if="showCsvFeedbackModal" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4">
      <div class="w-full max-w-sm rounded-xl bg-white p-5 shadow-2xl">
        <h3 class="text-base font-bold text-gray-900 mb-3">CSV Import Ergebnis</h3>
        <p class="text-sm mb-1">Erfolgreich: <span class="font-bold text-green-600">{{ csvFeedback.successCount }}</span></p>
        <p v-if="csvFeedback.errorCount > 0" class="text-sm mb-3">Fehler: <span class="font-bold text-red-600">{{ csvFeedback.errorCount }}</span></p>
        <p v-if="csvFeedback.errorMessage" class="text-red-500 text-xs mb-3">{{ csvFeedback.errorMessage }}</p>
        <button @click="showCsvFeedbackModal = false" class="btn-primary w-full py-1.5 text-xs">Schließen</button>
      </div>
    </div>

    <!-- Global Loading Spinner -->
    <div v-if="isGlobalLoading" class="fixed inset-0 z-[100] flex items-center justify-center bg-black/30 backdrop-blur-[2px]">
      <div class="bg-white p-6 rounded-2xl shadow-2xl flex flex-col items-center gap-3 animate-fade-in">
        <div class="relative">
          <LoaderIcon class="w-10 h-10 text-indigo-600 animate-spin"/>
        </div>
        <p class="text-indigo-900 font-bold text-sm">CSV Import läuft...</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import {computed, h, onMounted, reactive, ref, watch} from 'vue';
import api from '../api/axios';
import { useEventContextStore } from '../stores/eventContext';
import {
  ArrowUpDown as ArrowUpDownIcon,
  Calendar as CalendarIcon,
  Download as DownloadIcon,
  FileText as FileTextIcon,
  Hourglass as HourglassIcon,
  Loader as LoaderIcon,
  PlusCircle as PlusCircleIcon,
  Trash2 as Trash2Icon,
  Upload as UploadIcon,
  Zap as ZapIcon,
  Pencil as PencilIcon,
  Users as UsersIcon,
  User as UserIcon,
  Mail as MailIcon,
  ChevronDown as ChevronDownIcon,
  ChevronUp as ChevronUpIcon
} from 'lucide-vue-next';
import AdminVortragEditorModal from '../components/AdminVortragEditorModal.vue';
import UserEditorModal from '../components/UserEditorModal.vue';
import VeranstaltungEditorModal from '../components/VeranstaltungEditorModal.vue';
import RaumEditorModal from '../components/RaumEditorModal.vue';
import EventSlotEditorModal from '../components/EventSlotEditorModal.vue';
import GebaeudeEditorModal from '../components/GebaeudeEditorModal.vue';
import InviteUserModal from '../components/InviteUserModal.vue';

const eventContext = useEventContextStore();

// --- Konfigurierbare Tab-Labels (Dictionary) ---
const tabLabels = {
  ergebnisse: 'Ergebnisse',
  planung: 'Optimierung',
  teilnehmer: 'Teilnehmer',
  referenten: 'Referenten',
  vortraege: 'Vorträge',
  slots: 'Zeit-Slots',
  raeume: 'Räume',
  veranstaltungen: 'Veranstaltungen',
  gebaeude: 'Gebäude',
  administratoren: 'Organisatoren',
  verfuegbarkeiten: 'Verfügbarkeiten'
};

// --- Hilfskomponente für Pagination ---
const PaginationControls = (props, {emit}) => {
  const totalPages = Math.ceil(props.totalItems / props.pageSize);
  if (totalPages <= 1) return null;
  return h('div', {class: 'flex items-center justify-between px-4 py-2 bg-gray-50 border-t border-gray-100'}, [
    h('span', {class: 'text-[10px] text-gray-500'}, `Seite ${props.currentPage} von ${totalPages}`),
    h('div', {class: 'flex gap-1.5'}, [
      h('button', {
        class: 'btn-secondary text-[10px] py-0.5 px-2',
        disabled: props.currentPage === 1,
        onClick: () => emit('update:currentPage', props.currentPage - 1)
      }, 'Zurück'),
      h('button', {
        class: 'btn-secondary text-[10px] py-0.5 px-2',
        disabled: props.currentPage === totalPages,
        onClick: () => emit('update:currentPage', props.currentPage + 1)
      }, 'Weiter')
    ])
  ]);
};

// State
const activeTab = ref('ergebnisse');
const selectedVid = ref(null);
const veranstaltungen = ref([]);
const gebaeude = ref([]);
const raeume = ref([]);
const users = ref([]);
const vortraege = ref([]);
const eventSlots = ref([]);
const belegungsPlan = ref([]);
const qualitaet = ref({});
const verfuegbarkeiten = ref([]);

const isGlobalLoading = ref(false);

const pageSize = 15;
const pages = reactive({veranstaltungen: 1, gebaeude: 1, admins: 1, teilnehmer: 1, referenten: 1, vortraege: 1});
const filters = reactive({veranstaltungen: '', gebaeude: '', admins: '', teilnehmer: '', referenten: '', vortraege: ''});
const sorts = reactive({
  veranstaltungen: {key: 'name', dir: 'asc'},
  gebaeude: {key: 'name', dir: 'asc'},
  admins: {key: 'lastName', dir: 'asc'},
  teilnehmer: {key: 'lastName', dir: 'asc'},
  referenten: {key: 'lastName', dir: 'asc'},
  vortraege: {key: 'titel', dir: 'asc'},
  slots: {key: 'startTime', dir: 'asc'},
  raeume: {key: 'name', dir: 'asc'}
});

// Steuerung für einklappbare Bereiche in der Veranstaltungs-Detailansicht
const expandedSections = reactive({
  vortraege: true,
  teilnehmer: true
});

// Watcher für Filter, um Paginierung zurückzusetzen
watch(() => filters.veranstaltungen, () => { pages.veranstaltungen = 1; });
watch(() => filters.gebaeude, () => { pages.gebaeude = 1; });
watch(() => filters.admins, () => { pages.admins = 1; });
watch(() => filters.teilnehmer, () => { pages.teilnehmer = 1; });
watch(() => filters.referenten, () => { pages.referenten = 1; });
watch(() => filters.vortraege, () => { pages.vortraege = 1; });

const showVeranstaltungModal = ref(false);
const selectedVeranstaltung = ref(null);
const showGebaeudeModal = ref(false);
const selectedGebaeude = ref(null);
const showRaumModal = ref(false);
const selectedRaum = ref(null);
const showUserModal = ref(false);
const selectedUser = ref(null);
const showVortragModal = ref(false);
const selectedVortrag = ref(null);
const showSlotModal = ref(false);
const selectedSlot = ref(null);
const showInviteModal = ref(false);
const selectedUserForInvite = ref(null);

const isOptimizing = ref(false);
const solverConfig = reactive({solver: 'OR-tools', timeout: 120});
const fileInput = ref(null);
const currentUploadEndpoint = ref('');

// CSV Feedback Modal State
const showCsvFeedbackModal = ref(false);
const csvFeedback = reactive({
  successCount: 0,
  errorCount: 0,
  errorMessage: ''
});

const visibleTabs = computed(() => {
  const base = ['veranstaltungen', 'gebaeude', 'administratoren'];
  if (selectedVid.value) return ['ergebnisse', 'planung', 'teilnehmer', 'referenten', 'vortraege', 'slots', 'raeume', 'verfuegbarkeiten', ...base];
  return base;
});

const futureEvents = computed(() => {
  const now = new Date();
  return veranstaltungen.value.filter(v => {
    const endDate = v.endetAm ? new Date(v.endetAm) : new Date(v.beginntAm);
    return endDate > now;
  });
});

// --- Generic Filter, Sort & Paginate Logic ---
const processList = (list, filterText, sortConfig) => {
  let result = [...list];
  if (filterText) {
    const f = filterText.toLowerCase();
    result = result.filter(item => {
      const searchStrings = Object.values(item).map(v => v && typeof v === 'object' ? Object.values(v) : v).flat();
      return searchStrings.some(val => val && String(val).toLowerCase().includes(f));
    });
  }
  result.sort((a, b) => {
    const valA = a[sortConfig.key] || '';
    const valB = b[sortConfig.key] || '';
    if (typeof valA === 'number' && typeof valB === 'number') {
      return sortConfig.dir === 'asc' ? valA - valB : valB - valA;
    }
    const cmp = String(valA).localeCompare(String(valB));
    return sortConfig.dir === 'asc' ? cmp : -cmp;
  });
  return result;
};

const paginate = (list, page) => {
  const start = (page - 1) * pageSize;
  return list.slice(start, start + pageSize);
};

const toggleSort = (key, field) => {
  if (sorts[key].key === field) {
    sorts[key].dir = sorts[key].dir === 'asc' ? 'desc' : 'asc';
  } else {
    sorts[key].key = field;
    sorts[key].dir = 'asc';
  }
};

const sortRaeume = (raumList) => {
  const result = [...raumList];
  const config = sorts.raeume;
  result.sort((a, b) => {
    const valA = a[config.key] || '';
    const valB = b[config.key] || '';
    if (typeof valA === 'number' && typeof valB === 'number') {
      return config.dir === 'asc' ? valA - valB : valB - valA;
    }
    const cmp = String(valA).localeCompare(String(valB));
    return config.dir === 'asc' ? cmp : -cmp;
  });
  return result;
};

const admins = computed(() => users.value.filter(u => u.role === 'ADMIN'));
const referenten = computed(() => users.value.filter(u => u.role === 'REFERENT'));
const teilnehmer = computed(() => users.value.filter(u => u.role === 'TEILNEHMER'));

const filteredVeranstaltungen = computed(() => processList(veranstaltungen.value, filters.veranstaltungen, sorts.veranstaltungen));
const paginatedVeranstaltungen = computed(() => paginate(filteredVeranstaltungen.value, pages.veranstaltungen));

const filteredGebaeude = computed(() => processList(gebaeude.value, filters.gebaeude, sorts.gebaeude));
const paginatedGebaeude = computed(() => paginate(filteredGebaeude.value, pages.gebaeude));

const filteredAdmins = computed(() => processList(admins.value, filters.admins, sorts.admins));
const paginatedAdmins = computed(() => paginate(filteredAdmins.value, pages.admins));

const filteredSpeakers = computed(() => processList(referenten.value, filters.referenten, sorts.referenten));
const paginatedSpeakers = computed(() => paginate(filteredSpeakers.value, pages.referenten));

const filteredParticipants = computed(() => processList(teilnehmer.value, filters.teilnehmer, sorts.teilnehmer));
const paginatedParticipants = computed(() => paginate(filteredParticipants.value, pages.teilnehmer));

const filteredVortraege = computed(() => {
  const list = vortraege.value.map(v => ({
    ...v,
    referentName: v.referent ? `${v.referent.lastName}, ${v.referent.firstName}` : ''
  }));
  return processList(list, filters.vortraege, sorts.vortraege);
});
const paginatedVortraege = computed(() => paginate(filteredVortraege.value, pages.vortraege));

const sortedSlots = computed(() => {
  return [...eventSlots.value].sort((a, b) => {
    const cmp = new Date(a.startTime) - new Date(b.startTime);
    return sorts.slots.dir === 'asc' ? cmp : -cmp;
  });
});

const canImportVeranstaltung = computed(() => admins.value.length > 0 && gebaeude.value.length > 0);
const canImportVortraege = computed(() => eventSlots.value.length > 0);

onMounted(async () => {
  await refreshVeranstaltungen();
  await refreshGebaeude();
  await refreshAdmins();
  if (selectedVid.value) handleVeranstaltungChange();
});

const refreshVeranstaltungen = async () => {
  try {
    const res = await api.get('/api/veranstaltungen');
    veranstaltungen.value = res.data;
  } catch (e) {
    console.error('Fehler beim Laden der Veranstaltungen:', e);
  }
};
const refreshGebaeude = async () => {
  try {
    const res = await api.get('/api/gebaeude');
    gebaeude.value = res.data;
    updateRaeumeList();
  } catch (e) {
    console.error('Fehler beim Laden der Gebäude:', e);
  }
};
const refreshAdmins = async () => {
  try {
    const res = await api.get('/api/admin/nutzer');
    users.value = res.data;
  } catch (e) {
    console.error('Fehler beim Laden der Administratoren:', e);
  }
};

const updateRaeumeList = () => {
  raeume.value = gebaeude.value.flatMap(g => g.raeume.map(r => ({...r, gebaeude: {id: g.id, name: g.name}})));
};

const handleVeranstaltungChange = () => {
  const ev = veranstaltungen.value.find(v => v.id === selectedVid.value);
  if (ev) eventContext.setEvent(ev);
  else eventContext.clearEvent();
  loadData();
};

const loadData = async () => {
  if (!selectedVid.value) return;
  const base = `/api/veranstaltungen/${selectedVid.value}`;
  try {
    const [uRes, vRes, sRes, pRes, qRes, avRes] = await Promise.all([
      api.get(`${base}/nutzer`), api.get(`${base}/vortraege`),
      api.get(`${base}/slots`), api.get(`${base}/plan/details`), api.get(`${base}/plan/qualitaet`),
      api.get(`/api/admin/veranstaltung/${selectedVid.value}/verfuegbarkeiten`)
    ]);
    const localUsers = uRes.data;
    const globalAdmins = users.value.filter(u => u.role === 'ADMIN');
    users.value = [...globalAdmins, ...localUsers];

    vortraege.value = vRes.data;
    eventSlots.value = sRes.data;
    belegungsPlan.value = pRes.data;
    qualitaet.value = qRes.data;
    verfuegbarkeiten.value = avRes.data;
  } catch (err) {
    console.error('Fehler beim Laden der Veranstaltungsdaten:', err);
  }
};

// --- CRUD ACTIONS ---
const openVeranstaltungEditor = (v) => {
  selectedVeranstaltung.value = v || {
    name: '',
    beginntAm: '',
    endetAm: '',
    gebaeude: [],
    organisatorIds: []
  };
  showVeranstaltungModal.value = true;
};
const handleSaveVeranstaltung = async (v) => {
  try {
    if (v.id) await api.put(`/api/veranstaltungen/${v.id}`, v);
    else {
      const res = await api.post('/api/veranstaltungen', v);
      if (veranstaltungen.value.length === 0) {
        selectedVid.value = res.data.id;
        handleVeranstaltungChange();
        activeTab.value = 'ergebnisse';
      }
    }
    showVeranstaltungModal.value = false;
    await refreshVeranstaltungen();
  } catch (e) {
    console.error('Fehler beim Speichern der Veranstaltung:', e);
  }
};
const deleteVeranstaltung = async (id) => {
  if (confirm("Löschen?")) {
    try {
      await api.delete(`/api/veranstaltungen/${id}`);
      if (selectedVid.value === id) {
        selectedVid.value = null;
        eventContext.clearEvent();
      }
      await refreshVeranstaltungen();
    } catch (e) {
      console.error('Fehler beim Lore-Löschen der Veranstaltung:', e);
    }
  }
};

const openGebaeudeEditor = (g) => {
  selectedGebaeude.value = g || {name: '', typ: 'SCHULE', strasse: '', hausnummer: '', postleitzahl: '', ort: ''};
  showGebaeudeModal.value = true;
};
const handleSaveGebaeude = async (g) => {
  try {
    if (g.id) await api.put(`/api/gebaeude/${g.id}`, g); else await api.post('/api/gebaeude', g);
    showGebaeudeModal.value = false;
    await refreshGebaeude();
  } catch (e) {
    console.error('Fehler beim Speichern des Gebäudes:', e);
  }
};
const deleteGebaeude = async (id) => {
  if (confirm("Löschen?")) {
    try {
      await api.delete(`/api/gebaeude/${id}`);
      await refreshGebaeude();
    } catch (e) {
      console.error('Fehler beim Löschen des Gebäudes:', e);
    }
  }
};

const openRaumEditor = (r, buildingId = null) => {
  selectedRaum.value = r || {name: '', kapazitaet: 10, gebaeude: {id: buildingId || gebaeude.value[0]?.id}};
  showRaumModal.value = true;
};
const handleSaveRaum = async (r) => {
  const url = r.id ? `/api/gebaeude/${r.gebaeude.id}/raeume/${r.id}` : `/api/gebaeude/${r.gebaeude.id}/raeume`;
  try {
    if (r.id) await api.put(url, r); else await api.post(url, r);
    showRaumModal.value = false;
    await refreshGebaeude();
  } catch (e) {
    console.error('Fehler beim Speichern des Raumes:', e);
  }
};
const deleteRaum = async (r) => {
  if (confirm("Löschen?")) {
    try {
      await api.delete(`/api/gebaeude/${r.gebaeude.id}/raeume/${r.id}`);
      await refreshGebaeude();
    } catch (e) {
      console.error('Fehler beim Löschen des Raumes:', e);
    }
  }
};

const openUserModal = (u) => {
  selectedUser.value = u?.id ? u : {
    firstName: '',
    lastName: '',
    email: '',
    role: u?.role || 'TEILNEHMER',
    isActive: true,
    gruppe: '',
    veranstaltungIds: selectedVid.value ? [selectedVid.value] : []
  };
  showUserModal.value = true;
};
const handleSaveUser = async (u) => {
  const isGlobalAdmin = u.role === 'ADMIN';
  let endpoint;
  if (isGlobalAdmin) endpoint = `/api/admin/nutzer`;
  else if (selectedVid.value) endpoint = `/api/veranstaltungen/${selectedVid.value}/nutzer`;
  else return;

  try {
    if (u.id) await api.put(`${endpoint}/${u.id}`, u);
    else await api.post(endpoint, u);
    showUserModal.value = false;
    await loadData();
    await refreshAdmins();
  } catch (e) {
    console.error('Fehler beim Speichern des Nutzers:', e);
  }
};
const deleteUser = async (id) => {
  if (confirm("Löschen?")) {
    try {
      const userToDelete = users.value.find(u => u.id === id);
      let endpoint;
      if (userToDelete && userToDelete.role === 'ADMIN') endpoint = `/api/admin/nutzer/${id}`;
      else if (selectedVid.value) endpoint = `/api/veranstaltungen/${selectedVid.value}/nutzer/${id}`;
      else return;

      await api.delete(endpoint);
      await loadData();
      await refreshAdmins();
    } catch (e) {
      console.error('Fehler beim Löschen des Nutzers:', e);
    }
  }
};

const openInviteModal = (u) => {
  selectedUserForInvite = u;
  showInviteModal.value = true;
};
const handleInviteUser = async ({ userId, eventId }) => {
  try {
    await api.post(`/api/admin/nutzer/${userId}/einladen/${eventId}`);
    alert("Einladung erfolgreich versendet!");
    showInviteModal.value = false;
    await loadData();
  } catch (e) {
    alert("Fehler beim Einladen: " + (e.response?.data || e.message));
  }
};

const openVortragEditor = (v) => {
  selectedVortrag.value = v || {titel: '', inhalt: '', zielgruppe: '', referent: {id: null}, vortrag_typ: 'WAHL'};
  showVortragModal.value = true;
};
const handleSaveVortrag = async (v) => {
  const base = `/api/veranstaltungen/${selectedVid.value}/vortraege`;
  try {
    if (v.id) await api.put(`${base}/${v.id}`, v); else await api.post(base, v);
    showVortragModal.value = false;
    await loadData();
  } catch (e) {
    console.error('Fehler beim Speichern des Vortrags:', e);
  }
};
const deleteVortrag = async (id) => {
  if (confirm("Löschen?")) {
    try {
      await api.delete(`/api/veranstaltungen/${selectedVid.value}/vortraege/${id}`);
      await loadData();
    } catch (e) {
      console.error('Fehler beim Löschen des Vortrags:', e);
    }
  }
};

const openSlotEditor = (s) => {
  selectedSlot.value = s || {description: '', startTime: '', endTime: ''};
  showSlotModal.value = true;
};
const handleSaveSlot = async (s) => {
  const base = `/api/veranstaltungen/${selectedVid.value}/slots`;
  try {
    if (s.id) await api.put(`${base}/${s.id}`, s); else await api.post(base, s);
    showSlotModal.value = false;
    await loadData();
  } catch (e) {
    console.error('Fehler beim Speichern des Slots:', e);
  }
};
const deleteSlot = async (id) => {
  if (confirm("Löschen?")) {
    try {
      await api.delete(`/api/veranstaltungen/${selectedVid.value}/slots/${id}`);
      await loadData();
    } catch (e) {
      console.error('Fehler beim Löschen des Slots:', e);
    }
  }
};

const isAvailable = (userId, slotId) => {
  return verfuegbarkeiten.value.some(v => v.userId === userId && v.slotId === slotId && v.isAvailable);
};

const toggleAvailability = async (userId, slotId, isAvailable) => {
  try {
    await api.post('/api/admin/verfuegbarkeit', { userId, slotId, isAvailable });
    // Lokalen State aktualisieren
    const idx = verfuegbarkeiten.value.findIndex(v => v.userId === userId && v.slotId === slotId);
    if (idx !== -1) {
      verfuegbarkeiten.value[idx].isAvailable = isAvailable;
    } else {
      verfuegbarkeiten.value.push({ userId, slotId, isAvailable });
    }
  } catch (e) {
    console.error('Fehler beim Aktualisieren der Verfügbarkeit:', e);
  }
};

const startOptimization = async () => {
  isOptimizing.value = true;
  try {
    await api.post(`/api/veranstaltungen/${selectedVid.value}/optimierung/start`, solverConfig);
    await loadData();
    activeTab.value = 'ergebnisse';
  } catch (e) {
    console.error('Fehler bei der Optimierung:', e);
  } finally {
    isOptimizing.value = false;
  }
};

const downloadTuerschilder = async () => {
  try {
    const res = await api.get(`/api/veranstaltungen/${selectedVid.value}/export/tuerschilder`, {responseType: 'blob'});
    const url = window.URL.createObjectURL(new Blob([res.data]));
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', 'tuerschilder.pdf');
    document.body.appendChild(link);
    link.click();
  } catch (e) {}
};
const downloadExport = async () => {
  try {
    const res = await api.get(`/api/veranstaltungen/${selectedVid.value}/export/csv`, {responseType: 'blob'});
    const url = window.URL.createObjectURL(new Blob([res.data]));
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', 'planung.csv');
    document.body.appendChild(link);
    link.click();
  } catch (e) {}
};

const triggerUpload = (endpoint) => {
  if (endpoint === '/api/veranstaltungen/import' && !canImportVeranstaltung.value) return;
  if (endpoint.includes('/vortraege/import') && !canImportVortraege.value) return;
  currentUploadEndpoint.value = endpoint;
  fileInput.value.click();
};
const handleGlobalUpload = async (event) => {
  const file = event.target.files[0];
  if (!file) return;
  const formData = new FormData();
  formData.append('file', file);

  const endpoint = currentUploadEndpoint.value;
  const finalEndpoint = endpoint.replace('{vid}', selectedVid.value);

  isGlobalLoading.value = true;
  try {
    const res = await api.post(finalEndpoint, formData, {headers: {'Content-Type': 'multipart/form-data'}});
    showCsvFeedback(res.data, false);
    await loadData();
    await refreshVeranstaltungen();
    await refreshGebaeude();
    await refreshAdmins();
  } catch (e) {
    showCsvFeedback(e.response?.data || e.message, true);
  } finally {
    isGlobalLoading.value = false;
    event.target.value = '';
  }
};

const showCsvFeedback = (message, isError) => {
  csvFeedback.errorMessage = '';
  csvFeedback.successCount = 0;
  csvFeedback.errorCount = 0;
  if (isError) {
    csvFeedback.errorMessage = message;
    csvFeedback.errorCount = 1;
  } else {
    const match = message.match(/(\d+)\s.*angelegt/);
    csvFeedback.successCount = (match && match[1]) ? parseInt(match[1]) : 1;
  }
  showCsvFeedbackModal.value = true;
  if (!isError) setTimeout(() => { showCsvFeedbackModal.value = false; }, 3000);
};

const formatDate = (d) => d ? new Date(d).toLocaleDateString('de-DE') : '';
const formatDateTime = (dt) => dt ? new Date(dt).toLocaleDateString('de-DE', {weekday: 'short', day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit'}) : '';
const formatTime = (t) => t ? new Date(t).toLocaleTimeString('de-DE', {hour: '2-digit', minute: '2-digit'}) : '';
</script>

<style scoped>
.btn-primary {
  @apply rounded-lg bg-indigo-600 px-3 py-1.5 text-white font-bold hover:bg-indigo-700 transition shadow-sm border-none cursor-pointer disabled:opacity-50;
}
.btn-primary-xs {
  @apply rounded-md bg-indigo-600 px-2 py-0.5 text-white font-bold hover:bg-indigo-700 transition shadow-sm border-none cursor-pointer;
}
.btn-secondary {
  @apply bg-white text-gray-700 px-3 py-1.5 rounded-lg hover:bg-gray-50 font-bold border border-gray-200 transition shadow-sm cursor-pointer disabled:opacity-50;
}
.input-field {
  @apply rounded-lg border border-gray-300 px-2 py-1 text-gray-900 focus:ring-2 focus:ring-indigo-500 bg-white;
}
.animate-fade-in { animation: fadeIn 0.3s ease-in-out; }
@keyframes fadeIn { from { opacity: 0; transform: translateY(5px); } to { opacity: 1; transform: translateY(0); } }
</style>
