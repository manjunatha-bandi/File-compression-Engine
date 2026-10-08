# ============================================================
# Compression Engine v0.1
# Multi-stage Docker build
# ============================================================

# ------------------------------------------------------------
# Stage 1: Build
# ------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app

# Copy Maven project
COPY java/pom.xml ./java/pom.xml
COPY java/src ./java/src

# Build the Java application
RUN cd java && mvn clean package -DskipTests


# ------------------------------------------------------------
# Stage 2: Runtime
# ------------------------------------------------------------
FROM eclipse-temurin:21-jre

WORKDIR /app

# Copy compiled Java classes
COPY --from=builder /app/java/target/classes ./java/target/classes

# Copy web dashboard
COPY web ./web

# Render provides the actual PORT at runtime.
# This is documentation for the container.
EXPOSE 10000

# Start Compression Engine Web Server
CMD ["java", "-cp", "java/target/classes", "com.compression.web.WebServer"]
