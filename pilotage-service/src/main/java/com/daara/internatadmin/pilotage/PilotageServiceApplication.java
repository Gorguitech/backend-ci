package com.daara.internatadmin.pilotage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class PilotageServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PilotageServiceApplication.class, args);
    }
}
