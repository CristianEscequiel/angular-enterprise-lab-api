# syntax=docker/dockerfile:1

# ---------- Etapa 1: compilar ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Primero solo lo que define las dependencias. Mientras no cambien,
# Docker reutiliza esta capa y no las vuelve a descargar.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null

# Después el código. Los tests ya corrieron en el job `build` del workflow;
# además Testcontainers no tiene Docker disponible dentro de un `docker build`.
COPY src src
RUN ./gradlew --no-daemon bootJar -x test \
    && rm -f build/libs/*-plain.jar \
    && mv build/libs/*.jar app.jar

# ---------- Etapa 2: ejecutar ----------
FROM eclipse-temurin:21-jre-alpine
# Usuario sin privilegios: si alguien explota la app, no es root dentro del contenedor.
RUN addgroup -S app && adduser -S app -G app
USER app
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar

EXPOSE 8080
# Tope de memoria de la JVM relativo al límite del contenedor (importante en 2 GB).
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60", "-jar", "app.jar"]
