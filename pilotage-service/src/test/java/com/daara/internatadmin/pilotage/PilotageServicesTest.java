package com.daara.internatadmin.pilotage;

import com.daara.internatadmin.common.error.ServiceUnavailableException;
import com.daara.internatadmin.common.security.CurrentUser;
import com.daara.internatadmin.pilotage.client.AuthClient;
import com.daara.internatadmin.pilotage.client.ComptabiliteClient;
import com.daara.internatadmin.pilotage.client.EmployeCountsView;
import com.daara.internatadmin.pilotage.client.EnfantClient;
import com.daara.internatadmin.pilotage.client.EnfantCountsView;
import com.daara.internatadmin.pilotage.client.PaiementClient;
import com.daara.internatadmin.pilotage.client.PersonnelClient;
import com.daara.internatadmin.pilotage.client.SuiviResumeView;
import com.daara.internatadmin.pilotage.client.TotauxComptabiliteView;
import com.daara.internatadmin.pilotage.client.TotauxPaiementsView;
import com.daara.internatadmin.pilotage.client.UserStatsView;
import com.daara.internatadmin.pilotage.dashboard.DashboardService;
import com.daara.internatadmin.pilotage.dashboard.dto.GlobalDashboardResponse;
import com.daara.internatadmin.pilotage.notification.NotificationService;
import com.daara.internatadmin.pilotage.notification.dto.NotificationItem;
import com.daara.internatadmin.pilotage.stats.StatsService;
import com.daara.internatadmin.pilotage.stats.dto.EmployeStatsResponse;
import com.daara.internatadmin.pilotage.stats.dto.EnfantStatsResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Regles, messages et gravites du monolithe (DashboardService, NotificationService, getStats). */
class PilotageServicesTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-27T10:00:00Z"), ZoneOffset.UTC);
    private static final CurrentUser GESTIONNAIRE = new CurrentUser(5L, "awa.diop", "GESTIONNAIRE");
    private static final CurrentUser ADMIN = new CurrentUser(1L, "admin", "ADMIN");

    private final EnfantClient enfantClient = mock(EnfantClient.class);
    private final PersonnelClient personnelClient = mock(PersonnelClient.class);
    private final PaiementClient paiementClient = mock(PaiementClient.class);
    private final ComptabiliteClient comptabiliteClient = mock(ComptabiliteClient.class);
    private final AuthClient authClient = mock(AuthClient.class);

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final ParallelCalls parallelCalls = new ParallelCalls(executor, Duration.ofSeconds(5));

    private final DashboardService dashboardService = new DashboardService(enfantClient, personnelClient, paiementClient,
            comptabiliteClient, parallelCalls, CLOCK);
    private final NotificationService notificationService = new NotificationService(paiementClient, comptabiliteClient,
            authClient, parallelCalls, CLOCK);
    private final StatsService statsService = new StatsService(enfantClient, personnelClient, paiementClient, parallelCalls, CLOCK);

    @AfterEach
    void close() {
        executor.close();
    }

    // ---------------------------------------------------------------- tableau de bord

    @Test
    void dashboard_soldeGlobalEtImpayesDuMoisCourant() {
        when(enfantClient.counts(2026, 10)).thenReturn(new EnfantCountsView(12, 3, 2, 4));
        when(personnelClient.counts()).thenReturn(new EmployeCountsView(5, 1, 2));
        when(paiementClient.totauxGlobaux()).thenReturn(new TotauxPaiementsView(new BigDecimal("300000.00"), new BigDecimal("200000.00")));
        when(comptabiliteClient.totauxGlobaux()).thenReturn(new TotauxComptabiliteView(new BigDecimal("50000.00"), new BigDecimal("80000.00")));
        when(paiementClient.suiviResume(2026, 10)).thenReturn(new SuiviResumeView(7, 2, 3, 1, 4, 1));

        GlobalDashboardResponse dashboard = dashboardService.getGlobalDashboard();

        // recettes 300000 + 50000 ; depenses 200000 + 80000 ; impayes = partiels + impayes (statut different de PAYE)
        assertThat(dashboard).isEqualTo(new GlobalDashboardResponse(12, 5, new BigDecimal("350000.00"),
                new BigDecimal("280000.00"), new BigDecimal("70000.00"), 5));
        verifyNoInteractions(authClient);
    }

    @Test
    void dashboard_dependanceEnPanne_echecSansValeurParDefaut() {
        when(enfantClient.counts(2026, 10)).thenReturn(new EnfantCountsView(12, 3, 2, 4));
        when(personnelClient.counts()).thenReturn(new EmployeCountsView(5, 1, 2));
        when(paiementClient.totauxGlobaux()).thenReturn(new TotauxPaiementsView(BigDecimal.ZERO, BigDecimal.ZERO));
        when(comptabiliteClient.totauxGlobaux()).thenThrow(new ServiceUnavailableException());
        when(paiementClient.suiviResume(2026, 10)).thenReturn(new SuiviResumeView(0, 0, 0, 0, 0, 0));

        assertThatThrownBy(dashboardService::getGlobalDashboard).isInstanceOf(ServiceUnavailableException.class);
    }

    // ---------------------------------------------------------------- notifications

    @Test
    void notifications_gestionnaire_quatreNotificationsSansDonneesAdmin() {
        when(paiementClient.suiviResume(2026, 10)).thenReturn(new SuiviResumeView(7, 2, 3, 1, 4, 1));

        assertThat(notificationService.getNotifications(GESTIONNAIRE)).containsExactly(
                new NotificationItem("MENSUALITES_IMPAYEES", "WARNING",
                        "3 enfant(s) n'ont pas encore payé leur mensualité ce mois-ci", 3),
                new NotificationItem("MENSUALITES_PARTIELLES", "INFO",
                        "2 enfant(s) ont un paiement partiel ce mois-ci", 2),
                new NotificationItem("INSCRIPTIONS_IMPAYEES", "WARNING",
                        "1 enfant(s) n'ont jamais payé leurs frais d'inscription", 1),
                new NotificationItem("SALAIRES_IMPAYES", "WARNING",
                        "1 employé(s) n'ont pas encore reçu leur salaire ce mois-ci", 1));
        verifyNoInteractions(authClient, comptabiliteClient);
    }

    @Test
    void notifications_admin_soldeNegatifEtComptesDesactivesEnDernier() {
        when(paiementClient.suiviResume(2026, 10)).thenReturn(new SuiviResumeView(1, 0, 4, 0, 1, 0));
        when(paiementClient.totauxGlobaux()).thenReturn(new TotauxPaiementsView(new BigDecimal("37000.00"), new BigDecimal("75000.00")));
        when(comptabiliteClient.totauxGlobaux()).thenReturn(new TotauxComptabiliteView(new BigDecimal("50000.00"), new BigDecimal("76500.00")));
        when(authClient.stats()).thenReturn(new UserStatsView(2));

        assertThat(notificationService.getNotifications(ADMIN)).containsExactly(
                new NotificationItem("MENSUALITES_IMPAYEES", "WARNING",
                        "4 enfant(s) n'ont pas encore payé leur mensualité ce mois-ci", 4),
                new NotificationItem("SOLDE_NEGATIF", "DANGER", "La trésorerie est négative (-64500.00)", 1),
                new NotificationItem("COMPTES_DESACTIVES", "INFO", "2 compte(s) utilisateur désactivé(s)", 2));
    }

    @Test
    void notifications_seuils_aucuneNotificationAZeroNiPourUnSoldeNul() {
        when(paiementClient.suiviResume(2026, 10)).thenReturn(new SuiviResumeView(9, 0, 0, 0, 3, 0));
        when(paiementClient.totauxGlobaux()).thenReturn(new TotauxPaiementsView(new BigDecimal("100000.00"), new BigDecimal("60000.00")));
        when(comptabiliteClient.totauxGlobaux()).thenReturn(new TotauxComptabiliteView(new BigDecimal("0"), new BigDecimal("40000.00")));
        when(authClient.stats()).thenReturn(new UserStatsView(0));

        assertThat(notificationService.getNotifications(ADMIN)).as("solde exactement nul : pas de SOLDE_NEGATIF").isEmpty();
        assertThat(notificationService.getNotifications(GESTIONNAIRE)).isEmpty();
    }

    @Test
    void notifications_admin_authServiceEnPanne_echec() {
        when(paiementClient.suiviResume(2026, 10)).thenReturn(new SuiviResumeView(1, 0, 0, 0, 1, 0));
        when(paiementClient.totauxGlobaux()).thenReturn(new TotauxPaiementsView(BigDecimal.ONE, BigDecimal.ZERO));
        when(comptabiliteClient.totauxGlobaux()).thenReturn(new TotauxComptabiliteView(BigDecimal.ZERO, BigDecimal.ZERO));
        when(authClient.stats()).thenThrow(new ServiceUnavailableException());

        assertThatThrownBy(() -> notificationService.getNotifications(ADMIN)).isInstanceOf(ServiceUnavailableException.class);
    }

    // ---------------------------------------------------------------- statistiques des modules

    @Test
    void statsEnfants_effectifsEtImpayesNonPayes() {
        when(enfantClient.counts(2026, 10)).thenReturn(new EnfantCountsView(12, 3, 2, 4));
        when(paiementClient.suiviResume(2026, 10)).thenReturn(new SuiviResumeView(7, 2, 3, 1, 4, 1));

        assertThat(statsService.enfantStats()).isEqualTo(new EnfantStatsResponse(12, 3, 2, 4, 5));
        verify(enfantClient).counts(2026, 10);
    }

    @Test
    void statsEmployes_salairesPayesEtRestants() {
        when(personnelClient.counts()).thenReturn(new EmployeCountsView(5, 1, 2));
        when(paiementClient.suiviResume(2026, 10)).thenReturn(new SuiviResumeView(7, 2, 3, 1, 4, 2));

        assertThat(statsService.employeStats()).isEqualTo(new EmployeStatsResponse(5, 1, 2, 4, 2));
    }
}
