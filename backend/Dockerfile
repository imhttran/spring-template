# Multi-stage build for the Spring Boot API.
# Build stage is JDK 21 (not just JRE) so the Gradle Java toolchain resolves to
# the JDK already in the image instead of downloading one.

# ---- build ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Wrapper + build files first: these change rarely, so dependency resolution is
# cached as its own layer and only re-runs when the build itself changes.
COPY gradlew gradlew.bat ./
COPY gradle ./gradle
COPY settings.gradle.kts build.gradle.kts ./
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies

# Sources last, so editing code doesn't invalidate the dependency layer.
COPY src ./src
RUN ./gradlew --no-daemon bootJar

# ---- runtime ----
FROM eclipse-temurin:21-jre
WORKDIR /app
# The app writes nothing to disk, so it needs no root privileges.
RUN useradd --system --uid 1001 appuser
# Deterministic jar name set by bootJar { archiveFileName = "app.jar" }.
COPY --from=build /workspace/build/libs/app.jar app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
