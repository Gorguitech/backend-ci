package com.daara.internatadmin.common.support;

import com.daara.internatadmin.common.audit.Audited;
import org.springframework.stereotype.Service;

@Service
public class NonTransactionalAuditedService {

    @Audited(action = "PRINT_CARD", entityType = "Thing")
    public AuditedTestService.ThingResponse print() {
        return new AuditedTestService.ThingResponse(7L, "carte");
    }
}
