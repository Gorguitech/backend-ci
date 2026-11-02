package com.daara.internatadmin.audit;

import com.daara.internatadmin.security.CustomUserDetails;
import com.daara.internatadmin.user.Role;
import com.daara.internatadmin.user.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** E3 : pas de trace d'audit tant que la transaction n'est pas validee. */
class AuditAspectTest {

    record Result(Long id) {}

    private final AuditService auditService = mock(AuditService.class);
    private final AuditAspect aspect = new AuditAspect(auditService);

    @Audited(action = "UPDATE_STATUS", entityType = "Enfant")
    void auditedMethod() {
    }

    private Audited audited() throws NoSuchMethodException {
        return AuditAspectTest.class.getDeclaredMethod("auditedMethod").getAnnotation(Audited.class);
    }

    @BeforeEach
    void authenticate() {
        User user = new User();
        user.setId(7L);
        user.setUsername("admin");
        user.setRole(Role.ADMIN);
        CustomUserDetails details = new CustomUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        TransactionSynchronizationManager.clear();
    }

    @Test
    void dansUneTransaction_traceEcriteSeulementApresLeCommit() throws Exception {
        TransactionSynchronizationManager.initSynchronization();

        aspect.auditAfterSuccess(audited(), new Result(42L));
        verify(auditService, never()).record(any(), any(), any(), any(), any(), any());

        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        synchronizations.forEach(TransactionSynchronization::afterCommit);

        verify(auditService).record(eq(7L), eq("admin"), eq("UPDATE_STATUS"), eq("Enfant"), eq(42L), any());
    }

    @Test
    void transactionAnnulee_aucuneTrace() throws Exception {
        TransactionSynchronizationManager.initSynchronization();

        aspect.auditAfterSuccess(audited(), new Result(42L));
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(auditService, never()).record(any(), any(), any(), any(), any(), any());
    }
}
