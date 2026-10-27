package com.daara.internatadmin.pilotage.dashboard;

import com.daara.internatadmin.pilotage.ParallelCalls;
import com.daara.internatadmin.pilotage.client.ComptabiliteClient;
import com.daara.internatadmin.pilotage.client.EmployeCountsView;
import com.daara.internatadmin.pilotage.client.EnfantClient;
import com.daara.internatadmin.pilotage.client.EnfantCountsView;
import com.daara.internatadmin.pilotage.client.PaiementClient;
import com.daara.internatadmin.pilotage.client.PersonnelClient;
import com.daara.internatadmin.pilotage.client.SuiviResumeView;
import com.daara.internatadmin.pilotage.client.TotauxComptabiliteView;
import com.daara.internatadmin.pilotage.client.TotauxPaiementsView;
import com.daara.internatadmin.pilotage.dashboard.dto.GlobalDashboardResponse;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.YearMonth;
import java.util.function.Supplier;

@Service
public class DashboardService {

    private final EnfantClient enfantClient;
    private final PersonnelClient personnelClient;
    private final PaiementClient paiementClient;
    private final ComptabiliteClient comptabiliteClient;
    private final ParallelCalls parallelCalls;
    private final Clock clock;

    public DashboardService(EnfantClient enfantClient, PersonnelClient personnelClient, PaiementClient paiementClient,
                            ComptabiliteClient comptabiliteClient, ParallelCalls parallelCalls, Clock clock) {
        this.enfantClient = enfantClient;
        this.personnelClient = personnelClient;
        this.paiementClient = paiementClient;
        this.comptabiliteClient = comptabiliteClient;
        this.parallelCalls = parallelCalls;
        this.clock = clock;
    }

    /** Regles du monolithe : impayes du mois = eleves suivis dont la mensualite n'est pas PAYE (PARTIEL ou IMPAYE). */
    public GlobalDashboardResponse getGlobalDashboard() {
        YearMonth month = YearMonth.now(clock);
        ParallelCalls.Batch batch = parallelCalls.batch();
        Supplier<EnfantCountsView> enfants = batch.fork(() -> enfantClient.counts(month.getYear(), month.getMonthValue()));
        Supplier<EmployeCountsView> employes = batch.fork(personnelClient::counts);
        Supplier<TotauxPaiementsView> paiements = batch.fork(paiementClient::totauxGlobaux);
        Supplier<TotauxComptabiliteView> comptabilite = batch.fork(comptabiliteClient::totauxGlobaux);
        Supplier<SuiviResumeView> suivi = batch.fork(() -> paiementClient.suiviResume(month.getYear(), month.getMonthValue()));

        SoldeGlobal solde = SoldeGlobal.of(paiements.get(), comptabilite.get());
        SuiviResumeView resume = suivi.get();
        return new GlobalDashboardResponse(
                enfants.get().totalActifs(),
                employes.get().totalActifs(),
                solde.totalRecettes(),
                solde.totalDepenses(),
                solde.solde(),
                resume.mensualitesPartielles() + resume.mensualitesImpayees());
    }
}
