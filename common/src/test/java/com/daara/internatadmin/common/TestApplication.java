package com.daara.internatadmin.common;

import com.daara.internatadmin.common.support.TestTransactionManager;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Application minimale qui charge les auto-configurations du module, comme le ferait un service.
 * Le scan est limite au package support : les classes du module ne sont chargees que par auto-configuration.
 */
@SpringBootApplication(scanBasePackages = "com.daara.internatadmin.common.support")
public class TestApplication {

    @Bean
    TestTransactionManager transactionManager() {
        return new TestTransactionManager();
    }
}
