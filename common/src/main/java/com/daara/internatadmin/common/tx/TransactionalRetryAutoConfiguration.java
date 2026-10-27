package com.daara.internatadmin.common.tx;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;

@AutoConfiguration(after = TransactionAutoConfiguration.class)
@ConditionalOnBean(PlatformTransactionManager.class)
public class TransactionalRetryAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    TransactionalRetry transactionalRetry(PlatformTransactionManager transactionManager) {
        return new TransactionalRetry(transactionManager);
    }
}
