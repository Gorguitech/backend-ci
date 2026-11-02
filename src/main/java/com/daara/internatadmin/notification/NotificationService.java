package com.daara.internatadmin.notification;

import com.daara.internatadmin.finance.paiementenfant.PaiementEnfantService;
import com.daara.internatadmin.finance.paiementsalaire.PaiementSalaireService;
import com.daara.internatadmin.finance.tresorerie.TresorerieService;
import com.daara.internatadmin.finance.tresorerie.dto.SoldeGlobalResponse; // Import ajouté
import com.daara.internatadmin.notification.dto.NotificationItem;
import com.daara.internatadmin.user.Role;
import com.daara.internatadmin.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final PaiementEnfantService paiementEnfantService;
    private final PaiementSalaireService paiementSalaireService;
    private final TresorerieService tresorerieService;
    private final UserService userService;

    public List<NotificationItem> getNotifications(Role currentUserRole) {
        List<NotificationItem> items = new ArrayList<>();
        LocalDate now = LocalDate.now();

        var suiviMensuel = paiementEnfantService.getSuiviMensuel(now.getYear(), now.getMonthValue(), "ALL");

        long impayes = suiviMensuel.stream().filter(s -> s.statutPaiement().equals("IMPAYE")).count();
        if (impayes > 0) {
            items.add(new NotificationItem(
                    "MENSUALITES_IMPAYEES", "WARNING",
                    impayes + " enfant(s) n'ont pas encore payé leur mensualité ce mois-ci", impayes
            ));
        }

        long partiels = suiviMensuel.stream().filter(s -> s.statutPaiement().equals("PARTIEL")).count();
        if (partiels > 0) {
            items.add(new NotificationItem(
                    "MENSUALITES_PARTIELLES", "INFO",
                    partiels + " enfant(s) ont un paiement partiel ce mois-ci", partiels
            ));
        }

        long inscriptionsImpayees = paiementEnfantService.getInscriptions("IMPAYE").size();
        if (inscriptionsImpayees > 0) {
            items.add(new NotificationItem(
                    "INSCRIPTIONS_IMPAYEES", "WARNING",
                    inscriptionsImpayees + " enfant(s) n'ont jamais payé leurs frais d'inscription", inscriptionsImpayees
            ));
        }

        long salairesImpayes = paiementSalaireService.getSuiviMensuel(now.getYear(), now.getMonthValue(), "IMPAYE").size();
        if (salairesImpayes > 0) {
            items.add(new NotificationItem(
                    "SALAIRES_IMPAYES", "WARNING",
                    salairesImpayes + " employé(s) n'ont pas encore reçu leur salaire ce mois-ci", salairesImpayes
            ));
        }

        if (currentUserRole == Role.ADMIN) {
            // Remplacer getSoldeCumule() par getSoldeGlobal().solde()
            SoldeGlobalResponse soldeGlobal = tresorerieService.getSoldeGlobal();
            BigDecimal solde = soldeGlobal.solde();
            if (solde.compareTo(BigDecimal.ZERO) < 0) {
                items.add(new NotificationItem(
                        "SOLDE_NEGATIF", "DANGER",
                        "La trésorerie est négative (" + solde + ")", 1
                ));
            }

            long comptesDesactives = userService.getStats().totalDesactives();
            if (comptesDesactives > 0) {
                items.add(new NotificationItem(
                        "COMPTES_DESACTIVES", "INFO",
                        comptesDesactives + " compte(s) utilisateur désactivé(s)", comptesDesactives
                ));
            }
        }

        return items;
    }
}