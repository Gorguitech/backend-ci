package com.daara.internatadmin.pilotage;

import com.daara.internatadmin.common.error.ServiceUnavailableException;
import org.slf4j.MDC;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Appels inter-services d'une meme requete, lances en parallele sur des threads virtuels.
 * <p>
 * Le contexte de la requete (jeton relaye par l'intercepteur Feign) et le MDC (X-Request-Id) sont recopies dans
 * chaque thread. L'ensemble des appels d'un lot partage une attente maximale ; au-dela, 503. Le premier appel en
 * echec fait echouer la requete avec sa propre exception (traduite en 503 ou en 400 par common) et les appels
 * restants sont annules : aucune valeur par defaut ne remplace jamais une donnee manquante.
 */
public class ParallelCalls {

    private final ExecutorService executor;
    private final Duration maxWait;

    public ParallelCalls(ExecutorService executor, Duration maxWait) {
        this.executor = executor;
        this.maxWait = maxWait;
    }

    public Batch batch() {
        return new Batch(System.nanoTime() + maxWait.toNanos());
    }

    public final class Batch {

        private final long deadline;
        private final List<Future<?>> futures = new ArrayList<>();

        private Batch(long deadline) {
            this.deadline = deadline;
        }

        /** Lance l'appel immediatement ; le resultat est attendu au premier get(). */
        public <T> Supplier<T> fork(Supplier<T> call) {
            RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
            Map<String, String> mdc = MDC.getCopyOfContextMap();
            Future<T> future = executor.submit(() -> {
                RequestContextHolder.setRequestAttributes(attributes);
                if (mdc != null) {
                    MDC.setContextMap(mdc);
                }
                try {
                    return call.get();
                } finally {
                    RequestContextHolder.resetRequestAttributes();
                    MDC.clear();
                }
            });
            futures.add(future);
            return () -> join(future);
        }

        private <T> T join(Future<T> future) {
            try {
                return future.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            } catch (ExecutionException e) {
                cancelAll();
                if (e.getCause() instanceof RuntimeException runtime) {
                    throw runtime;
                }
                if (e.getCause() instanceof Error error) {
                    throw error;
                }
                throw new ServiceUnavailableException(e.getCause());
            } catch (TimeoutException e) {
                cancelAll();
                throw new ServiceUnavailableException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                cancelAll();
                throw new ServiceUnavailableException(e);
            }
        }

        private void cancelAll() {
            futures.forEach(f -> f.cancel(true));
        }
    }
}
