# Stage 1: build
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
RUN ./gradlew dependencies --no-daemon || true

COPY src src
RUN ./gradlew installDist --no-daemon -x test

# Stage 2: runtime
FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /workspace/build/install/firebase-manager .

EXPOSE 8080

ENV PORT=8080

ENTRYPOINT ["/app/bin/firebase-manager"]
