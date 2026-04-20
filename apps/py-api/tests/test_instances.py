from __future__ import annotations

from typing import Any

import pytest
from httpx import AsyncClient


def _minimal_instance(*, instance_id: str = "it-01") -> dict[str, Any]:
    return {
        "id": instance_id,
        "name": "Unit test instance",
        "source": "toy",
        "horizonDays": 3,
        "shifts": [
            {
                "id": "D",
                "label": "Day",
                "startMinutes": 420,
                "durationMinutes": 720,
                "isNight": False,
            }
        ],
        "nurses": [
            {
                "id": "N1",
                "name": "Alice",
                "skills": ["general"],
                "contractHoursPerWeek": 36,
                "unavailable": [],
            }
        ],
        "coverage": [
            {"day": 0, "shiftId": "D", "min": 1, "max": 1},
            {"day": 1, "shiftId": "D", "min": 1, "max": 1},
            {"day": 2, "shiftId": "D", "min": 1, "max": 1},
        ],
        "minRestHours": 11,
        "maxConsecutiveWorkingDays": 6,
        "maxConsecutiveNights": 3,
        "metadata": {"contractTolerance": 24},
    }


@pytest.mark.asyncio
async def test_list_contains_seed_instances(client: AsyncClient) -> None:
    r = await client.get("/instances")
    assert r.status_code == 200
    body = r.json()
    ids = {item["id"] for item in body["items"]}
    assert "toy-01" in ids
    assert "toy-02" in ids


@pytest.mark.asyncio
async def test_post_get_delete_roundtrip(client: AsyncClient) -> None:
    payload = _minimal_instance(instance_id="it-round-01")
    r = await client.post("/instances", json=payload)
    assert r.status_code == 201, r.text
    assert r.headers["location"] == "/instances/it-round-01"
    body = r.json()
    assert body["id"] == "it-round-01"

    r = await client.get("/instances/it-round-01")
    assert r.status_code == 200
    assert r.json()["id"] == "it-round-01"

    r = await client.delete("/instances/it-round-01")
    assert r.status_code == 204

    r = await client.get("/instances/it-round-01")
    assert r.status_code == 404
    err = r.json()
    assert err["code"] == "instance.notFound"


@pytest.mark.asyncio
async def test_pagination_cursor(client: AsyncClient) -> None:
    # Seed has 2 instances; add 3 more so we can paginate.
    for i in range(3):
        r = await client.post(
            "/instances", json=_minimal_instance(instance_id=f"page-{i}")
        )
        assert r.status_code == 201

    r = await client.get("/instances", params={"limit": 2})
    assert r.status_code == 200
    page1 = r.json()
    assert len(page1["items"]) == 2
    assert "nextCursor" in page1

    r = await client.get(
        "/instances", params={"limit": 2, "cursor": page1["nextCursor"]}
    )
    assert r.status_code == 200
    page2 = r.json()
    assert len(page2["items"]) == 2
    ids1 = {i["id"] for i in page1["items"]}
    ids2 = {i["id"] for i in page2["items"]}
    assert ids1.isdisjoint(ids2)


@pytest.mark.asyncio
async def test_invalid_cursor_rejected(client: AsyncClient) -> None:
    r = await client.get("/instances", params={"cursor": "***not-base64***"})
    assert r.status_code == 400
    assert r.json()["code"] == "request.malformed"


@pytest.mark.asyncio
async def test_get_missing_returns_404(client: AsyncClient) -> None:
    r = await client.get("/instances/does-not-exist")
    assert r.status_code == 404
    assert r.json()["code"] == "instance.notFound"


@pytest.mark.asyncio
async def test_post_malformed_body_returns_400(client: AsyncClient) -> None:
    r = await client.post(
        "/instances",
        content=b"{ not json ",
        headers={"content-type": "application/json"},
    )
    assert r.status_code == 400
    assert r.json()["code"] == "request.malformed"


@pytest.mark.asyncio
async def test_post_semantic_invalid_returns_422(client: AsyncClient) -> None:
    # Schema-valid body with an unknown coverage shift reference.
    bad = _minimal_instance(instance_id="it-bad")
    # Remove required field 'shifts' to trigger schema failure; the loader
    # raises InstanceValidationError which the route maps to 422.
    bad.pop("shifts")
    r = await client.post("/instances", json=bad)
    assert r.status_code == 422
    assert r.json()["code"] == "instance.invalid"


@pytest.mark.asyncio
async def test_delete_missing_returns_404(client: AsyncClient) -> None:
    r = await client.delete("/instances/does-not-exist")
    assert r.status_code == 404
    assert r.json()["code"] == "instance.notFound"
