package com.daara.internatadmin.enfant;

import com.daara.internatadmin.enfant.dto.EnfantCountsResponse;
import com.daara.internatadmin.enfant.dto.EnfantResponse;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.List;
import java.util.Set;

/** Endpoints inter-services (X-Internal-Key exige, jamais routes par la gateway). */
@Hidden
@RestController
@RequestMapping("/internal/enfants")
public class InternalEnfantController {

    private final EnfantService enfantService;

    public InternalEnfantController(EnfantService enfantService) {
        this.enfantService = enfantService;
    }

    /** Lu par paiement-service a la creation d'un paiement (tarifs, identite pour les copies figees). */
    @GetMapping("/{id}")
    public EnfantResponse get(@PathVariable Long id) {
        return enfantService.getById(id);
    }

    /** Lu par paiement-service pour les suivis mensuels et d'inscription (ACTIF et EN_VACANCES). */
    @GetMapping
    public List<EnfantResponse> listByStatuses(@RequestParam Set<StatutEnfant> statuses) {
        return enfantService.listByStatuses(statuses);
    }

    /** Lu par pilotage-service : statistiques du module et tableau de bord. */
    @GetMapping("/counts")
    public EnfantCountsResponse counts(@RequestParam int year, @RequestParam int month) {
        return enfantService.counts(YearMonth.of(year, month));
    }
}
