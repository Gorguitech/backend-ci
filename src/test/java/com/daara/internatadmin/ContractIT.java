package com.daara.internatadmin;

import com.daara.internatadmin.support.AbstractApiIT;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Contrat attendu par le frontend Angular, verifie de bout en bout. */
class ContractIT extends AbstractApiIT {

    private static final String STATUS_PATH = "/api/enfants/%d/status";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void sansJeton_403ACorpsVide() throws Exception {
        HttpResponse<String> response = send("GET", "/api/enfants", null, null);

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).isEmpty();
    }

    @Test
    void jetonInvalide_401InvalidToken() throws Exception {
        var key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(JWT_SECRET));
        Date past = new Date(System.currentTimeMillis() - 60_000);
        String expire = Jwts.builder().subject("admin").issuedAt(past).expiration(past).signWith(key).compact();
        String autreCle = Jwts.builder().subject("admin").expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(
                        Base64.getEncoder().encodeToString((UUID.randomUUID() + "-" + UUID.randomUUID()).getBytes()))))
                .compact();

        for (String token : new String[]{expire, autreCle, "mal.forme.jeton", "abc"}) {
            HttpResponse<String> response = send("GET", "/api/enfants", token, null);
            assertThat(response.statusCode()).as(token).isEqualTo(401);
            JsonNode body = JSON.readTree(response.body());
            assertThat(body.get("error").asString()).isEqualTo("INVALID_TOKEN");
            assertThat(body.get("message").asString()).isEqualTo("Jeton invalide ou expiré.");
        }
    }

    // Sans en-tete CORS, le navigateur masque le 401 et le frontend ne deconnecte jamais l'utilisateur
    @Test
    void jetonInvalide_reponseLisibleParLeNavigateur() throws Exception {
        HttpRequest request = request("/api/enfants", "abc").header("Origin", "http://localhost:4200").GET().build();

        HttpResponse<String> response = client().send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).contains("http://localhost:4200");
    }

    @Test
    void routesPubliques_ignorentUnJetonInvalide() throws Exception {
        HttpResponse<String> response = send("POST", "/api/auth/login", "abc",
                "{\"username\":\"inconnu\",\"password\":\"inconnu\"}");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(JSON.readTree(response.body()).get("message").asString())
                .isEqualTo("Identifiant ou mot de passe incorrect.");
    }

    @Test
    void connexionInitiale_claimsLusParLeFrontendEtChangementObligatoire() throws Exception {
        adminToken();
        HttpResponse<String> created = send("POST", "/api/users", adminToken(),
                "{\"firstName\":\"Moussa\",\"lastName\":\"Fall" + UUID.randomUUID().toString().substring(0, 6) + "\"}");
        assertThat(created.statusCode()).isEqualTo(201);
        JsonNode user = JSON.readTree(created.body());

        JsonNode login = login(user.get("username").asString(), user.get("temporaryPassword").asString());

        assertThat(login.get("mustChangePassword").asBoolean()).isTrue();
        String token = login.get("token").asString();
        JsonNode claims = JSON.readTree(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
        assertThat(claims.propertyNames()).contains("sub", "userId", "role", "mustChangePassword", "exp");
        assertThat(claims.get("role").asString()).isEqualTo("GESTIONNAIRE");
        assertThat(claims.get("mustChangePassword").asBoolean()).isTrue();

        HttpResponse<String> bloque = send("GET", "/api/enfants", token, null);
        assertThat(bloque.statusCode()).isEqualTo(403);
        assertThat(JSON.readTree(bloque.body()).get("error").asString()).isEqualTo("MUST_CHANGE_PASSWORD");
    }

    /** Effet de Jackson 3 : les dates restent des chaines ISO, a l'aller comme au retour. */
    @Test
    void eleveEtPaiement_datesEtMontantsDuContrat() throws Exception {
        JsonNode enfant = createEnfant(2026, 10000);
        long id = enfant.get("id").asLong();

        assertThat(enfant.get("birthDate").isString()).isTrue();
        assertThat(enfant.get("birthDate").asString()).isEqualTo("2015-03-20");
        assertThat(enfant.get("entryDate").asString()).isEqualTo("2026-01-15");
        assertThat(enfant.get("matricule").asString()).startsWith("ENF-2026-");
        assertThat(enfant.get("status").asString()).isEqualTo("ACTIF");
        assertThat(enfant.get("monthlyFee").isNumber()).isTrue();

        JsonNode relu = JSON.readTree(send("GET", "/api/enfants/" + id, adminToken(), null).body());
        assertThat(relu.get("birthDate").asString()).isEqualTo("2015-03-20");

        // confirmPartial absent, comme dans le modele Angular (champ optionnel)
        HttpResponse<String> nonConfirme = send("POST", "/api/paiements-enfants", adminToken(), """
                {"enfantId":%d,"paymentType":"MENSUALITE","paymentMethod":"ESPECES","amount":4000,
                 "concernedYear":2026,"concernedMonth":2,"paymentDate":"2026-02-03"}
                """.formatted(id));
        assertThat(nonConfirme.statusCode()).isEqualTo(409);
        assertThat(JSON.readTree(nonConfirme.body()).get("error").asString()).isEqualTo("PARTIAL_PAYMENT_NOT_CONFIRMED");

        HttpResponse<String> paye = send("POST", "/api/paiements-enfants", adminToken(), """
                {"enfantId":%d,"paymentType":"MENSUALITE","paymentMethod":"ESPECES","amount":4000,
                 "concernedYear":2026,"concernedMonth":2,"paymentDate":"2026-02-03","confirmPartial":true}
                """.formatted(id));
        assertThat(paye.statusCode()).isEqualTo(201);
        JsonNode paiement = JSON.readTree(paye.body());
        assertThat(paiement.get("paymentDate").asString()).isEqualTo("2026-02-03");
        assertThat(paiement.get("concernedMonth").asString()).isEqualTo("2026-02-01");
        assertThat(paiement.get("amount").decimalValue()).isEqualByComparingTo("4000");
        assertThat(paiement.get("balanceAfter").decimalValue()).isEqualByComparingTo("6000");
        assertThat(paiement.get("isPartial").asBoolean()).isTrue();
        assertThat(paiement.get("status").asString()).isEqualTo("VALIDE");

        JsonNode historique = JSON.readTree(send("GET",
                "/api/paiements-enfants/enfant/" + id + "/mensualites", adminToken(), null).body());
        assertThat(historique.size()).isEqualTo(1);
        assertThat(historique.get(0).get("paymentDate").asString()).isEqualTo("2026-02-03");

        HttpResponse<String> dateInvalide = send("POST", "/api/paiements-enfants", adminToken(), """
                {"enfantId":%d,"paymentType":"MENSUALITE","paymentMethod":"ESPECES","amount":1000,
                 "concernedYear":2026,"concernedMonth":2,"paymentDate":"03/02/2026","confirmPartial":true}
                """.formatted(id));
        assertThat(dateInvalide.statusCode()).isEqualTo(400);
    }

    @Test
    void doublon_409PossibleDuplicate() throws Exception {
        JsonNode enfant = createEnfant(2026, 10000);
        String body = """
                {"firstName":"Awa","lastName":"%s","birthDate":"2015-03-20","gender":"F","guardianName":"Fatou",
                 "guardianPhone":"%s","entryDate":"2026-01-15","registrationFee":5000,"monthlyFee":10000}
                """.formatted(enfant.get("lastName").asString(), enfant.get("guardianPhone").asString());

        HttpResponse<String> response = send("POST", "/api/enfants", adminToken(), body);

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(JSON.readTree(response.body()).get("error").asString()).isEqualTo("POSSIBLE_DUPLICATE");
        assertThat(send("POST", "/api/enfants?confirmDuplicate=true", adminToken(), body).statusCode()).isEqualTo(201);
    }

    /** E1 : la collision est provoquee, la creation doit reussir au numero suivant. */
    @Test
    void collisionDeMatricule_nouvelEssaiReussi() throws Exception {
        long id = createEnfant(2031, 10000).get("id").asLong();
        jdbcTemplate.update("UPDATE enfants SET matricule = 'ENF-2031-0002' WHERE id = ?", id);

        // une ligne pour ce prefixe : le numero 2 est tente en premier et il est deja pris
        assertThat(createEnfant(2031, 10000).get("matricule").asString()).isEqualTo("ENF-2031-0003");
    }

    /** E4 et E5, puis pagination de l'audit au premier niveau et trace ecrite apres le commit (E3). */
    @Test
    void changementDeStatut_valideEtAudite() throws Exception {
        long id = createEnfant(2026, 10000).get("id").asLong();
        String path = STATUS_PATH.formatted(id);

        assertThat(send("PATCH", path, adminToken(), "{}").statusCode()).isEqualTo(400);
        assertThat(send("PATCH", path, adminToken(), "{\"status\":\"INCONNU\"}").statusCode()).isEqualTo(400);

        HttpResponse<String> ok = send("PATCH", path, adminToken(), "{\"status\":\"SORTI\"}");
        assertThat(ok.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(ok.body()).get("status").asString()).isEqualTo("SORTI");

        String auditPath = "/api/audit?entityType=Enfant&entityId=" + id + "&size=5";
        await().atMost(Duration.ofSeconds(10)).until(() -> JSON.readTree(send("GET", auditPath, adminToken(), null).body())
                .get("totalElements").asInt() == 2);

        JsonNode page = JSON.readTree(send("GET", auditPath, adminToken(), null).body());
        assertThat(page.propertyNames()).contains("content", "totalPages", "totalElements", "size", "number",
                "numberOfElements", "first", "last", "empty");
        assertThat(page.get("size").asInt()).isEqualTo(5);
        assertThat(page.get("number").asInt()).isZero();
        JsonNode derniere = page.get("content").get(0);
        assertThat(derniere.get("action").asString()).isEqualTo("UPDATE_STATUS");
        assertThat(derniere.get("actionDate").asString()).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*");
        assertThat(page.get("content").get(1).get("action").asString()).isEqualTo("CREATE");
    }

    @Test
    void logo_envoiMultipartEtLimiteDeTaille() throws Exception {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};
        HttpResponse<String> envoi = uploadLogo(png);
        assertThat(envoi.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(envoi.body()).get("hasLogo").asBoolean()).isTrue();

        HttpResponse<byte[]> lu = client().send(request("/api/parametres/logo", adminToken()).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(lu.statusCode()).isEqualTo(200);
        assertThat(lu.body()).isEqualTo(png);
        assertThat(lu.headers().firstValue("Content-Type")).contains("image/png");

        HttpResponse<String> tropGros = uploadLogo(new byte[4 * 1024 * 1024]);
        assertThat(tropGros.statusCode()).isEqualTo(413);
        assertThat(JSON.readTree(tropGros.body()).get("error").asString()).isEqualTo("FILE_TOO_LARGE");
    }

    private HttpResponse<String> uploadLogo(byte[] content) throws Exception {
        String boundary = "frontiere";
        byte[] head = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"logo.png\"\r\n"
                + "Content-Type: image/png\r\n\r\n").getBytes();
        byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes();
        byte[] body = Arrays.copyOf(head, head.length + content.length + tail.length);
        System.arraycopy(content, 0, body, head.length, content.length);
        System.arraycopy(tail, 0, body, head.length + content.length, tail.length);

        HttpRequest request = request("/api/parametres/logo", adminToken())
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        return client().send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void healthEtSwagger_accessiblesSansJeton() throws Exception {
        HttpResponse<String> health = send("GET", "/health", null, null);
        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(health.body()).get("status").asString()).isEqualTo("UP");

        assertThat(send("GET", "/swagger-ui/index.html", null, null).statusCode()).isEqualTo(200);
        // version surchargee dans le POM : le bundle au DOMPurify corrige doit rester servi
        HttpResponse<String> bundle = send("GET", "/swagger-ui/swagger-ui-bundle.js", null, null);
        assertThat(bundle.statusCode()).isEqualTo(200);
        assertThat(bundle.body()).contains("version=\"3.4.17\"");
        HttpResponse<String> docs = send("GET", "/v3/api-docs", null, null);
        assertThat(docs.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(docs.body()).get("paths").propertyNames()).contains("/api/auth/login", "/api/enfants");
    }

    @Test
    void endpointsDAdministrationNonExposes() throws Exception {
        assertThat(send("GET", "/env", adminToken(), null).statusCode()).isIn(403, 404);
        assertThat(send("GET", "/actuator/env", adminToken(), null).statusCode()).isIn(403, 404);
    }
}
