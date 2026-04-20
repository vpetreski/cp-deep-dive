# Chapter 17 — Containerize & deploy

- **Phase:** App build — shipping
- **Estimated:** 1 week
- **Status:** drafted
- **Last updated:** 2026-04-19

## Goal

Take the three working pieces from chapters 15–16 — the Python API, the Kotlin API, the React UI — and turn them into something a stranger on the internet can `curl`. You'll build reproducible container images, wire them together locally with `docker-compose`, add enough observability to debug a stuck solve, set up a CI pipeline that catches regressions before merge, and push the whole lot to Fly.io.

By the end you'll have:
- Three small, layered Docker images (py-api, kt-api, web) that rebuild fast
- A `docker-compose.yml` that brings the full stack up with one command
- OpenTelemetry traces flowing from UI → API → CP-SAT and back
- A GitHub Actions pipeline running lint, tests, build, and image publish on every push
- A public Fly.io deployment you can share, with a runbook for when things go sideways

## Before you start

Make sure you have:
- Chapters 15–16 finished; both backends and the UI run locally via `uvicorn`, `./gradlew run`, and `pnpm dev`.
- Docker Desktop 4.30+ (or `colima` on Mac, `podman` on Linux if you prefer).
- A GitHub account with the repo pushed.
- A Fly.io account (`brew install flyctl && fly auth signup`). First-time deploy is free-tier friendly.
- Roughly 20 GB free disk — Docker layer caches add up fast.

See the relevant ADRs in `docs/adr/` — once the containerization strategy is locked in (multi-stage builds vs Buildpacks), add a new ADR and link it here.

## Concepts introduced

- *Multi-stage Docker build* — separate the build toolchain from the runtime image.
- *Base image choice* — `python:3.12-slim`, `eclipse-temurin:21-jre`, `nginx:1.27-alpine` — each picked for a reason.
- *Layer caching* — order matters; copy manifests before source.
- *Compose network* — services addressable by name (`py-api:8001`, `kt-api:8002`).
- *Named volume* — one `instances/` folder shared across all three services.
- *OpenTelemetry* — vendor-neutral tracing and metrics.
- *Trace propagation* — a single `traceparent` header ties UI, API, and solver together.
- *GitHub Actions matrix* — parallel jobs for Python + Kotlin + Node.
- *Container registry* — GHCR (GitHub Container Registry) as the default.
- *Fly.io primitives* — `fly.toml`, machines, volumes, secrets.

## §1 Intuition

A container is a tarball of your app's filesystem plus a command to run. It's not a VM — it shares the host kernel. That's why a 150 MB Python image starts in 200 ms but a 2 GB VM takes 20 seconds.

Three things make containers worth the hassle:
1. **Reproducibility.** The image that passes CI is byte-identical to the one in prod. No "works on my machine."
2. **Isolation.** The Kotlin API bundles JDK 21. The Python API bundles CPython 3.12. They don't fight.
3. **Portability.** Same image runs on your laptop, your CI runner, Fly.io, or a Raspberry Pi.

The cost: you pay up front with a Dockerfile per service and a compose file to orchestrate them. The payoff: deploy = `git push`.

*Multi-stage builds* matter because the tools you need to compile Kotlin (Gradle, JDK with compiler, ~800 MB) aren't the tools you need to run it (JRE, your jar, ~200 MB). Stage 1 builds. Stage 2 copies the artifact and throws the builder away. Your final image is small and has no build tools — smaller attack surface, faster pulls.

*OpenTelemetry* is the bit where you stop guessing why the solver hung. When a user clicks "Solve" in the UI, you generate a trace ID and propagate it through every HTTP hop. In your trace backend (Jaeger, Honeycomb, Grafana Tempo — pick any) you see a waterfall: 30 ms in the UI, 5 ms crossing the network, 47 seconds in `cp_model.Solve`, 2 ms to stream the result back. Now you know the solver is the bottleneck, not the network.

## §2 Formal definition — the shape of a production deploy

No math here, but there is a shape:

```
              ┌─────────────────┐
              │   web (nginx)   │  :80
              │  static SPA     │
              └────────┬────────┘
                       │ fetch('/api/...')
              ┌────────┴────────┐
              │  reverse proxy  │  (nginx or fly-proxy)
              └────┬───────┬────┘
                   │       │
          /py/*    │       │ /kt/*
                   ▼       ▼
             ┌────────┐ ┌────────┐
             │ py-api │ │ kt-api │
             │ :8001  │ │ :8002  │
             └───┬────┘ └───┬────┘
                 │          │
                 ▼          ▼
              ┌─────────────────┐
              │  instances/     │  shared volume
              │  runs/          │
              └─────────────────┘
                 │          │
                 └────┬─────┘
                      ▼
              ┌─────────────────┐
              │  OTLP collector │  (local: jaeger)
              └─────────────────┘
```

