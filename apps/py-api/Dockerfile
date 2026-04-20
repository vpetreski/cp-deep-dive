# syntax=docker/dockerfile:1.7-labs
# Multi-stage Dockerfile for cp-deep-dive py-api.
#
# Stage 1: install uv + sync dependencies into a venv.
# Stage 2: copy the venv + source into a slim runtime image.

# -----------------------------------------------------------------------------
# Stage 1 — build
# -----------------------------------------------------------------------------
FROM python:3.12-slim AS builder

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    UV_COMPILE_BYTECODE=1 \
    UV_LINK_MODE=copy \
    UV_PROJECT_ENVIRONMENT=/opt/venv

# Install uv from its official slim image — zero system deps, no curl download.
COPY --from=ghcr.io/astral-sh/uv:latest /uv /uvx /usr/local/bin/

WORKDIR /app

# Copy lockfile + manifest first for better layer caching.
COPY pyproject.toml ./
COPY src ./src

# Sync deps into /opt/venv. No dev group in runtime.
RUN uv sync --no-dev --no-editable

# -----------------------------------------------------------------------------
# Stage 2 — runtime
# -----------------------------------------------------------------------------
FROM python:3.12-slim AS runtime

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PATH="/opt/venv/bin:$PATH"

# Copy the venv + source from the builder.
COPY --from=builder /opt/venv /opt/venv
COPY --from=builder /app/src /app/src

WORKDIR /app

EXPOSE 8000

# Use uvicorn directly from the venv.
CMD ["uvicorn", "py_api.main:app", "--host", "0.0.0.0", "--port", "8000"]
