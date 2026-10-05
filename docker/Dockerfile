FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace
ARG SERVICE

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY common ./common
COPY order-service ./order-service
COPY storage-service ./storage-service

RUN test "$SERVICE" = "order-service" || test "$SERVICE" = "storage-service"
RUN ./gradlew --no-daemon ":${SERVICE}:bootJar" -x test
RUN cp "${SERVICE}/build/libs/${SERVICE}-1.0.0.jar" /tmp/application.jar

FROM eclipse-temurin:21-jre-jammy

ARG SERVICE
RUN groupadd --system dealership && useradd --system --gid dealership --home-dir /app dealership
WORKDIR /app
COPY --from=build --chown=dealership:dealership /tmp/application.jar /app/application.jar

USER dealership
EXPOSE 8081 8082 9090
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
