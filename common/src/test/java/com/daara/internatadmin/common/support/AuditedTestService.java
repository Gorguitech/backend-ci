package com.daara.internatadmin.common.support;

import com.daara.internatadmin.common.audit.Audited;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

/** Service transactionnel de test, dont les methodes sont auditees comme celles d'un service metier. */
@Service
public class AuditedTestService {

    public record ThingResponse(Long id, String name) {
    }

    @Audited(action = "CREATE", entityType = "Thing")
    @Transactional
    public ThingResponse create(String name, String password) {
        return new ThingResponse(42L, name);
    }

    @Audited(action = "CREATE", entityType = "Thing")
    @Transactional
    public ThingResponse createThenFail() {
        throw new IllegalArgumentException("refus metier");
    }

    @Audited(action = "CREATE", entityType = "Thing")
    @Transactional
    public ThingResponse createRollbackOnly() {
        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        return new ThingResponse(43L, "annule");
    }

    @Audited(action = "PRINT", entityType = "Thing")
    @Transactional
    public String withoutId() {
        return "texte";
    }

    @Audited(action = "PRINT", entityType = "Thing")
    @Transactional
    public ThingResponse nullResult() {
        return null;
    }
}
