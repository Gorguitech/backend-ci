package com.daara.internatadmin.comptabilite.internal;

import com.daara.internatadmin.comptabilite.StatutMouvement;
import com.daara.internatadmin.comptabilite.depense.DepenseRepository;
import com.daara.internatadmin.comptabilite.recette.AutreRecetteRepository;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints inter-services (X-Internal-Key exige, jamais routes par la gateway). */
@Hidden
@RestController
@RequestMapping("/internal/comptabilite")
public class InternalComptabiliteController {

    private final AutreRecetteRepository autreRecetteRepository;
    private final DepenseRepository depenseRepository;

    public InternalComptabiliteController(AutreRecetteRepository autreRecetteRepository, DepenseRepository depenseRepository) {
        this.autreRecetteRepository = autreRecetteRepository;
        this.depenseRepository = depenseRepository;
    }

    @GetMapping("/totaux-globaux")
    public TotauxComptabiliteResponse totauxGlobaux() {
        return new TotauxComptabiliteResponse(
                autreRecetteRepository.sumByStatus(StatutMouvement.VALIDE),
                depenseRepository.sumByStatus(StatutMouvement.VALIDE));
    }
}
