package com.daara.internatadmin.common.audit;

import com.daara.internatadmin.common.security.CurrentUser;
import com.daara.internatadmin.common.support.AuditedTestService;
import com.daara.internatadmin.common.support.NonTransactionalAuditedService;
import com.daara.internatadmin.common.support.TestJwts;
import com.daara.internatadmin.common.support.TestTransactionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.TransactionSystemException;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest
class AuditAspectTest {

    @DynamicPropertySource
    static void securityProperties(DynamicPropertyRegistry registry) {
        registry.add("internatadmin.security.jwt-public-key-b64", TestJwts::publicKeyB64);
        registry.add("internatadmin.security.internal-api-key", () -> "cle");
    }

    @MockitoBean
    AuditPublisher publisher;

    @Autowired
    AuditedTestService service;

    @Autowired
    NonTransactionalAuditedService nonTransactionalService;

    @Autowired
    TestTransactionManager transactionManager;

    private final AtomicInteger commitsSeenAtPublish = new AtomicInteger(-1);

    @BeforeEach
    void authenticate() {
        transactionManager.reset();
        CurrentUser user = new CurrentUser(5L, "awa.diop", "GESTIONNAIRE");
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_GESTIONNAIRE"))));
        doAnswer(invocation -> {
            commitsSeenAtPublish.set(transactionManager.commits.get());
            return null;
        }).when(publisher).publish(any());
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void succes_evenementPublieApresLeCommitAvecLesBonnesDonnees() {
        service.create("Moussa", "mot-de-passe-secret");

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(publisher).publish(captor.capture());
        AuditEvent event = captor.getValue();

        assertThat(commitsSeenAtPublish.get()).as("publication posterieure au commit").isEqualTo(1);
        assertThat(event.eventId()).isNotNull();
        assertThat(event.userId()).isEqualTo(5L);
        assertThat(event.username()).isEqualTo("awa.diop");
        assertThat(event.action()).isEqualTo("CREATE");
        assertThat(event.entityType()).isEqualTo("Thing");
        assertThat(event.entityId()).isEqualTo(42L);
        assertThat(event.details()).isEqualTo("ThingResponse[id=42, name=Moussa]");
        assertThat(event.actionDate()).isNotNull();
    }

    @Test
    void lesArgumentsDeLaMethodeNeSontJamaisAudites() {
        service.create("Moussa", "mot-de-passe-secret");

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(publisher).publish(captor.capture());
        assertThat(captor.getValue().toString()).doesNotContain("mot-de-passe-secret");
    }

    @Test
    void exceptionMetier_rienNestPublie() {
        assertThatThrownBy(() -> service.createThenFail()).isInstanceOf(IllegalArgumentException.class);
        verify(publisher, never()).publish(any());
        assertThat(transactionManager.rollbacks.get()).isEqualTo(1);
    }

    @Test
    void rollbackSansException_rienNestPublie() {
        service.createRollbackOnly();
        verify(publisher, never()).publish(any());
        assertThat(transactionManager.rollbacks.get()).isEqualTo(1);
    }

    @Test
    void echecDuCommit_rienNestPublie() {
        transactionManager.failNextCommit();
        assertThatThrownBy(() -> service.create("Moussa", "x")).isInstanceOf(TransactionSystemException.class);
        verify(publisher, never()).publish(any());
    }

    @Test
    void sansUtilisateurAuthentifie_rienNestPublie() {
        SecurityContextHolder.clearContext();
        service.create("Moussa", "x");
        verify(publisher, never()).publish(any());
    }

    @Test
    void retourSansId_entityIdNul() {
        service.withoutId();
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(publisher).publish(captor.capture());
        assertThat(captor.getValue().entityId()).isNull();
        assertThat(captor.getValue().details()).isEqualTo("texte");
    }

    @Test
    void retourNul_detailsEtEntityIdNuls() {
        service.nullResult();
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(publisher).publish(captor.capture());
        assertThat(captor.getValue().entityId()).isNull();
        assertThat(captor.getValue().details()).isNull();
    }

    @Test
    void methodeNonTransactionnelle_publicationImmediate() {
        nonTransactionalService.print();
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(publisher).publish(captor.capture());
        assertThat(captor.getValue().action()).isEqualTo("PRINT_CARD");
        assertThat(captor.getValue().entityId()).isEqualTo(7L);
    }
}
