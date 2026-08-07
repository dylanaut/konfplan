<template>
  <div class="container-fluid py-4">
    <div v-if="loading" class="d-flex justify-content-center">
      <div class="spinner-border" role="status">
        <span class="visually-hidden">Loading...</span>
      </div>
    </div>

    <div v-else-if="error" class="alert alert-danger">
      {{ error }}
    </div>

    <template v-else>
      <header class="mb-4 d-flex justify-content-between align-items-center no-print">
        <h1>📅 {{ reportData.veranstaltung.name }} - Teilnehmer-Zuordnungen</h1>
        <button @click="handlePrint" class="btn btn-secondary">
          <i class="bi bi-printer"></i> Drucken
        </button>
      </header>

      <section class="mt-5">
        <div class="row mb-3 align-items-center no-print">
          <div class="col-md-4">
            <label for="classFilter" class="form-label small fw-bold text-muted">Gruppe filtern:</label>
            <select id="classFilter" class="form-select shadow-sm" v-model="selectedGruppe">
              <option value="all">Alle Gruppen anzeigen</option>
              <option v-for="gruppe in reportData.gruppen" :key="gruppe" :value="gruppe">{{ gruppe }}</option>
            </select>
          </div>
          <div class="col-md-8 text-end pt-4">
            <span class="badge bg-light text-dark border">
              {{ visibleCountText }}
            </span>
          </div>
        </div>

        <div class="card shadow-sm">
          <div class="table-responsive" style="max-height: 900px;">
            <table class="table table-hover table-bordered table-sm m-0" style="font-size: 0.85rem;">
              <thead class="table-dark sticky-table-header text-center">
              <tr>
                <th class="p-2" style="min-width: 200px;">Teilnehmer</th>
                <th v-for="s_info in reportData.slots" :key="s_info.id" class="p-2">
                  {{ s_info.tag }}, {{ s_info.start }}-{{ s_info.ende }}
                </th>
              </tr>
              </thead>
              <tbody>
              <tr v-for="tn_sp in filteredTeilnehmer" :key="tn_sp.teilnehmer.id" class="participant-row">
                <td class="fw-bold bg-light border-end">{{ tn_sp.teilnehmer.fullname }}</td>
                <td v-for="slot_oid in Object.keys(reportData.slots)" :key="slot_oid"
                    class="text-center p-2"
                    :class="getStatusClass(tn_sp.tnSlotBelegungen[slot_oid])"
                    style="width: 150px; vertical-align: middle;">
                  <template v-if="tn_sp.tnSlotBelegungen[slot_oid] && tn_sp.tnSlotBelegungen[slot_oid].typ !== 'frei' && tn_sp.tnSlotBelegungen[slot_oid].typ !== 'abwesend'">
                    <small class="d-block fw-bold">{{ truncTo(tn_sp.tnSlotBelegungen[slot_oid].titel, 20) }}</small>
                    <span class="text-muted fw-bold">{{ tn_sp.tnSlotBelegungen[slot_oid].raum }}</span>
                  </template>
                  <template v-else>
                    <span class="small">{{ truncTo(tn_sp.tnSlotBelegungen[slot_oid].titel, 20) }}</span>
                  </template>
                </td>
              </tr>
              </tbody>
            </table>
          </div>
        </div>
      </section>

      <div class="container-fluid my-4">
        <div class="card shadow-sm border-0">
          <div class="card-body py-2">
            <h6 class="text-muted small mb-2 text-uppercase fw-bold">Farblegende & Status</h6>
            <div class="d-flex flex-wrap gap-3">
                <div class="d-flex align-items-center"><span class="legend-color bg-primary"></span><span class="small">Pflichtvortrag</span></div>
                <div class="d-flex align-items-center"><span class="legend-color bg-success"></span><span class="small">Wunsch erfüllt</span></div>
                <div class="d-flex align-items-center"><span class="legend-color bg-warning"></span><span class="small">Wunsch nicht erfüllt</span></div>
                <div class="d-flex align-items-center"><span class="legend-color bg-auffuellung"></span><span class="small">Auffüller</span></div>
                <div class="d-flex align-items-center"><span class="legend-color status-unavailable"></span><span class="small text-muted">Abwesend</span></div>
            </div>
          </div>
        </div>
      </div>

      <footer class="text-center py-2 text-muted small print-footer">
        <span class="badge bg-secondary">Stand: {{ reportData.geplantAm }}</span>
      </footer>
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue';
import { useRoute } from 'vue-router';
import api from '../../api/axios';

