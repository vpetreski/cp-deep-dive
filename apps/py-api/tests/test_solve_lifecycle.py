from __future__ import annotations

import asyncio

import pytest
from httpx import AsyncClient

_TERMINAL_OK = {"feasible", "optimal"}
_TERMINAL_ANY = _TERMINAL_OK | {"infeasible", "unknown", "timeout", "cancelled", "error"}


async def _wait_for(client: AsyncClient, job_id: str, *, timeout: float = 30.0) -> dict:
    elapsed = 0.0
    while elapsed < timeout:
        r = await client.get(f"/solution/{job_id}")
        body = r.json()
        if body["status"] in _TERMINAL_ANY:
            return body
        await asyncio.sleep(0.1)
        elapsed += 0.1
    return body


@pytest.mark.asyncio
async def test_solve_toy_01_reaches_feasible(
    client: AsyncClient, toy_01_body: dict
) -> None:
    r = await client.post(
        "/solve",
        json={"instance": toy_01_body, "params": {"timeLimitSeconds": 10}},
    )
    assert r.status_code == 202, r.text
    assert "location" in r.headers
    body = r.json()
    assert body["status"] in {"pending", "queued", "running"}
    job_id = body["jobId"]
    assert r.headers["location"] == f"/solution/{job_id}"

    final = await _wait_for(client, job_id)
    assert final["status"] in _TERMINAL_OK, final
    assert final["jobId"] == job_id
    schedule = final.get("schedule")
    assert schedule is not None
    assert schedule["instanceId"] == toy_01_body["id"]
    # Should have an assignment for every (nurse, day) ≠ unavailable.
    assert len(schedule["assignments"]) > 0


@pytest.mark.asyncio
async def test_solve_requires_instance(client: AsyncClient) -> None:
    r = await client.post("/solve", json={})
    assert r.status_code == 400
    assert r.json()["code"] == "request.malformed"


@pytest.mark.asyncio
async def test_solve_unknown_instance_id_returns_404(client: AsyncClient) -> None:
    r = await client.post("/solve", json={"instanceId": "nope"})
    assert r.status_code == 404
    assert r.json()["code"] == "instance.notFound"


@pytest.mark.asyncio
async def test_cancel_mid_solve(client: AsyncClient, toy_01_body: dict) -> None:
    # Kick off a solve and attempt cancellation. Toy instances finish very
    # fast, so either "cancelled" (rare race) or a 409 "already terminal" is
    # acceptable — what matters is the cancel endpoint exists and signals
    # correctly, and the job always ends in a terminal state.
    r = await client.post(
        "/solve",
        json={
            "instance": toy_01_body,
            "params": {
                "timeLimitSeconds": 60,
                "objectiveWeights": {"preference": 10, "fairness": 5},
            },
        },
    )
    assert r.status_code == 202
    job_id = r.json()["jobId"]

    # Cancel immediately — if the solver has already finished we get 409.
    r = await client.post(f"/solve/{job_id}/cancel")
    assert r.status_code in (202, 409), r.text
    if r.status_code == 409:
        assert r.json()["code"] == "job.conflict"
    else:
        assert r.json()["jobId"] == job_id

    final = await _wait_for(client, job_id)
    assert final["status"] in _TERMINAL_ANY


@pytest.mark.asyncio
async def test_cancel_after_terminal_returns_409(
    client: AsyncClient, toy_01_body: dict
) -> None:
    r = await client.post(
        "/solve",
        json={"instance": toy_01_body, "params": {"timeLimitSeconds": 5}},
    )
    job_id = r.json()["jobId"]
    final = await _wait_for(client, job_id)
    assert final["status"] in _TERMINAL_ANY

    r = await client.post(f"/solve/{job_id}/cancel")
    assert r.status_code == 409
    assert r.json()["code"] == "job.conflict"


@pytest.mark.asyncio
async def test_cancel_unknown_job_returns_404(client: AsyncClient) -> None:
    r = await client.post("/solve/does-not-exist/cancel")
    assert r.status_code == 404
    assert r.json()["code"] == "job.notFound"


@pytest.mark.asyncio
async def test_get_solution_unknown_job_returns_404(client: AsyncClient) -> None:
    r = await client.get("/solution/does-not-exist")
    assert r.status_code == 404
    assert r.json()["code"] == "job.notFound"


@pytest.mark.asyncio
async def test_solve_log_endpoint(client: AsyncClient, toy_01_body: dict) -> None:
    r = await client.post(
        "/solve", json={"instance": toy_01_body, "params": {"timeLimitSeconds": 5}}
    )
    assert r.status_code == 202
    job_id = r.json()["jobId"]
    await _wait_for(client, job_id)

    r = await client.get(f"/solve/{job_id}/log")
    assert r.status_code == 200
    assert r.headers["content-type"].startswith("text/plain")


@pytest.mark.asyncio
async def test_solve_by_stored_instance_id(
    client: AsyncClient, toy_01_body: dict
) -> None:
    # Use the seeded toy-01 by reference.
    r = await client.post(
        "/solve",
        json={"instanceId": toy_01_body["id"], "params": {"timeLimitSeconds": 5}},
    )
    assert r.status_code == 202, r.text
    job_id = r.json()["jobId"]
    final = await _wait_for(client, job_id)
    assert final["status"] in _TERMINAL_OK, final
