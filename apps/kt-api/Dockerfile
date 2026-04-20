# Multi-stage build for the Ktor NSP backend.
# Stage 1: compile to a distribution tar.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY . .
RUN ./gradlew --no-daemon installDist

# Stage 2: minimal runtime.
FROM eclipse-temurin:25-jre
WORKDIR /opt/kt-api
COPY --from=build /app/build/install/kt-api /opt/kt-api
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 --enable-native-access=ALL-UNNAMED"
ENTRYPOINT ["/opt/kt-api/bin/kt-api"]