const route = useRoute();
const reportData = ref(null);
const loading = ref(true);
const error = ref(null);
const selectedGruppe = ref('all');

const handlePrint = () => window.print();

onMounted(async () => {
  const veranstaltungId = route.params.vid;
  if (!veranstaltungId) {
    error.value = "Keine Veranstaltungs-ID in der URL gefunden.";
    loading.value = false;
    return;
  }
  try {
    const response = await api.get(`/api/reports/${veranstaltungId}/teilnehmer-dashboard-data`);
    reportData.value = response.data;
    document.title = `${response.data.veranstaltung.name} - Teilnehmer-Zuordnungen`;
  } catch (err) {
    error.value = 'Fehler beim Laden der Daten: ' + (err.response?.data?.message || err.message);
  } finally {
    loading.value = false;
  }
});

const filteredTeilnehmer = computed(() => {
  if (!reportData.value || !reportData.value.teilnehmer_stundenplan) return [];
  if (selectedGruppe.value === 'all') {
    return reportData.value.teilnehmer_stundenplan;
  }
  return reportData.value.teilnehmer_stundenplan.filter(tn => tn.teilnehmer.gruppen.includes(selectedGruppe.value));
});

const visibleCountText = computed(() => {
  if (!reportData.value) return '';
  if (selectedGruppe.value === 'all') {
    return `Zeige alle ${reportData.value.teilnehmer_stundenplan.length} Teilnehmer`;
  }
  return `Gruppe ${selectedGruppe.value}: ${filteredTeilnehmer.value.length} Teilnehmer`;
});

const getStatusClass = (sb) => {
  if (!sb) return 'text-muted';
  switch (sb.typ) {
    case 'pflicht': return 'bg-primary text-white';
    case 'wahl': return 'bg-success text-white';
    case 'auffuellung': return 'bg-auffuellung';
    case 'abwesend': return 'status-unavailable';
    default: return 'text-muted';
  }
};

const truncTo = (text, maxLen) => {
  if (!text) return '';
  return text.length > maxLen ? text.substring(0, maxLen - 1) + '…' : text;
};
</script>

<style>
/* Globale Druck-Styles */
@media print {
  .no-print { display: none !important; }
  .print-footer { display: block !important; position: fixed; bottom: 0; width: 100%; text-align: center; font-size: 0.8rem; color: #6c757d; }
  body { background-color: #fff; -webkit-print-color-adjust: exact; print-color-adjust: exact; }
  .container-fluid { width: 100% !important; padding: 0 !important; margin: 0 !important; }
  .table { font-size: 9pt; }
}
.print-footer { display: none; }

/* Spezifische Styles */
.sticky-table-header {
  position: sticky;
  top: 0;
  background-color: white;
  z-index: 10;
}
.status-unavailable {
  background-color: #e9ecef !important;
  color: #adb5bd;
  text-decoration: line-through;
}
.bg-auffuellung {
  background-color: rgba(111, 66, 193, 0.7) !important;
  color: white !important;
}
.legend-color {
  display: inline-block;
  border: 1px solid #ccc;
  box-shadow: 0 1px 3px rgba(0,0,0,0.1);
  width: 20px;
  height: 20px;
  border-radius: 3px;
  margin-right: 8px;
}
</style>
