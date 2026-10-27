package com.daara.internatadmin.pilotage.notification;

import com.daara.internatadmin.common.security.CurrentUser;
import com.daara.internatadmin.pilotage.ParallelCalls;
import com.daara.internatadmin.pilotage.client.AuthClient;
import com.daara.internatadmin.pilotage.client.ComptabiliteClient;
import com.daara.internatadmin.pilotage.client.PaiementClient;
import com.daara.internatadmin.pilotage.client.SuiviResumeView;
import com.daara.internatadmin.pilotage.client.TotauxComptabiliteView;
import com.daara.internatadmin.pilotage.client.TotauxPaiementsView;
import com.daara.internatadmin.pilotage.client.UserStatsView;
import com.daara.internatadmin.pilotage.dashboard.SoldeGlobal;
import com.daara.internatadmin.pilotage.notification.dto.NotificationItem;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Types, gravites, messages et ordre repris mot pour mot de NotificationService du monolithe. */
@Service
public class NotificationService {

    private final PaiementClient paiementClient;
    private final ComptabiliteClient comptabiliteClient;
    private final AuthClient authClient;
    private final ParallelCalls parallelCalls;
    private final Clock clock;

    public NotificationService(PaiementClient paiementClient, ComptabiliteClient comptabiliteClient, AuthClient authClient,
                               ParallelCalls parallelCalls, Clock clock) {
        this.paiementClient = paiementClient;
        this.comptabiliteClient = comptabiliteClient;
        this.authClient = authClient;
        this.parallelCalls = parallelCalls;
        this.clock = clock;
    }

    public List<NotificationItem> getNotifications(CurrentUser currentUser) {
        YearMonth month = YearMonth.now(clock);
        ParallelCalls.Batch batch = parallelCalls.batch();
        Supplier<SuiviResumeView> suivi = batch.fork(() -> paiementClient.suiviResume(month.getYear(), month.getMonthValue()));
        // donnees des deux notifications reservees a l'ADMIN : jamais demandees pour un GESTIONNAIRE
        Supplier<TotauxPaiementsView> paiements = currentUser.isAdmin() ? batch.fork(paiementClient::totauxGlobaux) : null;
        Supplier<TotauxComptabiliteView> comptabilite = currentUser.isAdmin() ? batch.fork(comptabiliteClient::totauxGlobaux) : null;
        Supplier<UserStatsView> users = currentUser.isAdmin() ? batch.fork(authClient::stats) : null;

        List<NotificationItem> items = new ArrayList<>();
        SuiviResumeView resume = suivi.get();

        long impayes = resume.mensualitesImpayees();
        if (impayes > 0) {
            items.add(new NotificationItem(
                    "MENSUALITES_IMPAYEES", "WARNING",
                    impayes + " enfant(s) n'ont pas encore payé leur mensualité ce mois-ci", impayes));
        }

        long partiels = resume.mensualitesPartielles();
        if (partiels > 0) {
            items.add(new NotificationItem(
                    "MENSUALITES_PARTIELLES", "INFO",
                    partiels + " enfant(s) ont un paiement partiel ce mois-ci", partiels));
        }

        long inscriptionsImpayees = resume.inscriptionsImpayees();
        if (inscriptionsImpayees > 0) {
            items.add(new NotificationItem(
                    "INSCRIPTIONS_IMPAYEES", "WARNING",
                    inscriptionsImpayees + " enfant(s) n'ont jamais payé leurs frais d'inscription", inscriptionsImpayees));
        }

        long salairesImpayes = resume.salairesImpayes();
        if (salairesImpayes > 0) {
            items.add(new NotificationItem(
                    "SALAIRES_IMPAYES", "WARNING",
                    salairesImpayes + " employé(s) n'ont pas encore reçu leur salaire ce mois-ci", salairesImpayes));
        }

        if (currentUser.isAdmin()) {
            BigDecimal solde = SoldeGlobal.of(paiements.get(), comptabilite.get()).solde();
            if (solde.compareTo(BigDecimal.ZERO) < 0) {
                items.add(new NotificationItem(
                        "SOLDE_NEGATIF", "DANGER",
                        "La trésorerie est négative (" + solde + ")", 1));
            }

            long comptesDesactives = users.get().totalDesactives();
            if (comptesDesactives > 0) {
                items.add(new NotificationItem(
                        "COMPTES_DESACTIVES", "INFO",
                        comptesDesactives + " compte(s) utilisateur désactivé(s)", comptesDesactives));
            }
        }

        return items;
    }
}
