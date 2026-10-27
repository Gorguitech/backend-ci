package com.daara.internatadmin.pilotage;

import com.daara.internatadmin.common.error.ServiceUnavailableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParallelCallsTest {

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @AfterEach
    void close() {
        RequestContextHolder.resetRequestAttributes();
        MDC.clear();
        executor.close();
    }

    @Test
    void appelsSimultanesAvecContexteDeLaRequeteEtMdc() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer jeton");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        MDC.put("requestId", "req-42");
        CountDownLatch bothStarted = new CountDownLatch(2);

        ParallelCalls.Batch batch = new ParallelCalls(executor, Duration.ofSeconds(5)).batch();
        Supplier<String> first = batch.fork(() -> awaitOther(bothStarted) + context());
        Supplier<String> second = batch.fork(() -> awaitOther(bothStarted) + context());

        // chaque appel attend que l'autre ait demarre : ils ne peuvent reussir que s'ils s'executent en meme temps
        assertThat(first.get()).isEqualTo("ok Bearer jeton req-42");
        assertThat(second.get()).isEqualTo("ok Bearer jeton req-42");
    }

    @Test
    void exceptionDeLAppelRelanceeTelleQuelle() {
        ParallelCalls.Batch batch = new ParallelCalls(executor, Duration.ofSeconds(5)).batch();
        Supplier<String> call = batch.fork(() -> {
            throw new IllegalArgumentException("Enfant introuvable : 3");
        });

        assertThatThrownBy(call::get).isInstanceOf(IllegalArgumentException.class).hasMessage("Enfant introuvable : 3");
    }

    @Test
    void erreurNonControleeRelancee() {
        ParallelCalls.Batch batch = new ParallelCalls(executor, Duration.ofSeconds(5)).batch();
        Supplier<String> call = batch.fork(() -> {
            throw new StackOverflowError();
        });

        assertThatThrownBy(call::get).isInstanceOf(StackOverflowError.class);
    }

    @Test
    void attenteMaximaleDepassee_503EtAppelsRestantsAnnules() throws Exception {
        CountDownLatch interrupted = new CountDownLatch(1);
        ParallelCalls.Batch batch = new ParallelCalls(executor, Duration.ofMillis(200)).batch();
        Supplier<String> slow = batch.fork(() -> sleep(10_000));
        batch.fork(() -> {
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                interrupted.countDown();
            }
            return "jamais";
        });

        long start = System.nanoTime();
        assertThatThrownBy(slow::get).isInstanceOf(ServiceUnavailableException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
        assertThat(interrupted.await(2, TimeUnit.SECONDS)).as("appel restant annule").isTrue();
    }

    @Test
    void threadAppelantInterrompu_503() {
        ParallelCalls.Batch batch = new ParallelCalls(executor, Duration.ofSeconds(5)).batch();
        Supplier<String> slow = batch.fork(() -> sleep(10_000));

        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(slow::get).isInstanceOf(ServiceUnavailableException.class);
            assertThat(Thread.interrupted()).as("indicateur d'interruption conserve").isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    private static String awaitOther(CountDownLatch latch) {
        latch.countDown();
        try {
            return latch.await(2, TimeUnit.SECONDS) ? "ok " : "sequentiel ";
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String context() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes.getRequest().getHeader("Authorization") + " " + MDC.get("requestId");
    }

    private static String sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "fini";
    }
}
