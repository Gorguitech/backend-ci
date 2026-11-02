# InternatAdmin, backend monolithique

Backend de gestion administrative et financiere d'un Daara : eleves, personnel, paiements, depenses et recettes,
tresorerie, tableau de bord, utilisateurs, audit.

Il reprend le monolithe d'origine en appliquant les corrections E1 a E6 et E9 identifiees sur la version
microservices (branche `microservices-v1`). Le contrat d'API du frontend Angular est conserve.

Pile : Java 21, Spring Boot 4.1, PostgreSQL, Flyway, Spring Security (JWT HS256), springdoc (Swagger).

## Demarrage

Prerequis : JDK 21 et Docker (pour PostgreSQL et les tests d'integration).

```bash
cp .env.example .env
# Renseigner dans .env :
#   DB_PASSWORD                  mot de passe de la base
#   JWT_SECRET                   openssl rand -base64 32
#   ADMIN_INITIAL_PASSWORD_HASH  ./scripts/hash-admin-password.sh (valeur entre apostrophes simples)

set -a; . ./.env; set +a

docker run -d --name internatadmin-db -p 127.0.0.1:5432:5432 \
  -e POSTGRES_DB=internatadmin -e POSTGRES_USER="$DB_USERNAME" -e POSTGRES_PASSWORD="$DB_PASSWORD" \
  postgres:17-alpine

./mvnw spring-boot:run
```

| URL | Role |
|---|---|
| `http://localhost:8080/api` | API appelee par le frontend |
| `http://localhost:8080/health` | Etat de l'application et de la base, sans jeton |
| `http://localhost:8080/swagger-ui.html` | Documentation Swagger, sans jeton |
| `http://localhost:8080/v3/api-docs` | Document OpenAPI |

Au premier demarrage, le compte `admin` est cree avec le mot de passe correspondant a
`ADMIN_INITIAL_PASSWORD_HASH` ; son changement est force a la premiere connexion.

## Configuration

Aucun secret n'est versionne. Toute la configuration passe par l'environnement (modele : `.env.example`).

| Variable | Obligatoire | Role |
|---|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | oui | Connexion PostgreSQL |
| `JWT_SECRET` | oui | Cle HMAC en base64, 256 bits minimum |
| `ADMIN_INITIAL_PASSWORD_HASH` | tant qu'aucun compte ADMIN n'existe | Hash BCrypt du mot de passe initial |
| `JWT_EXPIRATION_MINUTES` | non (480) | Duree de vie du jeton |
| `CORS_ALLOWED_ORIGINS` | non (`http://localhost:4200`) | Origines autorisees, separees par des virgules |
| `LOGO_DIR` | non (`uploads/logos`) | Dossier du logo |
| `SERVER_PORT` | non (8080) | Port HTTP |
| `PAIEMENT_LOCK_TIMEOUT` | non (5s) | Attente maximale du verrou d'une mensualite |

Le demarrage echoue si `JWT_SECRET` est absent ou trop court, ou si aucun compte ADMIN n'existe et que le hash est
absent ou invalide.

## Tests

```bash
./mvnw test     # tests unitaires
./mvnw verify   # tests unitaires et d'integration (Docker requis)
```

Le rapport de couverture est produit dans `target/site/jacoco`. Les tests sont volontairement peu nombreux :

| Classe | Type | Objet |
|---|---|---|
| `PaiementEnfantServiceTest` | unitaire | Regles de paiement : solde, partiel, depassement, inscription, ordre du verrou |
| `TransactionalRetryTest` | unitaire | E1 : une transaction par tentative, erreur technique apres la derniere |
| `AuditAspectTest` | unitaire | E3 : aucune trace avant le commit ni apres un rollback |
| `ContractIT` | integration | Contrat du frontend en HTTP reel sur PostgreSQL |
| `PaiementConcurrentIT` | integration | E2 : paiements simultanes et delai du verrou |

## Contrat avec le frontend

Les routes, les DTO et les messages d'erreur sont ceux du monolithe. Points sur lesquels le frontend s'appuie :

- Sans jeton : 403 a corps vide. Jeton expire, mal forme ou mal signe : 401 `INVALID_TOKEN`. Le frontend deconnecte
  l'utilisateur sur tout 401 : aucun autre cas n'en emet.
- Le frontend lit dans le jeton les claims `sub`, `userId`, `role`, `mustChangePassword` et `exp`.
- Changement de mot de passe obligatoire : 403 `MUST_CHANGE_PASSWORD`.
- `POST /api/enfants` : tout 409 est lu comme un doublon (`POSSIBLE_DUPLICATE`).
- Paiement d'une mensualite : `AMOUNT_MISMATCH` et `PARTIAL_PAYMENT_NOT_CONFIRMED` restent des 409.
- `/api/audit` et `/api/audit/me` : les champs de pagination (`content`, `totalPages`, `totalElements`, `size`,
  `number`, `numberOfElements`, `first`, `last`, `empty`) sont au premier niveau.
- Dates : chaines ISO (`2026-02-03`).
- `spring.jackson.use-jackson2-defaults` garde les valeurs par defaut de Jackson 2. Il est indispensable : sans lui,
  un paiement sans `confirmPartial` (champ optionnel du modele Angular) recoit 400 au lieu du 409 attendu par le
  formulaire de paiement partiel (verifie par `ContractIT`).
- Envoi du logo en multipart ; au-dela de 3 Mo : 413 `FILE_TOO_LARGE`.

## Ecarts corriges par rapport au monolithe

| Ecart | Constat dans le monolithe | Correction |
|---|---|---|
| E1 | Retry sur collision de matricule ou de username dans une meme transaction, qui echoue | Une transaction par tentative ; apres la derniere, 500, jamais 409 |
| E2 | Index unique des mensualites supprime : deux paiements partiels concurrents peuvent depasser le du | `pg_advisory_xact_lock` par eleve et par mois ; verrou non obtenu dans le delai : 503, jamais 409 |
| E3 | Une trace d'audit peut etre ecrite meme si le commit echoue | Ecriture apres le commit |
| E4 | `EnfantService.updateStatus` audite avec l'action `CREATE` | `UPDATE_STATUS` |
| E5 | `PATCH /api/enfants/{id}/status` sans `@Valid` | `@Valid`, 400 |
| E6 | Jeton expire ou mal forme : 500 | 401 `INVALID_TOKEN` |
| E9 | Journaux dans `/var/log/gestion-daara`, niveau de log sur un mauvais package | Journaux sur la sortie standard, sans fichier |

Autres changements :

- La migration `V2__seed_admin.sql` (hash du mot de passe admin) est retiree : le compte est cree au demarrage. Une base
  deja migree par l'ancien monolithe n'est donc pas reprise telle quelle : ce projet part d'une base neuve.
- `application.properties`, qui contenait des secrets, n'est pas repris. Ces secrets restent dans l'historique Git du
  monolithe : ne pas les reutiliser.
- Sans jeton, le corps du 403 est desormais vide.
- Le paquet devient `com.daara.internatadmin` ; `/health` est ajoute.
- swagger-ui est force en 5.34.1 dans `pom.xml` : la version apportee par springdoc (5.34.0) embarque un DOMPurify
  vulnerable. Surcharge a retirer des qu'une version de springdoc l'apporte.

## Limites connues

- Audit (E3) : la trace est ecrite apres le commit, en base, sans file de messages. Si l'application s'arrete entre le
  commit et l'ecriture, cette trace est perdue.
- `reset-with-recovery-code` revele si un utilisateur existe et ne verifie pas `active` : comportement du monolithe,
  conserve pour ne pas changer le contrat (E13 dans la branche `microservices-v1`).
- Les tests couvrent les regles critiques et le contrat, pas tous les modules (depenses, recettes, salaires, tresorerie).

## Image Docker

```bash
docker build -t internatadmin-monolithe:local .

docker run -d --name internatadmin -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://<hote>:5432/internatadmin \
  -e DB_USERNAME -e DB_PASSWORD -e JWT_SECRET -e ADMIN_INITIAL_PASSWORD_HASH \
  -v internatadmin-logos:/data/logos \
  internatadmin-monolithe:local
```

Image multi-etapes, JRE 21, utilisateur non root, application en lecture seule, sonde `/health`, aucun secret embarque.

## Integration continue

Le `Jenkinsfile` reprend le pipeline des microservices, reduit a une seule application :
build et tests unitaires, tests d'integration, OWASP Dependency-Check, SonarQube (optionnel), image, Trivy, Docker Hub.

- Sans changement du code depuis le dernier build reussi (`src/`, `pom.xml`, `Dockerfile`, `.dockerignore`, `.mvn/`,
  `mvnw`), tout est saute apres le checkout. Le parametre `BUILD_ALL` force le build complet. En particulier, modifier
  seulement `.trivyignore` ou le README ne relance aucune analyse.
- Trivy analyse les vulnerabilites uniquement (`--scanners vuln`) et bloque sur les CRITICAL ; les exceptions vont dans
  `.trivyignore`, avec justification et date d'expiration.
- OWASP Dependency-Check lit les fichiers annuels de NVD (`nvdDatafeedUrl`) ; sa base reste en cache sur l'hote, dans
  `~/.cache/internatadmin-ci/dependency-check-data`. Le premier telechargement est long, les suivants sont incrementaux.
- Publication : `abdoulaye940/daara-backend`, tags `<commit>`, `1.<build>` et `latest`, depuis `main` seulement.
- `TRIGGER_DEPLOY` (desactive par defaut) lance `DEPLOY_JOB` apres le push, avec `BACKEND_SERVICES=backend` et
  `BACKEND_TAG=<commit>`. Ce job doit savoir deployer cette image unique, dont les tags ne portent pas de nom de service.

Credentials Jenkins attendus : `dockerhub-credentials`, `nvd-api-key` (si `RUN_DEPCHECK`), `sonar-token` (si `RUN_SONAR`).
