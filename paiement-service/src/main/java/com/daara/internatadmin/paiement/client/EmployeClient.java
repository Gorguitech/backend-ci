package com.daara.internatadmin.paiement.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/** personnel-service, endpoints internes ; memes regles d'erreur que {@link EnfantClient}. */
@FeignClient(name = "personnel-service", url = "${internatadmin.services.personnel}", path = "/internal/employes")
public interface EmployeClient {

    @GetMapping("/{id}")
    EmployeView get(@PathVariable("id") Long id);

    @GetMapping
    List<EmployeView> listByStatuses(@RequestParam("statuses") List<String> statuses);
}
