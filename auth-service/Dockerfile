FROM eclipse-temurin:21-jdk AS builder

WORKDIR /workspace

COPY auth-service/mvnw ./mvnw
COPY auth-service/.mvn .mvn/
COPY pom.xml ./
COPY shared/common/pom.xml shared/common/pom.xml
COPY patient-service/pom.xml patient-service/pom.xml
COPY billing-service/pom.xml billing-service/pom.xml
COPY analytics-service/pom.xml analytics-service/pom.xml
COPY auth-service/pom.xml auth-service/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml

RUN apt-get update && apt-get install -y --no-install-recommends unzip && rm -rf /var/lib/apt/lists/*

RUN chmod +x mvnw && ./mvnw -pl auth-service -am dependency:go-offline -B

COPY shared/common/src shared/common/src
COPY auth-service/src auth-service/src

RUN ./mvnw -pl auth-service -am clean package -DskipTests -B

FROM eclipse-temurin:21-jre-alpine

LABEL description="Auth Service - Patient Management Microservice"

RUN apk add --no-cache curl

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

COPY --from=builder --chown=appuser:appgroup /workspace/auth-service/target/auth-service-0.0.1-SNAPSHOT.jar ./app.jar

COPY --chown=appuser:appgroup auth-service/docker-entrypoint.sh /docker-entrypoint.sh

RUN chmod +x /docker-entrypoint.sh

USER appuser

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

EXPOSE 4003

ENTRYPOINT ["/docker-entrypoint.sh"]
