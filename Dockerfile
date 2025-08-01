# Multi-stage Dockerfile for Quarkus application
# Stage 1: Build the Quarkus application
FROM maven:3.9.10-eclipse-temurin-21-alpine AS build

# Set working directory
WORKDIR /app

# Copy the parent pom.xml first for better layer caching
COPY pom.xml ./

# Copy the service module pom.xml
COPY service/pom.xml ./service/

RUN --mount=type=secret,id=github-username \
    --mount=type=secret,id=github-token \
    --mount=type=cache,target=/root/.m2 \
    mkdir -p /root/.m2 && \
    echo "<settings>\
      <servers>\
        <server>\
          <id>github</id>\
          <username>$(cat /run/secrets/github-username)</username>\
          <password>$(cat /run/secrets/github-token)</password>\
        </server>\
      </servers>\
    </settings>" > /root/.m2/settings.xml && \
    mvn dependency:go-offline -B -q

# Copy source code
COPY service/src ./service/src

# Build the application
RUN --mount=type=cache,target=/root/.m2 \
    mvn clean package -f service/pom.xml -DskipTests -B -q

#---------------------------------------------------------
# Stage 2: Runtime image
FROM ghcr.io/netcracker/qubership/java-base:1.0.0
LABEL maintainer="qubership"

# Set working directory
WORKDIR /app

# Copy the built application from the build stage
COPY --from=build --chown=10001:0 /app/service/target/quarkus-app/lib/ /app/lib/
COPY --from=build --chown=10001:0 /app/service/target/quarkus-app/*.jar /app/
COPY --from=build --chown=10001:0 /app/service/target/quarkus-app/app/ /app/app/
COPY --from=build --chown=10001:0 /app/service/target/quarkus-app/quarkus/ /app/quarkus/

# Switch to non-root user
USER 10001:0

EXPOSE 8080

WORKDIR /app
USER 10001:10001

CMD ["/usr/bin/java", "-Xmx512m", "-jar", "/app/quarkus-run.jar"]
