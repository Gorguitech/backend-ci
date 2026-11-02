package com.daara.internatadmin.audit;

import com.daara.internatadmin.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Method;

@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditService auditService;

    @AfterReturning(pointcut = "@annotation(audited)", returning = "result")
    public void auditAfterSuccess(Audited audited, Object result) {
        CustomUserDetails currentUser = getCurrentUser();
        if (currentUser == null) {
            return;
        }

        Long entityId = extractId(result);
        Runnable record = () -> auditService.record(
                currentUser.getId(),
                currentUser.getUsername(),
                audited.action(),
                audited.entityType(),
                entityId,
                result != null ? result.toString() : null
        );

        // Dans une transaction, la trace n'est ecrite qu'apres le commit : une action annulee n'est jamais tracee
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    record.run();
                }
            });
        } else {
            record.run();
        }
    }

    private CustomUserDetails getCurrentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails userDetails) {
            return userDetails;
        }
        return null;
    }

    private Long extractId(Object result) {
        try {
            Method getId = result.getClass().getMethod("id");
            return (Long) getId.invoke(result);
        } catch (Exception e) {
            return null;
        }
    }
}