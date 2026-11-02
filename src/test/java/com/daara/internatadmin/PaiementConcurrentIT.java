package com.daara.internatadmin;

import com.daara.internatadmin.support.AbstractApiIT;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/** E2 : le verrou des mensualites, sur de vrais paiements simultanes. */
class PaiementConcurrentIT extends AbstractApiIT {

    private static final String PAIEMENT = """
            {"enfantId":%d,"paymentType":"MENSUALITE","paymentMethod":"ESPECES","amount":6000,
             "concernedYear":2026,"concernedMonth":%d,"confirmPartial":true}
            """;

    @Test
    void paiementsPartielsSimultanes_jamaisAuDelaDuDu() throws Exception {
        long id = createEnfant(2026, 10000).get("id").asLong();
        String token = adminToken();
        int threads = 4;
        CountDownLatch depart = new CountDownLatch(threads);

        List<Integer> statuts = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                Callable<Integer> payer = () -> {
                    depart.countDown();
                    depart.await();
                    return send("POST", "/api/paiements-enfants", token, PAIEMENT.formatted(id, 3)).statusCode();
                };
                futures.add(executor.submit(payer));
            }
            for (Future<Integer> future : futures) {
                statuts.add(future.get());
            }
        }

        // 6000 sur 10000 du : le premier passe, il ne reste que 4000 pour les suivants
        assertThat(statuts).containsOnly(201, 409);
        assertThat(statuts).filteredOn(s -> s == 201).hasSize(1);

        JsonNode historique = JSON.readTree(send("GET",
                "/api/paiements-enfants/enfant/" + id + "/mensualites", token, null).body());
        BigDecimal total = BigDecimal.ZERO;
        for (JsonNode paiement : historique) {
            total = total.add(paiement.get("amount").decimalValue());
        }
        assertThat(total).isEqualByComparingTo("6000");
    }

    @Test
    void verrouNonObtenuDansLeDelai_503EtJamais409() throws Exception {
        long id = createEnfant(2026, 10000).get("id").asLong();
        int monthKey = 2026 * 100 + 4;

        // verrou de session tenu par une autre connexion, le temps de l'appel
        try (Connection connection = DriverManager.getConnection(jdbcUrl(), jdbcUsername(), jdbcPassword());
             PreparedStatement lock = connection.prepareStatement("SELECT pg_advisory_lock(?, ?)")) {
            lock.setInt(1, Math.toIntExact(id));
            lock.setInt(2, monthKey);
            lock.execute();

            HttpResponse<String> response = send("POST", "/api/paiements-enfants", adminToken(), PAIEMENT.formatted(id, 4));

            assertThat(response.statusCode()).isEqualTo(503);
            assertThat(JSON.readTree(response.body()).get("error").asString()).isEqualTo("SERVICE_UNAVAILABLE");
        }

        // verrou libere : le meme paiement passe
        assertThat(send("POST", "/api/paiements-enfants", adminToken(), PAIEMENT.formatted(id, 4)).statusCode()).isEqualTo(201);
    }
}
