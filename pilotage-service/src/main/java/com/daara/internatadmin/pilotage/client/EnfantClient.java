package com.daara.internatadmin.pilotage.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** enfant-service, endpoints internes ; toute defaillance est traduite en 503 par common. */
@FeignClient(name = "enfant-service", url = "${internatadmin.services.enfant}", path = "/internal/enfants")
public interface EnfantClient {

    @GetMapping("/counts")
    EnfantCountsView counts(@RequestParam("year") int year, @RequestParam("month") int month);
}
