# syntax=docker/dockerfile:1.7
# Multi-stage build for the Vite + React Router v7 frontend.
# Node 22 LTS per docs/plan.md.

FROM node:22-alpine AS development-dependencies-env
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

FROM node:22-alpine AS production-dependencies-env
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --omit=dev

FROM node:22-alpine AS build-env
WORKDIR /app
COPY --from=development-dependencies-env /app/node_modules ./node_modules
COPY . .
# Allow baking VITE_* vars at build time if provided; empty defaults are fine.
ARG VITE_PY_API_URL=""
ARG VITE_KT_API_URL=""
ENV VITE_PY_API_URL=$VITE_PY_API_URL
ENV VITE_KT_API_URL=$VITE_KT_API_URL
RUN npm run build

FROM node:22-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production
COPY package.json package-lock.json ./
COPY --from=production-dependencies-env /app/node_modules ./node_modules
COPY --from=build-env /app/build ./build
EXPOSE 3000
CMD ["npm", "run", "start"]
