package com.daara.internatadmin.paiement.internal;

import com.daara.internatadmin.paiement.StatutPaiement;
import com.daara.internatadmin.paiement.enfant.PaiementEnfantRepository;
import com.daara.internatadmin.paiement.enfant.PaiementEnfantService;
import com.daara.internatadmin.paiement.enfant.dto.SuiviMensuelResponse;
import com.daara.internatadmin.paiement.salaire.PaiementSalaireRepository;
import com.daara.internatadmin.paiement.salaire.PaiementSalaireService;
import com.daara.internatadmin.paiement.salaire.dto.SalaireSuiviResponse;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.List;

/** Endpoints inter-services (X-Internal-Key exige, jamais routes par la gateway). */
@Hidden
@RestController
@RequestMapping("/internal/paiements")
public class InternalPaiementController {

    private final PaiementEnfantRepository paiementEnfantRepository;
    private final PaiementSalaireRepository paiementSalaireRepository;
    private final PaiementEnfantService paiementEnfantService;
    private final PaiementSalaireService paiementSalaireService;

    public InternalPaiementController(PaiementEnfantRepository paiementEnfantRepository,
                                      PaiementSalaireRepository paiementSalaireRepository,
                                      PaiementEnfantService paiementEnfantService,
                                      PaiementSalaireService paiementSalaireService) {
        this.paiementEnfantRepository = paiementEnfantRepository;
        this.paiementSalaireRepository = paiementSalaireRepository;
        this.paiementEnfantService = paiementEnfantService;
        this.paiementSalaireService = paiementSalaireService;
    }

    /** Tresorerie d'un mois : paiements d'eleves par date de paiement, salaires par mois concerne (regle du monolithe). */
    @GetMapping("/totaux-mensuels")
    public TotauxResponse totauxMensuels(@RequestParam int year, @RequestParam int month) {
        YearMonth ym = YearMonth.of(year, month);
        return new TotauxResponse(
                paiementEnfantRepository.sumByStatusAndPaymentDateBetween(StatutPaiement.VALIDE, ym.atDay(1), ym.atEndOfMonth()),
                paiementSalaireRepository.sumByStatusAndConcernedMonth(StatutPaiement.VALIDE, ym.atDay(1)));
    }

    /** Solde global : tous les paiements valides depuis l'origine. */
    @GetMapping("/totaux-globaux")
    public TotauxResponse totauxGlobaux() {
        return new TotauxResponse(
                paiementEnfantRepository.sumByStatus(StatutPaiement.VALIDE),
                paiementSalaireRepository.sumByStatus(StatutPaiement.VALIDE));
    }

    @GetMapping("/suivi-resume")
    public SuiviResumeResponse suiviResume(@RequestParam int year, @RequestParam int month) {
        List<SuiviMensuelResponse> mensualites = paiementEnfantService.getSuiviMensuel(year, month, "ALL");
        List<SalaireSuiviResponse> salaires = paiementSalaireService.getSuiviMensuel(year, month, "ALL");
        return new SuiviResumeResponse(
                countMensualites(mensualites, "PAYE"),
                countMensualites(mensualites, "PARTIEL"),
                countMensualites(mensualites, "IMPAYE"),
                paiementEnfantService.getInscriptions("IMPAYE").size(),
                salaires.stream().filter(s -> "PAYE".equals(s.statut())).count(),
                salaires.stream().filter(s -> "IMPAYE".equals(s.statut())).count());
    }

    private static long countMensualites(List<SuiviMensuelResponse> lines, String statut) {
        return lines.stream().filter(s -> statut.equals(s.statutPaiement())).count();
    }
}
