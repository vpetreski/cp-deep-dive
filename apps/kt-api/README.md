# kt-api — Ktor 3 backend for the NSP explorer

Skeleton Ktor 3 app that will eventually host the Nurse Scheduling Problem
(NSP) endpoints, backed by the `cpsat-kt` library.

## Endpoints today

| Method | Path      | Response |
|--------|-----------|----------|
| GET    | /health   | `{"status":"ok"}` |
| GET    | /version  | `{"name":"kt-api","version":"0.1.0","ortools":"9.15.6755"}` |

## Running locally

```bash
./gradlew run
```

The server binds to `0.0.0.0:8080` by default. Override with the `PORT`
environment variable.

```bash
curl http://localhost:8080/health
# {"status":"ok"}
```

## Building a Docker image

```bash
docker build -t kt-api:dev -f Dockerfile ..
```

(The build context is the repo root because the Dockerfile copies the whole
app directory; adjust to your CI.)

## Configuration

Ktor reads `src/main/resources/application.conf` (HOCON). Environment
variables override individual fields — e.g. `PORT=9090 ./gradlew run`.

## Requirements

- JDK 25+
- Gradle 9.x (use bundled wrapper)
- `libs/cpsat-kt` — included via composite build from `../../libs/cpsat-kt`.
