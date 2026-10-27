package com.daara.internatadmin.common.support;

import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Gestionnaire de transactions sans base de donnees : il applique la vraie mecanique de Spring
 * (synchronisations, afterCommit, rollback-only) et compte les debuts, commits et rollbacks.
 */
public class TestTransactionManager extends AbstractPlatformTransactionManager {

    public final AtomicInteger begins = new AtomicInteger();
    public final AtomicInteger commits = new AtomicInteger();
    public final AtomicInteger rollbacks = new AtomicInteger();
    private volatile boolean failNextCommit;

    public void failNextCommit() {
        this.failNextCommit = true;
    }

    public void reset() {
        begins.set(0);
        commits.set(0);
        rollbacks.set(0);
        failNextCommit = false;
    }

    @Override
    protected Object doGetTransaction() {
        return new Object();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        begins.incrementAndGet();
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
        if (failNextCommit) {
            failNextCommit = false;
            throw new TransactionSystemException("Echec simule du commit");
        }
        commits.incrementAndGet();
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
        rollbacks.incrementAndGet();
    }
}
