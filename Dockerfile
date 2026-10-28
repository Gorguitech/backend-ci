# syntax=docker/dockerfile:1.7
#
# Image d'un service du backend, choisi par l'argument SERVICE :
#   docker build --build-arg SERVICE=auth-service -t internatadmin-backend:auth-service-local .
#
# Etape 1 : compilation du module et de ses dependances (common) avec le wrapper Maven.
#           Les tests ne sont pas executes ici : ils passent avant, dans la chaine CI (./mvnw verify).
#           -DskipTests et non -Dmaven.test.skip : ce dernier supprime aussi le jar de test de common,
#           dont les services dependent (scope test), et la resolution des dependances echoue.
# Etape 2 : extraction du jar en couches (dependances, chargeur, SNAPSHOT, application).
# Etape 3 : image d'execution JRE 21, utilisateur non root, sans aucun secret (tout vient de l'environnement).

ARG BUILD_IMAGE=eclipse-temurin:21-jdk-alpine
ARG RUNTIME_IMAGE=eclipse-temurin:21-jre-alpine

FROM ${BUILD_IMAGE} AS build
ARG SERVICE
RUN test -n "$SERVICE" || { echo "Argument SERVICE obligatoire (par exemple auth-service)" >&2; exit 1; }
WORKDIR /src

# POM d'abord : la couche ne change que si les dependances changent
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY common/pom.xml common/
COPY audit-service/pom.xml audit-service/
COPY auth-service/pom.xml auth-service/
COPY api-gateway/pom.xml api-gateway/
COPY enfant-service/pom.xml enfant-service/
COPY personnel-service/pom.xml personnel-service/
COPY paiement-service/pom.xml paiement-service/
COPY comptabilite-service/pom.xml comptabilite-service/
COPY pilotage-service/pom.xml pilotage-service/

COPY common/src/ common/src/
COPY ${SERVICE}/src/ ${SERVICE}/src/

RUN --mount=type=cache,target=/root/.m2 \
    chmod +x mvnw \
    && ./mvnw -B -q -pl "${SERVICE}" -am package -DskipTests -Djacoco.skip=true \
    && cp "${SERVICE}/target/${SERVICE}-1.0.0-SNAPSHOT.jar" /app.jar

FROM ${BUILD_IMAGE} AS layers
COPY --from=build /app.jar /app.jar
# l'outil de migration de proprietes ne sert qu'en developpement : le build echoue s'il est dans le jar
RUN java -Djarmode=tools -jar /app.jar extract --layers --launcher --destination /extracted \
    && if find /extracted -name 'spring-boot-properties-migrator*' | grep -q .; then \
         echo "spring-boot-properties-migrator present dans le jar : a exclure du POM" >&2; exit 1; \
       fi

FROM ${RUNTIME_IMAGE}
ARG SERVICE
LABEL org.opencontainers.image.title="internatadmin-${SERVICE}" \
      org.opencontainers.image.description="InternatAdmin ${SERVICE}" \
      org.opencontainers.image.vendor="InternatAdmin"

RUN addgroup -S -g 10001 app \
    && adduser -S -D -H -u 10001 -G app app \
    && mkdir -p /data/logos \
    && chown app:app /data/logos

WORKDIR /app
# fichiers de l'application en lecture seule pour l'utilisateur app (proprietaire root)
COPY --from=layers /extracted/dependencies/ ./
COPY --from=layers /extracted/spring-boot-loader/ ./
COPY --from=layers /extracted/snapshot-dependencies/ ./
COPY --from=layers /extracted/application/ ./

ENV SERVER_PORT=8080 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k -XX:+ExitOnOutOfMemoryError"

USER 10001:10001
EXPOSE 8080

# start-period mesure : environ 160 s quand les 7 services demarrent ensemble (profil all), 10 a 20 s seuls
HEALTHCHECK --interval=15s --timeout=3s --start-period=180s --start-interval=2s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/health || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
