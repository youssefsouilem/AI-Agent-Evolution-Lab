FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S -G app app && mkdir -p /app/data && chown -R app:app /app
COPY --from=build --chown=app:app /workspace/target/agent-service-0.0.1-SNAPSHOT.jar /app/agent-service.jar
USER app
VOLUME ["/app/data"]
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/agent-service.jar"]