Everything except the nginx-served SPA speaks HTTP on an internal network. The browser only reaches `web`; the UI proxies to the two APIs. The APIs share a volume for instance JSON and run artifacts. All three emit OTLP traces to a collector.

Invariants you'll enforce:
- **Stateless APIs.** All persistent data lives in the volume. Killing a container never loses work.
- **Health endpoints.** `GET /health` returns 200 or the orchestrator restarts you.
- **Graceful shutdown.** SIGTERM → stop accepting new requests → finish in-flight ones → exit. 30 s timeout.
- **No secrets in images.** Fly secrets, GH secrets, `.env.example` for local.
- **Pinned digests in prod.** `nginx:1.27-alpine@sha256:…` not floating `nginx:alpine`.

## §3 Worked example — one service containerized by hand

Before we automate, let's containerize the Python API by hand and see every layer.

```dockerfile
# /apps/python/api/Dockerfile — v1, naive
FROM python:3.12
WORKDIR /app
COPY . .
RUN pip install -r requirements.txt
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8001"]
```

Build and inspect:

```bash
docker build -t py-api:naive apps/python/api
docker images py-api:naive
# py-api:naive   1.2GB
docker history py-api:naive
```

1.2 GB. Why?
- `python:3.12` is ~1 GB before you do anything (full Debian + build tools).
- `COPY . .` includes `__pycache__`, `.venv`, `.git`, and whatever else is in the folder.
- Every `pip install` invalidates the cache when any file changes.

Fix it in four moves:

```dockerfile
# /apps/python/api/Dockerfile — v2, multi-stage + slim + cache-friendly
# ---- builder ----
FROM python:3.12-slim AS builder
WORKDIR /build

# 1. Install build deps only here.
RUN apt-get update && apt-get install -y --no-install-recommends \
      build-essential \
    && rm -rf /var/lib/apt/lists/*

# 2. Copy manifests first — this layer only invalidates when deps change.
COPY requirements.txt .

# 3. Install into a venv we can copy wholesale.
RUN python -m venv /opt/venv
ENV PATH="/opt/venv/bin:$PATH"
RUN pip install --no-cache-dir -r requirements.txt

# ---- runtime ----
FROM python:3.12-slim AS runtime
WORKDIR /app

# Non-root user — never run as root in prod.
RUN groupadd -r app && useradd -r -g app app

# Pull in the venv from the builder.
COPY --from=builder /opt/venv /opt/venv
ENV PATH="/opt/venv/bin:$PATH"

# Now copy source — this is the layer that changes on every commit.
COPY --chown=app:app src/ ./src/

USER app
EXPOSE 8001

# Health endpoint must exist (see §5).
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8001/health').read()" || exit 1

CMD ["uvicorn", "src.main:app", "--host", "0.0.0.0", "--port", "8001"]
```

Build and measure:

```bash
docker build -t py-api:v2 apps/python/api
docker images py-api:v2
# py-api:v2   180MB
```

180 MB vs 1.2 GB. The deltas:
- `python:3.12-slim` instead of `python:3.12` saves ~800 MB.
- Build deps (`build-essential`) only live in the builder stage, not runtime.
- Source is copied last, so changing a line in `main.py` only invalidates the tiny source layer.

A `.dockerignore` next to the Dockerfile cuts the context size:

```
# /apps/python/api/.dockerignore
.venv/
__pycache__/
*.pyc
.pytest_cache/
.ruff_cache/
.git/
tests/
```

Rebuild time after a source-only change: ~5 seconds. That's the whole point.

## §4 Python implementation — py-api Dockerfile + OpenTelemetry

Here's the final `/apps/python/api/Dockerfile`:

```dockerfile
# /apps/python/api/Dockerfile
FROM python:3.12-slim AS builder
WORKDIR /build

RUN apt-get update && apt-get install -y --no-install-recommends \
      build-essential \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN python -m venv /opt/venv && \
    /opt/venv/bin/pip install --no-cache-dir --upgrade pip && \
    /opt/venv/bin/pip install --no-cache-dir -r requirements.txt

FROM python:3.12-slim AS runtime
WORKDIR /app

RUN groupadd -r app && useradd -r -g app -d /app app \
    && chown -R app:app /app

COPY --from=builder /opt/venv /opt/venv
ENV PATH="/opt/venv/bin:$PATH" \
    PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    OTEL_SERVICE_NAME=py-api \
    OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4318

COPY --chown=app:app src/ ./src/

USER app
EXPOSE 8001

HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
    CMD python -c "import urllib.request,sys; \
        sys.exit(0 if urllib.request.urlopen('http://localhost:8001/health').status==200 else 1)"

# opentelemetry-instrument auto-patches fastapi + requests + logging.
CMD ["opentelemetry-instrument", "uvicorn", "src.main:app", \
     "--host", "0.0.0.0", "--port", "8001"]
```

