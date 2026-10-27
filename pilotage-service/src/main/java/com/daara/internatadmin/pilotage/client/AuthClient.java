package com.daara.internatadmin.pilotage.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

/** auth-service, endpoints internes ; toute defaillance est traduite en 503 par common. */
@FeignClient(name = "auth-service", url = "${internatadmin.services.auth}", path = "/internal/users")
public interface AuthClient {

    @GetMapping("/stats")
    UserStatsView stats();
}
