# The built app served by nginx, which also forwards /api to the backend - so the browser
# talks to one origin and no CORS is involved (vite.config.ts does the same in development).
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build

FROM nginx:1.29-alpine
# The site is a template (see its header): the backend's address, and the container's own DNS so a
# replaced backend is found again without restarting this one.
ENV BACKEND_ORIGIN=http://backend:8080 NGINX_ENTRYPOINT_LOCAL_RESOLVERS=1
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY nginx/security-headers.conf /etc/nginx/snippets/security-headers.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=10s --timeout=3s --retries=6 CMD wget -q -O /dev/null http://127.0.0.1/ || exit 1
