FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY target/pulse-command-service-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