Add OTel deps to `requirements.txt`:

```txt
# /apps/python/api/requirements.txt (additions)
opentelemetry-distro==0.48b0
opentelemetry-exporter-otlp==1.27.0
opentelemetry-instrumentation-fastapi==0.48b0
opentelemetry-instrumentation-logging==0.48b0
```

For custom spans around the solve itself, wrap the CP-SAT call:

```python
# /apps/python/api/src/solver_runner.py (relevant bit)
from opentelemetry import trace
from ortools.sat.python import cp_model

tracer = trace.get_tracer(__name__)

def run_solve(instance: dict, run_id: str) -> dict:
    with tracer.start_as_current_span("solve.cp_sat") as span:
        span.set_attribute("run.id", run_id)
        span.set_attribute("instance.nurses", len(instance["nurses"]))
        span.set_attribute("instance.days", instance["horizon_days"])

        model = cp_model.CpModel()
        # ... build model ...

        with tracer.start_as_current_span("solve.build_model"):
            build_model(model, instance)

        with tracer.start_as_current_span("solve.cp_sat.invoke") as solve_span:
            solver = cp_model.CpSolver()
            solver.parameters.max_time_in_seconds = 60.0
            solver.parameters.num_search_workers = 8
            status = solver.Solve(model)

            solve_span.set_attribute("solve.status", solver.StatusName(status))
            solve_span.set_attribute("solve.objective", solver.ObjectiveValue())
            solve_span.set_attribute("solve.wall_time_s", solver.WallTime())
            solve_span.set_attribute("solve.branches", solver.NumBranches())
            solve_span.set_attribute("solve.conflicts", solver.NumConflicts())

        return extract_solution(solver, instance)
```

Now every solve shows up in your trace backend with status, objective, wall time, and branch count as span attributes. Filter for slow runs by `solve.wall_time_s > 30` — you'll see them with one query.

## §5 Kotlin implementation — kt-api Dockerfile + OpenTelemetry

The Kotlin build has an extra wrinkle: Gradle's build cache is heavy, and the fat jar is chunky. Multi-stage handles it cleanly.

```dockerfile
# /apps/kotlin/api/Dockerfile
# ---- builder ----
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

# Copy the wrapper + config first — they change rarely.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle/ gradle/
RUN chmod +x gradlew && ./gradlew --version

# Resolve deps before copying source; next build reuses the cache.
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon dependencies || true

# Now source.
COPY src/ src/

# Build the shadow jar (uses com.github.johnrengelman.shadow).
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon shadowJar

# ---- runtime ----
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# OpenTelemetry Java agent — instrumented via -javaagent, no code changes.
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.8.0/opentelemetry-javaagent.jar /opt/otel/agent.jar

RUN addgroup -S app && adduser -S -G app app \
    && chown -R app:app /app /opt/otel

COPY --from=builder --chown=app:app /build/build/libs/kt-api-all.jar /app/app.jar

USER app
EXPOSE 8002

ENV JAVA_TOOL_OPTIONS="-javaagent:/opt/otel/agent.jar \
                       -Dotel.service.name=kt-api \
                       -Dotel.exporter.otlp.endpoint=http://otel-collector:4318 \
                       -Dotel.traces.exporter=otlp \
                       -Dotel.metrics.exporter=otlp \
                       -Dotel.logs.exporter=otlp \
                       -XX:MaxRAMPercentage=75 \
                       -XX:+UseG1GC"

HEALTHCHECK --interval=30s --timeout=3s --start-period=15s --retries=3 \
    CMD wget -q -O - http://localhost:8002/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
```

Key choices:
- **Alpine-based JRE** for runtime: 180 MB vs 450 MB for Ubuntu-based.
- **Mount cache for Gradle**: `--mount=type=cache` is a BuildKit feature that persists `/root/.gradle` across builds on the same machine. First build: 3 min. Subsequent builds after a code-only change: 25 s.
- **Java agent for OpenTelemetry**: `-javaagent:agent.jar` auto-instruments Ktor, HTTP clients, logging. Zero code changes in the app.
- **`MaxRAMPercentage=75`**: the JVM defaults assume a whole machine. Tell it to use 75% of the container's memory limit instead.

For custom spans around solver invocation, use the OTel API directly in `SolverRunner.kt`:

