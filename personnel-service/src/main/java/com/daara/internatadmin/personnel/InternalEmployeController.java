package com.daara.internatadmin.personnel;

import com.daara.internatadmin.personnel.dto.EmployeCountsResponse;
import com.daara.internatadmin.personnel.dto.EmployeResponse;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/** Endpoints inter-services (X-Internal-Key exige, jamais routes par la gateway). */
@Hidden
@RestController
@RequestMapping("/internal/employes")
public class InternalEmployeController {

    private final EmployeService employeService;

    public InternalEmployeController(EmployeService employeService) {
        this.employeService = employeService;
    }

    /** Lu par paiement-service a l'enregistrement d'un salaire (salaire de base, identite pour les copies figees). */
    @GetMapping("/{id}")
    public EmployeResponse get(@PathVariable Long id) {
        return employeService.getById(id);
    }

    /** Lu par paiement-service pour le suivi mensuel des salaires (ACTIF et EN_CONGE). */
    @GetMapping
    public List<EmployeResponse> listByStatuses(@RequestParam Set<StatutEmploye> statuses) {
        return employeService.listByStatuses(statuses);
    }

    /** Lu par pilotage-service : statistiques du module et tableau de bord. */
    @GetMapping("/counts")
    public EmployeCountsResponse counts() {
        return employeService.counts();
    }
}
