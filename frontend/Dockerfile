# --- Build stage ---
FROM node:20-alpine AS build
WORKDIR /workspace

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# --- Runtime stage ---
FROM nginx:1.27-alpine
COPY --from=build /workspace/dist/frontend/browser /usr/share/nginx/html
# nginx's docker-entrypoint.d/20-envsubst-on-templates.sh runs envsubst on every
# /etc/nginx/templates/*.template file at container start, writing the result
# to /etc/nginx/conf.d/ (see docker-compose.yml for NGINX_MAX_BODY_SIZE).
COPY templates/default.conf.template /etc/nginx/templates/default.conf.template
ENV NGINX_MAX_BODY_SIZE=12M

EXPOSE 80
