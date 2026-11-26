# syntax=docker/dockerfile:1
FROM node:22-bookworm-slim AS frontend
WORKDIR /workspace/frontend
COPY frontend/package*.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /workspace
COPY backend/pom.xml ./backend/pom.xml
COPY backend/src ./backend/src
COPY --from=frontend /workspace/frontend/dist ./backend/src/main/resources/static
RUN --mount=type=cache,target=/root/.m2/repository,sharing=locked \
    set -eu; \
    for attempt in 1 2 3; do \
      if mvn -B -U -f backend/pom.xml clean package -DskipTests >/tmp/maven-build.log 2>&1; then \
        cat /tmp/maven-build.log; rm /tmp/maven-build.log; exit 0; \
      fi; \
      cat /tmp/maven-build.log; \
      if [ "$attempt" = 3 ] || ! grep -Eiq 'status code: (429|502|503|504)|connection reset|read timed out|connect timed out|temporary failure' /tmp/maven-build.log; then exit 1; fi; \
      sleep "$((attempt * 20))"; \
    done; exit 1

FROM eclipse-temurin:21-jre-jammy AS runtime-base
RUN groupadd --system huce && useradd --system --gid huce --home-dir /app huce
WORKDIR /app
ENV HOST=0.0.0.0
USER huce
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/huce.jar"]

# CI runs the packaged, verified artifact from the same workflow; no second Maven download.
FROM runtime-base AS ci
COPY .ci-package/huce.jar /app/huce.jar

# Default target for local users: build from the checked-out source, without CI artifacts.
FROM runtime-base AS local
COPY --from=backend /workspace/backend/target/huce-backend-1.0.0.jar /app/huce.jar
