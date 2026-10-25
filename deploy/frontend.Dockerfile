FROM node:22-alpine AS build
WORKDIR /build/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
COPY landing-page/ /build/landing-page/
ARG VITE_API_URL
ENV VITE_API_URL=${VITE_API_URL}
RUN npm run build

FROM caddy:2.10-alpine
COPY deploy/frontend.Caddyfile /etc/caddy/Caddyfile
COPY --from=build --chown=10001:10001 /build/frontend/dist/ /srv/
USER 10001:10001
EXPOSE 8080
