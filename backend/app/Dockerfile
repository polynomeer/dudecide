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
# Structured JSON logs. Health and metrics live on a separate port so they are never public; do not route 8081 externally.
ENV LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs
ENV MANAGEMENT_SERVER_PORT=8081
ENV MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,info,prometheus
EXPOSE 8080 8081
# Flyway migrates on startup. Configure with DB_URL, DB_USER, DB_PASSWORD, APP_CORS_ALLOWED_ORIGINS,
# DUDECIDE_SESSION_COOKIE_SECURE, DUDECIDE_FORWARD_HEADERS, and DUDECIDE_AI_PROVIDER.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
