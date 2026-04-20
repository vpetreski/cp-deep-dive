from __future__ import annotations

import pytest
from httpx import AsyncClient


@pytest.mark.asyncio
async def test_same_key_same_body_returns_same_job(
    client: AsyncClient, toy_01_body: dict
) -> None:
    body = {"instance": toy_01_body, "params": {"timeLimitSeconds": 5}}
    key = "idem-AAA-111"
    r1 = await client.post("/solve", json=body, headers={"Idempotency-Key": key})
    assert r1.status_code == 202
    job1 = r1.json()["jobId"]

    r2 = await client.post("/solve", json=body, headers={"Idempotency-Key": key})
    assert r2.status_code == 202
    job2 = r2.json()["jobId"]
    assert job1 == job2


@pytest.mark.asyncio
async def test_same_key_different_body_returns_409(
    client: AsyncClient, toy_01_body: dict
) -> None:
    key = "idem-BBB-222"
    r1 = await client.post(
        "/solve",
        json={"instance": toy_01_body, "params": {"timeLimitSeconds": 3}},
        headers={"Idempotency-Key": key},
    )
    assert r1.status_code == 202

    altered = {**toy_01_body, "name": "modified-different-body"}
    r2 = await client.post(
        "/solve",
        json={"instance": altered, "params": {"timeLimitSeconds": 3}},
        headers={"Idempotency-Key": key},
    )
    assert r2.status_code == 409
    err = r2.json()
    assert err["code"] == "job.conflict"
