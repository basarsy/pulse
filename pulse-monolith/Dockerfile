# Pulse modular monolith. Build from the repository root:
#   docker build -f pulse-monolith/Dockerfile -t pulse-app .

# Stage 1: build the executable JAR
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build

# Resolve dependencies first so they are cached independently of source changes
COPY pom.xml ./
COPY pulse-monolith/pom.xml pulse-monolith/
RUN mvn -B -q -pl pulse-monolith -am dependency:go-offline

COPY pulse-monolith/src pulse-monolith/src
RUN mvn -B -q -pl pulse-monolith -am package -DskipTests

# Stage 2: minimal runtime image, non-root
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S pulse && adduser -S pulse -G pulse
USER pulse:pulse

COPY --from=builder /build/pulse-monolith/target/pulse-monolith-*.jar app.jar
EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