```kotlin
// /apps/kotlin/api/src/main/kotlin/SolverRunner.kt (additions)
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Tracer

private val tracer: Tracer = GlobalOpenTelemetry.getTracer("kt-api.solver")

suspend fun runSolve(instance: Instance, runId: String): SolveResult {
    val span = tracer.spanBuilder("solve.cp_sat")
        .setAttribute("run.id", runId)
        .setAttribute("instance.nurses", instance.nurses.size.toLong())
        .setAttribute("instance.days", instance.horizonDays.toLong())
        .startSpan()

    return try {
        span.makeCurrent().use {
            val model = buildModel(instance)  // wrap in child span if you like
            val result = tracer.spanBuilder("solve.cp_sat.invoke").startSpan().use { solve ->
                val solver = CpSolver()
                solver.parameters.maxTimeInSeconds = 60.0
                solver.parameters.numSearchWorkers = 8
                val status = solver.solve(model.native)
                solve.setAttribute("solve.status", status.name)
                solve.setAttribute("solve.objective", solver.objectiveValue)
                solve.setAttribute("solve.wall_time_s", solver.wallTime)
                extractSolution(solver, instance)
            }
            result
        }
    } catch (e: Throwable) {
        span.recordException(e)
        throw e
    } finally {
        span.end()
    }
}
```

The pattern mirrors Python: span per logical unit, attributes for numbers you want to filter on.

## §6 docker-compose for local development

A single `docker-compose.yml` brings the whole stack up:

```yaml
# /docker-compose.yml
name: cp-deep-dive

services:
  py-api:
    build:
      context: ./apps/python/api
      dockerfile: Dockerfile
    ports:
      - "8001:8001"
    volumes:
      - instances:/app/instances
      - runs:/app/runs
    environment:
      OTEL_EXPORTER_OTLP_ENDPOINT: http://otel-collector:4318
      LOG_LEVEL: info
    depends_on:
      otel-collector:
        condition: service_started
    healthcheck:
      test: ["CMD", "python", "-c", "import urllib.request; urllib.request.urlopen('http://localhost:8001/health')"]
      interval: 30s
      timeout: 3s
      retries: 3
      start_period: 10s

  kt-api:
    build:
      context: ./apps/kotlin/api
      dockerfile: Dockerfile
    ports:
      - "8002:8002"
    volumes:
      - instances:/app/instances
      - runs:/app/runs
    environment:
      OTEL_EXPORTER_OTLP_ENDPOINT: http://otel-collector:4318
    depends_on:
      otel-collector:
        condition: service_started

  web:
    build:
      context: ./apps/web
      dockerfile: Dockerfile
    ports:
      - "5173:80"
    environment:
      # Baked at build time via ARG; see Dockerfile.
      VITE_PY_API_URL: http://localhost:8001
      VITE_KT_API_URL: http://localhost:8002
    depends_on:
      py-api:
        condition: service_healthy
      kt-api:
        condition: service_started

  otel-collector:
    image: otel/opentelemetry-collector-contrib:0.106.1
    command: ["--config=/etc/otel/config.yaml"]
    volumes:
      - ./ops/otel-collector.yaml:/etc/otel/config.yaml:ro
    ports:
      - "4318:4318"  # OTLP/HTTP
    depends_on:
      jaeger:
        condition: service_started

  jaeger:
    image: jaegertracing/all-in-one:1.60
    ports:
      - "16686:16686"  # UI
    environment:
      COLLECTOR_OTLP_ENABLED: "true"

volumes:
  instances:
  runs:
```

Collector config routes traces to Jaeger:

```yaml
# /ops/otel-collector.yaml
receivers:
  otlp:
    protocols:
      http:
        endpoint: 0.0.0.0:4318

processors:
  batch:
    timeout: 1s

exporters:
  otlp/jaeger:
    endpoint: jaeger:4317
    tls:
      insecure: true
  debug:
    verbosity: basic

service:
  pipelines:
    traces:
      receivers: [otlp]
      processors: [batch]
      exporters: [otlp/jaeger, debug]
```

Bring everything up:

```bash
docker compose up --build
```

Open:
- UI: http://localhost:5173
- Python API docs: http://localhost:8001/docs
- Kotlin API docs: http://localhost:8002/openapi
- Jaeger UI: http://localhost:16686

Trigger a solve from the UI, then in Jaeger pick service `py-api` → find the trace. You'll see the span waterfall: `POST /runs` → `solve.cp_sat` → `solve.build_model` → `solve.cp_sat.invoke`. Cross-service traces work because OTel auto-propagates `traceparent` across HTTP.

## §7 The web image

The UI is just static files after `pnpm build`. Use nginx for runtime.

