# syntax=docker/dockerfile:1.7
#
# Image du backend monolithique :
#   docker build -t internatadmin-monolithe:local .
#
# Etape 1 : compilation avec le wrapper Maven. Les tests ne sont pas executes ici : ils passent avant,
#           dans la chaine CI (./mvnw verify).
# Etape 2 : extraction du jar en couches (dependances, chargeur, SNAPSHOT, application).
# Etape 3 : image d'execution JRE 21, utilisateur non root, sans aucun secret (tout vient de l'environnement).

ARG BUILD_IMAGE=eclipse-temurin:21-jdk-alpine
ARG RUNTIME_IMAGE=eclipse-temurin:21-jre-alpine

FROM ${BUILD_IMAGE} AS build
WORKDIR /src

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY src/main/ src/main/

RUN --mount=type=cache,target=/root/.m2 \
    chmod +x mvnw \
    && ./mvnw -B -q package -DskipTests -Djacoco.skip=true \
    && cp target/internatadmin-monolithe-1.0.0-SNAPSHOT.jar /app.jar

FROM ${BUILD_IMAGE} AS layers
COPY --from=build /app.jar /app.jar
RUN java -Djarmode=tools -jar /app.jar extract --layers --launcher --destination /extracted

FROM ${RUNTIME_IMAGE}
LABEL org.opencontainers.image.title="internatadmin-monolithe" \
      org.opencontainers.image.description="InternatAdmin, backend monolithique" \
      org.opencontainers.image.vendor="InternatAdmin"

# libexpat >= 2.10.0-r0 : corrige CVE-2026-117610 et CVE-2026-118370, absent de l'image de base
RUN apk add --no-cache --upgrade 'libexpat>=2.10.0-r0' \
    && addgroup -S -g 10001 app \
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
    LOGO_DIR=/data/logos \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k -XX:+ExitOnOutOfMemoryError"

USER 10001:10001
EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=3s --start-period=180s --start-interval=2s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/health || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
