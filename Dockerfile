# Образ без сборки внутри: нужен уже собранный JAR (./gradlew shadowJar)
FROM eclipse-temurin:17-jre
WORKDIR /app

COPY build/libs/firebase-manager.jar ./firebase-manager.jar

EXPOSE 8080 8443

ENTRYPOINT ["java", "-jar", "firebase-manager.jar"]
