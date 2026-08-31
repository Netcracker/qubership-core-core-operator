FROM ghcr.io/netcracker/qubership-java-base:21-alpine-2.4.1@sha256:3292f3d70adf9862e4c4a1d1d9a8a820543a31d14a48212c95d9194d2f1e626f
LABEL maintainer="qubership"

COPY --chown=10001:0 service/target/quarkus-app/lib/ /app/lib/
COPY --chown=10001:0 service/target/quarkus-app/*.jar /app/
COPY --chown=10001:0 service/target/quarkus-app/app/ /app/app/
COPY --chown=10001:0 service/target/quarkus-app/quarkus/ /app/quarkus/

EXPOSE 8080

WORKDIR /app

CMD ["/usr/bin/java", "-Xmx512m", "-jar", "/app/quarkus-run.jar"]
