# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:21-jdk-alpine-3.24 AS build

ARG SERVICE_NAME
WORKDIR /workspace

COPY . .
RUN --mount=type=cache,id=orderflow-gradle,target=/root/.gradle \
    test -n "${SERVICE_NAME}" \
    && chmod +x gradlew \
    && ./gradlew ":services:${SERVICE_NAME}:bootJar" --no-daemon -x test \
    && find "services/${SERVICE_NAME}/build/libs" -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' \
       -exec cp '{}' /workspace/application.jar \;

FROM eclipse-temurin:21-jre-alpine-3.24

RUN addgroup -S -g 10001 orderflow \
    && adduser -S -D -H -u 10001 -G orderflow orderflow \
    && mkdir -p /application /tmp \
    && chown -R 10001:10001 /application /tmp

WORKDIR /application
COPY --from=build --chown=10001:10001 /workspace/application.jar /application/application.jar

USER 10001:10001
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Djava.io.tmpdir=/tmp"

ENTRYPOINT ["java", "-jar", "/application/application.jar"]