```dockerfile
# /apps/web/Dockerfile
# ---- builder ----
FROM node:20-alpine AS builder
WORKDIR /build

RUN corepack enable

# Deps first.
COPY package.json pnpm-lock.yaml ./
RUN --mount=type=cache,target=/root/.local/share/pnpm pnpm install --frozen-lockfile

# Source.
COPY . .

# Build-time API URLs; override via `--build-arg`.
ARG VITE_PY_API_URL=http://localhost:8001
ARG VITE_KT_API_URL=http://localhost:8002
ENV VITE_PY_API_URL=$VITE_PY_API_URL
ENV VITE_KT_API_URL=$VITE_KT_API_URL

RUN pnpm build

# ---- runtime ----
FROM nginx:1.27-alpine AS runtime

COPY --from=builder /build/build/client /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
    CMD wget -q -O - http://localhost/ || exit 1
```

nginx config supports SPA routing (send unknown paths to `index.html`):

```nginx
# /apps/web/nginx.conf
server {
    listen 80 default_server;
    server_name _;
    root /usr/share/nginx/html;
    index index.html;

    gzip on;
    gzip_types text/css application/javascript application/json image/svg+xml;

    # Cache immutable assets forever.
    location /assets/ {
        expires 1y;
        add_header Cache-Control "public, immutable";
        try_files $uri =404;
    }

    # SPA fallback.
    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

One-line image: 22 MB. Pulls instantly.

## §8 GitHub Actions CI

One workflow, three parallel jobs, gated on a unified status check.

```yaml
# /.github/workflows/ci.yml
name: CI

on:
  push:
    branches: [main]
  pull_request:

concurrency:
  group: ci-${{ github.ref }}
  cancel-in-progress: true

