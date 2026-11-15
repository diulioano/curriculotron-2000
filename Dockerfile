# syntax=docker/dockerfile:1

FROM node:24-alpine AS frontend-build
WORKDIR /workspace/frontend

COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --ignore-scripts

COPY frontend/ ./
RUN npm run build

FROM nginx:1.30.6-alpine3.24 AS frontend
COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=frontend-build /workspace/frontend/dist /usr/share/nginx/html
EXPOSE 80

FROM eclipse-temurin:25-jdk-jammy AS backend-build
WORKDIR /workspace/backend

COPY backend/ ./
RUN ./gradlew --no-daemon -Pcontainerized=true bootJar

FROM eclipse-temurin:25-jre-jammy AS backend

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && groupadd --system --gid 10001 curriculotron \
    && useradd --system --uid 10001 --gid curriculotron \
        --home-dir /var/lib/curriculotron --shell /usr/sbin/nologin curriculotron \
    && mkdir -p /var/lib/curriculotron \
    && chown -R curriculotron:curriculotron /var/lib/curriculotron \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=backend-build --chown=curriculotron:curriculotron \
    /workspace/backend/build/libs/curriculotron-0.0.1-SNAPSHOT.jar /app/app.jar

USER 10001:10001
EXPOSE 8443

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD curl --fail --silent http://127.0.0.1:8443/api/health || exit 1

ENTRYPOINT ["java", "--enable-native-access=ALL-UNNAMED", "-jar", "/app/app.jar"]
