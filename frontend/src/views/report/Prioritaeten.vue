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
      <VeranstaltungHeader :veranstaltung="reportData.veranstaltung" />
      <header class="mb-4 d-flex justify-content-between align-items-center no-print">
        <h1>📅 {{ reportData.veranstaltung.name }} - Prioritätenanalyse</h1>
        <button @click="handlePrint" class="btn btn-secondary">
          <i class="bi bi-printer"></i> Drucken
        </button>
      </header>

      <section class="mt-4">
        <div class="row mb-3 align-items-center no-print">
          <div class="col-md-4">
            <label for="classFilter" class="form-label small fw-bold text-muted">Gruppe filtern:</label>
            <select id="classFilter" class="form-select shadow-sm" v-model="selectedGruppe">
              <option value="all">Alle Gruppen anzeigen</option>
              <option v-for="gruppe in reportData.gruppen" :key="gruppe" :value="gruppe">{{ gruppe }}</option>
            </select>
          </div>
          <div class="col-md-8 text-end pt-4">
            <span class="badge bg-light text-dark border" id="visibleCount">
              {{ visibleCountText }}
            </span>
          </div>
        </div>

        <div class="card shadow-sm">
          <div class="table-responsive" style="max-height: 900px;">
            <table class="table table-hover table-prios m-0">
              <thead class="sticky-table-header">
              <tr class="table-dark">
                <th class="p-2" style="min-width: 200px;">Teilnehmer</th>
                <th v-for="(wv, wv_oid) in reportData.wv_dict" :key="wv_oid" class="text-center p-2" :title="getWvTitle(wv)">
                  WV{{ wv_oid }}
                </th>
              </tr>
              </thead>
              <tbody>
              <tr v-for="tn_erf in filteredTeilnehmer" :key="tn_erf.teilnehmer.id" class="participant-row">
                <td class="fw-bold border-end bg-light">{{ tn_erf.teilnehmer.fullname }}</td>
                <td v-for="wv_oid in reportData.wvOids" :key="wv_oid" class="text-center p-2" :class="getStatusClass(tn_erf.wvStatuus[wv_oid])">
                  <template v-if="tn_erf.wvStatuus[wv_oid] && tn_erf.wvStatuus[wv_oid].status !== '0'">
                    <div v-if="tn_erf.wvStatuus[wv_oid].instanz" class="fw-bold">
                      {{ getSlotInfo(tn_erf.wvStatuus[wv_oid], wv_oid) }}
                    </div>
                    <div class="small">
                      <b v-if="tn_erf.wvStatuus[wv_oid].prioWert > 0">Prio {{ tn_erf.wvStatuus[wv_oid].prioWert }}</b>
                      <span v-if="tn_erf.wvStatuus[wv_oid].instanz">({{ reportData.num_instanzen_pro_wv[tn_erf.wvStatuus[wv_oid].instanz] }}x)</span>
                    </div>
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
              <div class="d-flex align-items-center"><span class="legend-color bg-success"></span><span class="small">Wunsch erfüllt</span></div>
              <div class="d-flex align-items-center"><span class="legend-color bg-warning"></span><span class="small">Wunsch nicht erfüllt</span></div>
              <div class="d-flex align-items-center"><span class="legend-color bg-auffuellung"></span><span class="small">Auffüller</span></div>
              <div class="d-flex align-items-center"><span class="legend-color status-unavailable"></span><span class="small text-muted">Abwesend (Matrix)</span></div>
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
import VeranstaltungHeader from '../../components/VeranstaltungHeader.vue';

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
    const response = await api.get(`/api/reports/${veranstaltungId}/prios-dashboard-data`);
    reportData.value = response.data;
    document.title = `${response.data.veranstaltung.name} - Prioritätenanalyse`;
  } catch (err) {
    error.value = 'Fehler beim Laden der Daten: ' + (err.response?.data?.message || err.message);
  } finally {
    loading.value = false;
  }
});

const filteredTeilnehmer = computed(() => {
  if (!reportData.value || !reportData.value.teilnehmer_erfuellung) return [];
  if (selectedGruppe.value === 'all') {
    return reportData.value.teilnehmer_erfuellung;
  }
  return reportData.value.teilnehmer_erfuellung.filter(tn => tn.teilnehmer.gruppen.includes(selectedGruppe.value));
});

const visibleCountText = computed(() => {
  if (!reportData.value) return '';
  if (selectedGruppe.value === 'all') {
    return `Zeige alle ${reportData.value.teilnehmer_erfuellung.length} Teilnehmer`;
  }
  return `Gruppe ${selectedGruppe.value}: ${filteredTeilnehmer.value.length} Teilnehmer`;
});

const getWvTitle = (wv) => {
  const ref = reportData.value.ref_dict[wv.referentId];
  return `${ref.organisation}: ${wv.titel}`;
};

const getStatusClass = (wvs) => {
  if (!wvs) return '';
  switch (wvs.status) {
    case '+': return 'bg-success text-white';
    case 'f': return 'bg-auffuellung';
    case '-': return 'bg-warning';
    default: return 'status-unavailable';
  }
};

const getSlotInfo = (wvs, wv_oid) => {
  const wv_oid_index = reportData.value.wvOids.indexOf(parseInt(wv_oid));
  const wvs_idx = wvs.instanz - 1;
  const slot_idx = reportData.value.instanz_slot[wv_oid_index][wvs_idx];
  const slot = reportData.value.slots[reportData.value.slotOids[slot_idx - 1]];
  const raum_idx = reportData.value.instanz_raum[wv_oid_index][wvs_idx];
  const raum = reportData.value.raeume[reportData.value.raumOids[raum_idx - 1]];
  return `${slot.tag}, ${slot.start} @ ${raum.name}`;
};
</script>

<style>
/* Globale Druck-Styles */
@media print {
  .no-print { display: none !important; }
  .print-footer { display: block !important; position: fixed; bottom: 0; width: 100%; text-align: center; font-size: 0.8rem; color: #6c757d; }
  body { background-color: #fff; }
  .container-fluid { width: 100% !important; padding: 0 !important; margin: 0 !important; }
  .table { font-size: 9pt; }
}
.print-footer { display: none; }

/* Spezifische Styles aus dem Template */
body {
  background-color: #f8f9fa;
  font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
}
.sticky-table-header {
  position: sticky;
  top: 0;
  background-color: white;
  z-index: 10;
}
.table-prios td {
  font-size: 0.7rem;
  padding: 2px !important;
  min-width: 40px;
  border: 1px solid #dee2e6;
}
.bg-auffuellung {
  background-color: rgba(111, 66, 193, 0.7);
  color: white;
}
.status-unavailable {
  background-color: #e9ecef;
  text-decoration: line-through;
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
