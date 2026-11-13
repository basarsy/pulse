# Stage 1: Build
FROM node:20-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

# Stage 2: Serve (the nginx entrypoint renders /etc/nginx/templates/*.template with envsubst)
FROM nginx:1.27-alpine
ENV API_UPSTREAM=http://app:8080
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
EXPOSE 80
