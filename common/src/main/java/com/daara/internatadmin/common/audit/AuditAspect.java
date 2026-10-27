package com.daara.internatadmin.common.audit;

import com.daara.internatadmin.common.security.CurrentUser;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Intercepte les methodes @Audited qui se terminent normalement. Si une transaction est en cours,
 * l'evenement n'est publie qu'apres son commit : une action annulee par rollback n'est jamais tracee.
 * Comme dans le monolithe, rien n'est trace sans utilisateur authentifie.
 */
@Aspect
@Order(Ordered.LOWEST_PRECEDENCE)
public class AuditAspect {

    private final AuditPublisher publisher;
    private final Clock clock;

    public AuditAspect(AuditPublisher publisher, Clock clock) {
        this.publisher = publisher;
        this.clock = clock;
    }

    @AfterReturning(pointcut = "@annotation(audited)", returning = "result")
    public void afterSuccess(Audited audited, Object result) {
        CurrentUser user = currentUser();
        if (user == null) {
            return;
        }

        AuditEvent event = new AuditEvent(
                UUID.randomUUID(),
                user.id(),
                user.username(),
                audited.action(),
                audited.entityType(),
                extractId(result),
                result != null ? result.toString() : null,
                LocalDateTime.now(clock));

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publisher.publish(event);
                }
            });
        } else {
            publisher.publish(event);
        }
    }

    private static CurrentUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CurrentUser user) {
            return user;
        }
        return null;
    }

    // Les reponses metier sont des records exposant id() : meme convention que le monolithe.
    private static Long extractId(Object result) {
        if (result == null) {
            return null;
        }
        try {
            Object id = result.getClass().getMethod("id").invoke(result);
            return (id instanceof Number number) ? number.longValue() : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
