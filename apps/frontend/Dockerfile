FROM node:24-alpine AS build
WORKDIR /workspace
COPY package.json package-lock.json* ./
COPY apps/frontend/package.json apps/frontend/package.json
COPY packages/tracking-sdk/package.json packages/tracking-sdk/package.json
RUN npm ci
COPY apps/frontend apps/frontend
COPY packages/tracking-sdk packages/tracking-sdk
ENV NEXT_TELEMETRY_DISABLED=1
RUN npm run build:web

FROM node:24-alpine AS runtime
ENV NODE_ENV=production NEXT_TELEMETRY_DISABLED=1 PORT=3000 HOSTNAME=0.0.0.0
RUN addgroup -S reengage && adduser -S -G reengage -u 10001 reengage
WORKDIR /app
COPY --from=build --chown=reengage:reengage /workspace/apps/frontend/.next/standalone ./
COPY --from=build --chown=reengage:reengage /workspace/apps/frontend/.next/static ./apps/frontend/.next/static
COPY --from=build --chown=reengage:reengage /workspace/apps/frontend/public ./apps/frontend/public
USER reengage
EXPOSE 3000
CMD ["node","apps/frontend/server.js"]
