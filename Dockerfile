# Multi-stage Docker build for Spring Boot services
FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /workspace

COPY pom.xml .
COPY saas-platform-common/ saas-platform-common/
COPY analytics-service/ analytics-service/
COPY ingestion-service/ ingestion-service/

RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Accept service name as build arg to select jar
ARG SERVICE_NAME

COPY --from=builder /workspace/${SERVICE_NAME}/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
