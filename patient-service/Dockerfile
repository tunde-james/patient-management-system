# ===================================================================
# Multi-Stage Build: Patient Service
# Build: docker build -t patient-service .
# Run:  docker run -p 8080:8080 patient-service
# ===================================================================

# -------------------------------------------------------------------
# STAGE 1: Build the application
# -------------------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS builder

# Set working directory
WORKDIR /app

# Copy Maven wrapper and pom.xml first (for layer caching)
COPY mvnw pom.xml ./
COPY .mvn .mvn/

# Download dependencies (cached unless pom.xml changes)
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy source code and build the JAR
COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# -------------------------------------------------------------------
# STAGE 2: Runtime image (smaller, no build tools)
# -------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine

LABEL maintainer="devops-coach"
LABEL description="Patient Service - Spring Boot Microservice"

# Create a non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Set working directory
WORKDIR /app

# Copy the built JAR from the build stage
COPY --from=builder --chown=appuser:appgroup /app/target/*.jar app.jar

# Switch to non-root user
USER appuser

# JVM tuning for containerized environments
# -XX:+UseContainerSupport: Let the JVM know it's running in a container
# -XX:MaxRAMPercentage=75.0: Use 75% of available container memory
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# Expose the application port
EXPOSE 8080

# Health check: Spring Boot Actuator /actuator/health
# We need to add the actuator dependency first (will be added in next step)
# For now, this will be a placeholder comment
# HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \\
#   CMD wget --quiet --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Run the application
ENTRYPOINT exec java $JAVA_OPTS -jar app.jar
