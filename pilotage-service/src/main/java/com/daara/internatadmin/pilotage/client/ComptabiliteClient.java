package com.daara.internatadmin.pilotage.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

/** comptabilite-service, endpoints internes ; toute defaillance est traduite en 503 par common. */
@FeignClient(name = "comptabilite-service", url = "${internatadmin.services.comptabilite}", path = "/internal/comptabilite")
public interface ComptabiliteClient {

    @GetMapping("/totaux-globaux")
    TotauxComptabiliteView totauxGlobaux();
}
