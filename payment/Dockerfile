# Build from the repository root: `docker build -f payment/Dockerfile -t bike-rental-payment .`
FROM node:20-alpine AS build
WORKDIR /app
COPY package.json package-lock.json tsconfig.base.json tsconfig.json ./
COPY core-api/package.json ./core-api/package.json
COPY payment/package.json ./payment/package.json
COPY rental/package.json ./rental/package.json
RUN npm ci
COPY core-api ./core-api
COPY payment ./payment
COPY rental ./rental
RUN npm run build && npm prune --omit=dev

FROM node:20-alpine
WORKDIR /app
ENV NODE_ENV=production
COPY --from=build /app/node_modules ./node_modules
COPY --from=build /app/package.json ./package.json
COPY --from=build /app/core-api/package.json ./core-api/package.json
COPY --from=build /app/core-api/dist ./core-api/dist
COPY --from=build /app/payment/package.json ./payment/package.json
COPY --from=build /app/payment/dist ./payment/dist
EXPOSE 8081
ENTRYPOINT ["node", "payment/dist/index.js"]
