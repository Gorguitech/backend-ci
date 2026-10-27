package com.daara.internatadmin.paiement.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * enfant-service, endpoints internes. Un eleve inconnu renvoie 400 INVALID_REQUEST, relance ici en
 * IllegalArgumentException avec le meme message ; toute autre defaillance devient un 503.
 */
@FeignClient(name = "enfant-service", url = "${internatadmin.services.enfant}", path = "/internal/enfants")
public interface EnfantClient {

    @GetMapping("/{id}")
    EnfantView get(@PathVariable("id") Long id);

    @GetMapping
    List<EnfantView> listByStatuses(@RequestParam("statuses") List<String> statuses);
}
