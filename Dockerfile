FROM node:22-bookworm-slim AS frontend
WORKDIR /workspace/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /workspace
COPY backend/pom.xml ./backend/pom.xml
COPY backend/src ./backend/src
COPY --from=frontend /workspace/frontend/dist ./backend/src/main/resources/static
RUN mvn -B -f backend/pom.xml clean package -DskipTests

FROM eclipse-temurin:21-jre-jammy
RUN groupadd --system huce && useradd --system --gid huce --home-dir /app huce
WORKDIR /app
ENV HOST=0.0.0.0
COPY --from=backend /workspace/backend/target/huce-backend-1.0.0.jar /app/huce.jar
USER huce
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/huce.jar"]
