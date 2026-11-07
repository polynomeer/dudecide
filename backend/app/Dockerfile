# Build from the repository root: docker build -f backend/app/Dockerfile .
FROM gradle:8.14.2-jdk21 AS build
WORKDIR /workspace
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY backend ./backend
RUN gradle --no-daemon :backend:app:bootJar \
    && cp "$(ls backend/app/build/libs/*.jar | grep -v -- '-plain')" /workspace/app.jar

FROM eclipse-temurin:21-jre
RUN useradd --system --no-create-home dudecide
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
USER dudecide
EXPOSE 8080
# Flyway migrates on startup. Configure with DB_URL, DB_USER, DB_PASSWORD, APP_CORS_ALLOWED_ORIGINS,
# DUDECIDE_SESSION_COOKIE_SECURE, DUDECIDE_FORWARD_HEADERS, and DUDECIDE_AI_PROVIDER.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
