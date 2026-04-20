from __future__ import annotations

import pytest
from httpx import AsyncClient


@pytest.mark.asyncio
async def test_malformed_json_returns_400(client: AsyncClient) -> None:
    r = await client.post(
        "/instances",
        content=b"not really json",
        headers={"content-type": "application/json"},
    )
    assert r.status_code == 400
    body = r.json()
    assert body["code"] == "request.malformed"
    assert "message" in body


@pytest.mark.asyncio
async def test_unknown_path_returns_404_error_envelope(client: AsyncClient) -> None:
    r = await client.get("/does-not-exist")
    assert r.status_code == 404
    body = r.json()
    # FastAPI's default 404 goes through the envelope handler.
    assert "code" in body
    assert "message" in body


@pytest.mark.asyncio
async def test_semantic_invalid_instance_returns_422(client: AsyncClient) -> None:
    bad = {"horizonDays": 1, "shifts": [], "nurses": [], "coverage": []}
    # Empty shifts array fails schema minItems:1 — InstanceValidationError → 422.
    r = await client.post("/instances", json=bad)
    assert r.status_code == 422
    body = r.json()
    assert body["code"] == "instance.invalid"
    # details.errors carries the schema failure list.
    assert "details" in body
    assert "errors" in body["details"]


@pytest.mark.asyncio
async def test_instance_body_must_be_object(client: AsyncClient) -> None:
    r = await client.post(
        "/instances",
        content=b"[1,2,3]",
        headers={"content-type": "application/json"},
    )
    assert r.status_code == 400
    body = r.json()
    assert body["code"] == "request.malformed"
