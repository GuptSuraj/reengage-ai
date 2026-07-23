FROM maven:3.9.16-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S reengage && adduser -S -G reengage -u 10001 reengage
WORKDIR /app
COPY --from=build /workspace/target/core-api-1.0.0.jar app.jar
USER reengage
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --retries=5 \
  CMD wget -q -O - http://localhost:8080/actuator/health/liveness || exit 1
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-XX:+ExitOnOutOfMemoryError","-jar","app.jar"]
