FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build
COPY backend/.mvn .mvn
COPY backend/mvnw backend/pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY backend/src src
RUN ./mvnw -B -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build --chown=10001:10001 /build/target/clinica-0.0.1-SNAPSHOT.jar /app/app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
