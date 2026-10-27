package com.daara.internatadmin.comptabilite;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class ComptabiliteServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ComptabiliteServiceApplication.class, args);
    }
}
