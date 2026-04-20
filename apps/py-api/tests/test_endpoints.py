"""Endpoint tests for py-api using FastAPI's TestClient (httpx under the hood)."""

from __future__ import annotations

from fastapi.testclient import TestClient

from py_api.main import app

client = TestClient(app)


def test_health_ok() -> None:
    response = client.get("/health")
    assert response.status_code == 200
    body = response.json()
    assert body == {"status": "ok", "service": "py-api"}


def test_version_reports_ortools() -> None:
    response = client.get("/version")
    assert response.status_code == 200
    body = response.json()
    assert body["version"] == "0.1.0"
    # OR-Tools version string is "X.Y.Z" or "X.Y.Z.devN"; minimally non-empty.
    assert isinstance(body["ortools"], str)
    assert body["ortools"]


def test_solve_stub_returns_501() -> None:
    response = client.post("/solve", json={})
    assert response.status_code == 501
    assert response.json() == {"todo": "Phase 7 Chapter 15"}


def test_solution_stub_returns_501() -> None:
    response = client.get("/solution/abc-123")
    assert response.status_code == 501
    assert response.json() == {"todo": "Phase 7 Chapter 15"}
