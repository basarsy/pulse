FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /app

# Copy the parent pom and common module
COPY pom.xml .
COPY pulse-common/pom.xml pulse-common/
COPY pulse-vehicle-registry/pom.xml pulse-vehicle-registry/
COPY pulse-common/src pulse-common/src/

# Install the parent and common modules locally first
RUN mvn -N install
RUN cd pulse-common && mvn clean install -DskipTests

# Now build the service
COPY pulse-vehicle-registry/src pulse-vehicle-registry/src/
RUN cd pulse-vehicle-registry && mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/pulse-vehicle-registry/target/pulse-vehicle-registry-*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
