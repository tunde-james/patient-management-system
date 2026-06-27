FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

COPY mvnw pom.xml ./
COPY .mvn .mvn/

RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

COPY src ./src

RUN ./mvnw clean package -DskipTests -B

FROM eclipse-temurin:21-jre-alpine

LABEL description="Patient Service - Patient Management Microservice"

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

COPY --from=builder --chown=appuser:appgroup /app/target/*.jar app.jar

COPY --chown=appuser:appgroup docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh

USER appuser

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

EXPOSE 4000

# Health check: Spring Boot Actuator /actuator/health
# We need to add the actuator dependency first (will be added in next step)
# For now, this will be a placeholder comment
# HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \\
#   CMD wget --quiet --tries=1 --spider http://localhost:4000/actuator/health || exit 1

ENTRYPOINT ["/docker-entrypoint.sh"]

