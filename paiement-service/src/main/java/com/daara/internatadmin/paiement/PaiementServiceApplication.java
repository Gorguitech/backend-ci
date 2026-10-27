package com.daara.internatadmin.paiement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class PaiementServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaiementServiceApplication.class, args);
    }
}
