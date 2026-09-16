# Dev-oriented image: runs `next dev` (hot reload), not a production build.
# In compose the browser hits :3000 and Next proxies /api/* server-side to the
# API, so the frontend container still needs API_URL pointing at the api service.
FROM node:22-alpine
WORKDIR /app

# Deterministic install: npm ci uses the committed lockfile exactly.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
EXPOSE 3000
CMD ["npm", "run", "dev"]
