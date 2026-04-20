# Multi-stage build for the Ktor NSP backend.
#
# IMPORTANT: build this image from the REPO ROOT, not from `apps/kt-api`, so
# the composite-build siblings (`apps/kt-cp-sat/nsp-core`, `libs/cpsat-kt`,
# `apps/shared/openapi.yaml`, `data/nsp/*.json`) are reachable:
#
#   docker build -f apps/kt-api/Dockerfile -t nsp-kt-api .
#
# Or use the provided helper task `./gradlew :kt-api:dockerBuild` once it's
# wired in.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /work

# Copy the slim set of files Gradle needs. Order matters for cache hits:
# toolchains + wrappers first, then sources.
COPY gradle/ /work/gradle/
COPY apps/kt-api/gradle/ /work/apps/kt-api/gradle/
COPY apps/kt-api/gradlew /work/apps/kt-api/gradlew
COPY apps/kt-api/gradlew.bat /work/apps/kt-api/gradlew.bat
COPY apps/kt-api/build.gradle.kts /work/apps/kt-api/
COPY apps/kt-api/settings.gradle.kts /work/apps/kt-api/

COPY libs/cpsat-kt/ /work/libs/cpsat-kt/
COPY apps/kt-cp-sat/ /work/apps/kt-cp-sat/

COPY apps/kt-api/src /work/apps/kt-api/src
COPY apps/shared/openapi.yaml /work/apps/shared/openapi.yaml
COPY data/nsp /work/data/nsp

WORKDIR /work/apps/kt-api
RUN chmod +x ./gradlew && ./gradlew --no-daemon --no-configuration-cache installDist -x test

# Stage 2: minimal runtime.
FROM eclipse-temurin:25-jre
WORKDIR /opt/kt-api

# App distribution.
COPY --from=build /work/apps/kt-api/build/install/kt-api /opt/kt-api
# Bundled OpenAPI doc + sample instances (looked up via loadOpenApiDocument fallback).
COPY --from=build /work/apps/shared/openapi.yaml /opt/kt-api/share/openapi.yaml
COPY --from=build /work/data/nsp /opt/kt-api/share/data/nsp

# Create a non-root user to run the service.
RUN useradd --system --uid 1000 --create-home --shell /usr/sbin/nologin ktapi \
 && mkdir -p /var/lib/kt-api \
 && chown -R ktapi:ktapi /var/lib/kt-api /opt/kt-api

USER ktapi
WORKDIR /var/lib/kt-api

ENV PORT=8080 \
    NSP_API_DB_URL=jdbc:sqlite:/var/lib/kt-api/nsp-api.sqlite \
    NSP_API_SOLVER_CAPACITY=2 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0 --enable-native-access=ALL-UNNAMED"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=20s --retries=3 \
    CMD wget -qO- http://127.0.0.1:${PORT}/health >/dev/null 2>&1 || exit 1

ENTRYPOINT ["/opt/kt-api/bin/kt-api"]
