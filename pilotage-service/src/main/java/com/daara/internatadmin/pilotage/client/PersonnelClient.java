package com.daara.internatadmin.pilotage.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

/** personnel-service, endpoints internes ; toute defaillance est traduite en 503 par common. */
@FeignClient(name = "personnel-service", url = "${internatadmin.services.personnel}", path = "/internal/employes")
public interface PersonnelClient {

    @GetMapping("/counts")
    EmployeCountsView counts();
}
