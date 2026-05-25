# Stage 1: Build
FROM maven:3.9-eclipse-temurin-17-alpine AS builder

WORKDIR /app

# Copy pom.xml and download dependencies (cacheable layer)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build
COPY . .
RUN mvn clean package -DskipTests -DskipITs

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Install curl and netcat for healthcheck/wait script
RUN apk add --no-cache curl netcat-openbsd

# Create non-root user
RUN addgroup -g 1001 appuser && adduser -D -u 1001 -G appuser appuser


# Copy built JAR from builder stage
COPY --from=builder /app/target/*.jar app.jar

# Copy wait-for-db helper script
COPY scripts/wait-for-db.sh /app/wait-for-db.sh

# Create logs directory and ensure ownership
RUN mkdir -p /app/logs && chmod +x /app/wait-for-db.sh && chown -R appuser:appuser /app

# Switch to non-root user
USER appuser

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD curl -fsS http://localhost:8080/actuator/health || exit 1

# Run the application with DB wait helper
ENTRYPOINT ["/app/wait-for-db.sh", "java", "-jar", "app.jar"]
