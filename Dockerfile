FROM node:24-bookworm-slim AS frontend
WORKDIR /workspace/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM node:24-bookworm-slim
ENV NODE_ENV=production HOST=0.0.0.0
WORKDIR /workspace/backend
COPY backend/package*.json ./
RUN npm ci --omit=dev
COPY backend/src ./src
COPY backend/database ./database
COPY backend/scripts/setup-local.mjs ./scripts/setup-local.mjs
COPY --from=frontend /workspace/frontend/dist /workspace/frontend/dist
USER node
EXPOSE 8080
CMD ["node","src/server.js"]