jobs:
  py-api:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: apps/python/api
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-python@v5
        with:
          python-version: "3.12"
          cache: pip
          cache-dependency-path: apps/python/api/requirements*.txt
      - run: pip install -r requirements.txt -r requirements-dev.txt
      - run: ruff check .
      - run: mypy src
      - run: pytest --cov=src --cov-report=xml
      - uses: codecov/codecov-action@v4
        with:
          files: apps/python/api/coverage.xml
          flags: py-api

  kt-api:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: apps/kotlin/api
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "21"
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew ktlintCheck detekt
      - run: ./gradlew test
      - run: ./gradlew shadowJar
      - uses: actions/upload-artifact@v4
        with:
          name: kt-api-jar
          path: apps/kotlin/api/build/libs/*-all.jar

  web:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: apps/web
    steps:
      - uses: actions/checkout@v4
      - uses: pnpm/action-setup@v4
        with:
          version: 9
      - uses: actions/setup-node@v4
        with:
          node-version: "20"
          cache: pnpm
          cache-dependency-path: apps/web/pnpm-lock.yaml
      - run: pnpm install --frozen-lockfile
      - run: pnpm lint
      - run: pnpm typecheck
      - run: pnpm test
      - run: pnpm build

  docker:
    runs-on: ubuntu-latest
    needs: [py-api, kt-api, web]
    if: github.event_name == 'push' && github.ref == 'refs/heads/main'
    permissions:
      contents: read
      packages: write
    strategy:
      matrix:
        service:
          - name: py-api
            context: ./apps/python/api
          - name: kt-api
            context: ./apps/kotlin/api
          - name: web
            context: ./apps/web
    steps:
      - uses: actions/checkout@v4
      - uses: docker/setup-buildx-action@v3
      - uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
      - uses: docker/build-push-action@v6
        with:
          context: ${{ matrix.service.context }}
          push: true
          tags: |
            ghcr.io/${{ github.repository }}/${{ matrix.service.name }}:latest
            ghcr.io/${{ github.repository }}/${{ matrix.service.name }}:${{ github.sha }}
          cache-from: type=gha,scope=${{ matrix.service.name }}
          cache-to: type=gha,mode=max,scope=${{ matrix.service.name }}
```

Notes worth internalizing:
- `concurrency` cancels in-flight runs for the same ref — don't waste CI minutes on superseded pushes.
- Matrix strategy for the docker job lets three images build in parallel.
- `cache-from: type=gha` uses GitHub's action cache for Docker layers. Dramatic speedup after the first run.
- Build job only pushes on `main`. PRs build but don't push.

## §9 Fly.io deploy

One `fly.toml` per service. Start with `py-api`:

```toml
# /apps/python/api/fly.toml
app = "nsp-py-api"
primary_region = "fra"

[build]
  image = "ghcr.io/vanja-petreski/cp-deep-dive/py-api:latest"

[http_service]
  internal_port = 8001
  force_https = true
  auto_stop_machines = true
  auto_start_machines = true
  min_machines_running = 0

  [http_service.checks]
    [[http_service.checks.http]]
      interval = "30s"
      timeout = "3s"
      grace_period = "10s"
      method = "GET"
      path = "/health"

[[mounts]]
  source = "nsp_data"
  destination = "/app/instances"
  initial_size = "1gb"

[[vm]]
  cpu_kind = "shared"
  cpus = 1
  memory_mb = 1024

[env]
  OTEL_SERVICE_NAME = "py-api"
  # Collector URL set as a Fly secret (see below).
```

First deploy:

```bash
cd apps/python/api
fly launch --no-deploy  # generates fly.toml, links to app, creates volume
fly volumes create nsp_data --size 1 --region fra

# Secrets (never in fly.toml).
fly secrets set OTEL_EXPORTER_OTLP_ENDPOINT="https://your-otlp-endpoint"

fly deploy
```

Repeat for `kt-api` and `web`. For the web image, override build args at deploy time so it targets the deployed API URLs:

```bash
cd apps/web
fly deploy --build-arg VITE_PY_API_URL=https://nsp-py-api.fly.dev \
           --build-arg VITE_KT_API_URL=https://nsp-kt-api.fly.dev
```

After `fly deploy`, hit the health endpoint:

```bash
curl https://nsp-py-api.fly.dev/health
# {"status":"ok","version":"0.3.0"}
```

Open `https://nsp-web.fly.dev` — your deep-dive project is now live on the internet.

### §9.1 Managing instances

List machines and scale:

```bash
fly status                       # machines, health, last deploy
fly logs                         # tail logs (structured JSON if you emit it)
fly ssh console                  # shell into the running machine
fly scale count 2 --region fra   # horizontal scale (if stateless)
fly scale vm shared-cpu-2x       # vertical scale
```

`auto_stop_machines = true` means Fly will park the machine after 5 minutes of no traffic. First request after cold start takes ~2 s. Saves money — for a demo app that's the right call.

## §10 Runbook — when things go wrong

### "Solver stuck" — a run hangs > 2 min

Symptoms: UI shows "running" indefinitely; no incumbent updates.

1. Find the run: `curl https://nsp-py-api.fly.dev/runs/<id>` returns `{status: running, elapsed_s: 120}`.
2. Open Jaeger, filter by `run.id = <id>`. Look at the `solve.cp_sat.invoke` span.
   - If the span hasn't closed: solver is still searching. Check `solve.branches` attribute as it grows.
   - If the span closed with an error: read the exception.
3. SSH in: `fly ssh console -a nsp-py-api`. Run `ps aux | grep python`. If you see 8 worker processes and no progress, the solver is on a hard instance.
4. Cancel from the UI (DELETE run). The worker sends `solver.stop_search()`.
5. If cancel doesn't work, kill the machine: `fly machine restart <id>`.

### "Out of memory" — container OOMKills

Symptoms: `fly logs` shows `exit code 137`.

1. Check machine memory: `fly status` shows current VM size.
2. If CP-SAT is the culprit, cap workers via env: `fly secrets set CPSAT_NUM_WORKERS=4`.
3. Scale up: `fly scale vm shared-cpu-4x`. (4 CPUs, 8 GB.)
4. Add `-XX:MaxRAMPercentage=75` to `JAVA_TOOL_OPTIONS` for the Kotlin side.

### "Deploy failed on Fly" — machine won't come up

1. `fly logs -a nsp-py-api` — look for the startup error. Usually env var or missing volume.
2. `fly image show` — confirm the latest image is what you just pushed.
3. `fly releases` — if the new release failed, `fly releases rollback <prev>` reverts in ~30 s.

### "CI red on main" — don't panic

1. Revert the bad commit: `git revert <sha> && git push`.
2. CI will rebuild and redeploy the reverted version.
3. Fix forward on a branch.

Keep this runbook close — preferably linked from the README or pinned in your team's wiki. Future-you will thank you.

## §11 MiniZinc — not applicable

No MiniZinc in this chapter. Deployment isn't a modeling topic.

## §12 Comparison & takeaways

| Aspect | Python (py-api) | Kotlin (kt-api) | Web (react) |
|---|---|---|---|
| Base image | `python:3.12-slim` | `eclipse-temurin:21-jre-alpine` | `nginx:1.27-alpine` |
| Final size | ~180 MB | ~195 MB | ~22 MB |
| Cold-start | 0.8 s | 2.5 s (JVM warmup) | instant (static) |
| Rebuild after source change | 5 s | 20 s | 10 s |
| OTel instrumentation | `opentelemetry-distro` + manual spans | Java agent + manual spans | not yet (see exercise) |
| Graceful shutdown | SIGTERM → `app.on_shutdown` | SIGTERM → `application.engine.stop()` | nginx exits clean |

Takeaways:
1. **Multi-stage always.** No exceptions. Your attack surface, pull time, and rebuild time all halve at minimum.
2. **Layer manifests before source.** It's the single biggest dev-loop speedup you'll get.
3. **Use BuildKit cache mounts.** Gradle and pnpm are slow; cache mounts are the difference between 3-minute and 25-second rebuilds.
4. **OTel is free insurance.** You wrote zero observability code for the Python HTTP layer and got full instrumentation from `opentelemetry-distro`. That's the win.
5. **Fly.io is the right starting point.** Heroku's gone, Render is fine but slower, AWS Fargate is overkill for a demo. When you outgrow Fly, the Docker images port anywhere.
6. **CI should gate deploys.** Tests green → docker job runs → image pushed → Fly auto-deploys (optional webhook). If tests are flaky, fix them or kill them.

## §13 Exercises

### 13-A Prometheus metrics endpoint (Python API)

Add a `/metrics` endpoint to `py-api` exposing CP-SAT counters: `solves_total`, `solves_failed_total`, `solve_duration_seconds` (histogram), `active_runs` (gauge). Write a quick Grafana dashboard screenshot-and-share.

<details><summary>Hint</summary>

- Use `prometheus-client` (`pip install prometheus-client`).
- In `main.py`, add `from prometheus_client import Counter, Histogram, Gauge, make_asgi_app`.
- Mount: `app.mount("/metrics", make_asgi_app())`.
- Increment counters inside `solver_runner.py`: `SOLVES_TOTAL.inc()` on completion.
- Histogram: `SOLVE_DURATION.observe(elapsed_s)` using `time.perf_counter()` deltas.
- Gauge: `ACTIVE_RUNS.inc()` on start, `.dec()` in `finally`.
- Scrape config in `ops/prometheus.yml`; add `prometheus` + `grafana` services to `docker-compose.yml`.
- Grafana dashboard JSON: import the "FastAPI" community dashboard (ID 14282) and tweak.
</details>

### 13-B Nightly-solve cron

Build a "nightly regression" job: at 03:00 UTC daily, pick 5 instances from a benchmark set, solve them against both APIs, and write the results to `runs/nightly/YYYY-MM-DD.json`. If any solve times out or produces a worse objective than yesterday, fail the job and open a GitHub issue.

<details><summary>Hint</summary>

- GH Actions `schedule: - cron: "0 3 * * *"`.
- Spin up a separate ubuntu runner that doesn't need Docker — just `curl` both Fly URLs.
- Compare yesterday's JSON to today's via `jq`.
- Use `actions/github-script@v7` to `github.rest.issues.create(...)` with a templated body listing regressions.
- For Fly to always accept traffic at 03:00, set `min_machines_running = 1` (costs a bit more) or warm them up with a ping right before the run.
</details>

### 13-C Blue/green deploy on Fly

Zero-downtime deploys by running two app versions side by side, then flipping traffic. Write a short runbook for your own project.

<details><summary>Hint</summary>

- Fly already does rolling deploys by default — for most apps that's enough.
- For true blue/green: deploy to `nsp-py-api-green` as a separate app, health-check it externally, then update DNS/LB to point to green. `fly apps clone` is helpful.
- Keep both deployed for 24h; cut over by `fly deploy` to the primary app pointed at the green image tag; keep blue for 48h as rollback insurance.
- This is overkill for a demo — exercise mostly for the muscle memory.
</details>

### 13-D Load test with k6

Write a `k6` script that hits `POST /runs` on both APIs with 20 concurrent users, 5-minute ramp-up, measuring p95 latency and error rate. Compare the two.

<details><summary>Hint</summary>

```javascript
// /ops/k6/solve_load.js
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '1m', target: 20 },
    { duration: '3m', target: 20 },
    { duration: '1m', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
  },
};

const INSTANCE = JSON.parse(open('./toy-instance.json'));
const TARGET = __ENV.TARGET || 'https://nsp-py-api.fly.dev';

export default function () {
  const res = http.post(`${TARGET}/runs`, JSON.stringify({
    instance: INSTANCE,
    time_limit_s: 10,
  }), { headers: { 'Content-Type': 'application/json' } });
  check(res, { 'status 201': (r) => r.status === 201 });
  sleep(1);
}
```

Run: `k6 run --env TARGET=https://nsp-kt-api.fly.dev ops/k6/solve_load.js`. Note wall time differs between backends — Kotlin warms up slower but is steadier at peak.
</details>

### 13-E Trace a real bug

Deliberately break something: set `CPSAT_NUM_WORKERS=1` in the Python API, trigger a solve on a hard instance, watch it slow down. Use Jaeger to diagnose without reading any application code. Write up what you saw in `/docs/knowledge/ops/2026-04-first-incident.md`.

<details><summary>Hint</summary>

- Induce the slowdown, trigger a solve.
- Open Jaeger → service `py-api` → find your trace.
- Span tree: `POST /runs` (parent) → `solve.cp_sat` → `solve.cp_sat.invoke`.
- Attribute `solve.branches` is 10× higher than on a healthy run. That's the smoking gun: the solver is doing more work because it has fewer workers exploring in parallel.
- Lesson: if you only had logs, you'd compare two `solve complete in 47.2s` lines and not know why. With traces, the diagnosis is one query.
</details>

## §14 Self-check

1. Why does a multi-stage Dockerfile produce smaller images than a single-stage one?

   <details><summary>Answer</summary>

   The build stage contains everything needed to compile/install (compilers, SDKs, test tools, caches). The runtime stage starts from a clean minimal base and only copies the final artifacts. None of the build tools make it to the runtime image — smaller size, smaller attack surface, faster pulls.
   </details>

2. What invariant must hold for `--mount=type=cache` to speed up Gradle builds, and what breaks it?

   <details><summary>Answer</summary>

   The cache is per-builder (per CI runner or per laptop). It persists across builds on the same machine but isn't shared across CI runners. On GH Actions, swap to `cache-from: type=gha` which uses the GitHub cache action as the backing store — that's network-mounted and shared across runners.
   </details>

3. Why does `HEALTHCHECK` matter, and what happens without it?

   <details><summary>Answer</summary>

   Orchestrators (Fly, Kubernetes, compose) use it to know whether a container is actually serving traffic. Without it, a container that booted but deadlocked mid-startup looks healthy. `HEALTHCHECK` + `depends_on: condition: service_healthy` in compose means dependents wait for real readiness. On Fly, failing health checks trigger automatic restarts.
   </details>

4. How does OpenTelemetry tie a UI click to a CP-SAT span?

   <details><summary>Answer</summary>

   Auto-instrumentation on the browser SDK (or the API-gateway's nginx) generates a `traceparent` header with a unique trace ID. Every downstream HTTP client adds the same header. When each service records a span, it includes the trace ID as the parent. The collector assembles all spans sharing a trace ID into one waterfall. The UI click → API call → solver → response all share the same root trace.
   </details>

5. Why does the deploy job push to GHCR but Fly pulls from GHCR — couldn't we push straight to Fly's registry?

   <details><summary>Answer</summary>

   You can (`fly deploy` accepts local images and pushes to Fly's registry). Splitting push (GHCR) from deploy (Fly) gives two wins: (1) the image is an artifact anyone can pull — useful for local debugging or a future migration off Fly; (2) deploy is just "retag and redeploy", no rebuild, no risk of the deploy producing a different image than CI tested.
   </details>

## §15 What this unlocks

You shipped it. The app you built across chapters 14–16 is now pullable as three images, composes locally with one command, traces end-to-end, deploys on `git push`, and has a runbook for when things go wrong. That's a full production stack for a CP-SAT app.

The next (and final) chapter steps back from CP-SAT and looks sideways. You'll port NSP v1 to **Timefold** and **Choco** — two different approaches to optimization — and write the retrospective on your whole deep dive. That's chapter 18.

## §16 Further reading

- Docker, Inc. — ["Best practices for writing Dockerfiles"](https://docs.docker.com/develop/develop-images/dockerfile_best-practices/). The canonical reference; re-read when in doubt.
- BuildKit team — ["Dockerfile reference: RUN --mount"](https://docs.docker.com/reference/dockerfile/#run---mount). Cache mounts deserve a careful read.
- Fly.io — ["Hands-on with Fly"](https://fly.io/docs/hands-on/). Walk through the full tutorial even if you know Docker; Fly's machine model is worth internalizing.
- OpenTelemetry — ["Getting Started with Python"](https://opentelemetry.io/docs/languages/python/getting-started/) and ["Getting Started with Java"](https://opentelemetry.io/docs/languages/java/getting-started/). Both are 20-min reads with runnable examples.
- Charity Majors — ["Observability, A 3-Year Retrospective"](https://charity.wtf/2021/02/22/observability-a-3-year-retrospective/). The "why" behind OTel, from someone who helped invent the word.
- Brendan Gregg — ["USE Method"](https://www.brendangregg.com/usemethod.html). When your metrics fire, walk Utilization/Saturation/Errors — it'll save you hours.
- GitHub — ["Docker metadata action"](https://github.com/marketplace/actions/docker-metadata-action). If you want richer image tagging (semver, git sha prefix, latest on main), plug this in before `build-push-action`.
- [ADR 0007](../knowledge/decisions/0007-containerization-strategy.md) — the call to use multi-stage Dockerfiles + compose instead of buildpacks or Nix.
