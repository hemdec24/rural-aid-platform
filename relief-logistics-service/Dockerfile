FROM maven:3.9.16-eclipse-temurin-25-noble AS build

WORKDIR /workspace

COPY . .

RUN mvn \
    -pl relief-logistics-service \
    -am \
    clean package \
    -DskipTests


FROM eclipse-temurin:25-jre-noble AS runtime

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app \
    && useradd --system --gid app app

WORKDIR /app

COPY --from=build \
    /workspace/relief-logistics-service/target/relief-logistics-service-0.1.0-SNAPSHOT.jar \
    /app/application.jar

COPY --from=build \
    /workspace/relief-logistics-service/src/main/resources/relief-logistics-service.yml \
    /app/config.yml

RUN chown -R app:app /app

USER app

EXPOSE 8090 8091

HEALTHCHECK --interval=10s --timeout=3s --start-period=20s --retries=5 \
    CMD curl --fail --silent http://localhost:8091/healthcheck > /dev/null || exit 1

ENTRYPOINT ["java", "-jar", "/app/application.jar", "server", "/app/config.yml"]

