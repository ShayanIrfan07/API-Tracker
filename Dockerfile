# syntax=docker/dockerfile:1

FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace

# Avoid Maven Central 429s on Render (no build cache + dependency:go-offline hammers Central).
COPY docker/maven-settings.xml /root/.m2/settings.xml
COPY pom.xml .
COPY src/main ./src/main

RUN set -e; \
    for attempt in 1 2 3 4 5; do \
      echo "Maven package attempt ${attempt}..."; \
      if mvn -B -ntp -Dmaven.test.skip=true package; then \
        break; \
      fi; \
      if [ "$attempt" -eq 5 ]; then exit 1; fi; \
      sleep $((attempt * 20)); \
    done; \
    mv target/api-tracker-*.jar /workspace/app.jar

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S apitracker && adduser -S apitracker -G apitracker
USER apitracker

COPY --from=build /workspace/app.jar /app/app.jar

EXPOSE 8080

ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar --server.port=${PORT:-10000}"]