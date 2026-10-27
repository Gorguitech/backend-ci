package com.daara.internatadmin.pilotage.stats;

import com.daara.internatadmin.pilotage.ParallelCalls;
import com.daara.internatadmin.pilotage.client.EmployeCountsView;
import com.daara.internatadmin.pilotage.client.EnfantClient;
import com.daara.internatadmin.pilotage.client.EnfantCountsView;
import com.daara.internatadmin.pilotage.client.PaiementClient;
import com.daara.internatadmin.pilotage.client.PersonnelClient;
import com.daara.internatadmin.pilotage.client.SuiviResumeView;
import com.daara.internatadmin.pilotage.stats.dto.EmployeStatsResponse;
import com.daara.internatadmin.pilotage.stats.dto.EnfantStatsResponse;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.YearMonth;
import java.util.function.Supplier;

/**
 * Statistiques des modules eleves et personnel (EnfantService.getStats et EmployeService.getStats du monolithe).
 * Calculees ici et non dans enfant-service ou personnel-service : elles dependent des paiements, et paiement-service
 * appelle deja ces deux services (on evite un cycle d'appels).
 */
@Service
public class StatsService {

    private final EnfantClient enfantClient;
    private final PersonnelClient personnelClient;
    private final PaiementClient paiementClient;
    private final ParallelCalls parallelCalls;
    private final Clock clock;

    public StatsService(EnfantClient enfantClient, PersonnelClient personnelClient, PaiementClient paiementClient,
                        ParallelCalls parallelCalls, Clock clock) {
        this.enfantClient = enfantClient;
        this.personnelClient = personnelClient;
        this.paiementClient = paiementClient;
        this.parallelCalls = parallelCalls;
        this.clock = clock;
    }

    /** Impayes du mois : mensualite non PAYE (PARTIEL ou IMPAYE), comme dans le monolithe. */
    public EnfantStatsResponse enfantStats() {
        YearMonth month = YearMonth.now(clock);
        ParallelCalls.Batch batch = parallelCalls.batch();
        Supplier<EnfantCountsView> counts = batch.fork(() -> enfantClient.counts(month.getYear(), month.getMonthValue()));
        Supplier<SuiviResumeView> suivi = batch.fork(() -> paiementClient.suiviResume(month.getYear(), month.getMonthValue()));

        EnfantCountsView c = counts.get();
        SuiviResumeView s = suivi.get();
        return new EnfantStatsResponse(c.totalActifs(), c.totalSortis(), c.totalEnVacances(), c.nouveauxDuMois(),
                s.mensualitesPartielles() + s.mensualitesImpayees());
    }

    public EmployeStatsResponse employeStats() {
        YearMonth month = YearMonth.now(clock);
        ParallelCalls.Batch batch = parallelCalls.batch();
        Supplier<EmployeCountsView> counts = batch.fork(personnelClient::counts);
        Supplier<SuiviResumeView> suivi = batch.fork(() -> paiementClient.suiviResume(month.getYear(), month.getMonthValue()));

        EmployeCountsView c = counts.get();
        SuiviResumeView s = suivi.get();
        return new EmployeStatsResponse(c.totalActifs(), c.totalEnConge(), c.totalAnciens(), s.salairesPayes(), s.salairesImpayes());
    }
}
