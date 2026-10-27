package com.daara.internatadmin.pilotage.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** paiement-service, endpoints internes ; toute defaillance est traduite en 503 par common. */
@FeignClient(name = "paiement-service", url = "${internatadmin.services.paiement}", path = "/internal/paiements")
public interface PaiementClient {

    @GetMapping("/suivi-resume")
    SuiviResumeView suiviResume(@RequestParam("year") int year, @RequestParam("month") int month);

    @GetMapping("/totaux-globaux")
    TotauxPaiementsView totauxGlobaux();
}
