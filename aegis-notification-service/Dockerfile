# Build context MUST be the repository root (modules inherit from aegis-parent):
#   docker build -f aegis-notification-service/Dockerfile -t aegis-notification-service .

# ---- build stage ----
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY . .
# Checkstyle and tests already run in CI, so they are skipped here.
RUN chmod +x mvnw \
    && ./mvnw -B -ntp -pl aegis-notification-service -am package \
       -Dmaven.test.skip=true -Dcheckstyle.skip=true

# ---- runtime stage ----
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S aegis && adduser -S -G aegis aegis
WORKDIR /app
COPY --from=build --chown=aegis:aegis \
     /workspace/aegis-notification-service/target/aegis-notification-service-*.jar app.jar
USER aegis
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
