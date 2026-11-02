package com.daara.internatadmin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class InternatAdminApplication {

	public static void main(String[] args) {
		SpringApplication.run(InternatAdminApplication.class, args);
	}

}
