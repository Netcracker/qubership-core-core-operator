FROM ghcr.io/netcracker/qubership-java-base:21-alpine-2.4.0@sha256:a7c78350fc6f7e24d64f91457c8e1c4bdd3395b033fcd13f97cdfd49c63a4c30
LABEL maintainer="qubership"

COPY --chown=10001:0 service/target/quarkus-app/lib/ /app/lib/
COPY --chown=10001:0 service/target/quarkus-app/*.jar /app/
COPY --chown=10001:0 service/target/quarkus-app/app/ /app/app/
COPY --chown=10001:0 service/target/quarkus-app/quarkus/ /app/quarkus/

EXPOSE 8080

WORKDIR /app

CMD ["/usr/bin/java", "-Xmx512m", "-jar", "/app/quarkus-run.jar"]
