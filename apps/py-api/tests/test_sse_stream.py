from __future__ import annotations

import asyncio
import json

import pytest
from httpx import AsyncClient


@pytest.mark.asyncio
async def test_stream_emits_solution_event(
    client: AsyncClient, toy_01_body: dict
) -> None:
    # Submit a solve.
    r = await client.post(
        "/solve", json={"instance": toy_01_body, "params": {"timeLimitSeconds": 10}}
    )
    assert r.status_code == 202
    job_id = r.json()["jobId"]

    # Consume the SSE stream and look for at least one `event: solution`.
    url = f"/solutions/{job_id}/stream"
    async with client.stream("GET", url, timeout=30.0) as resp:
        assert resp.status_code == 200
        assert resp.headers["content-type"].startswith("text/event-stream")
        saw_solution = False
        data_seen: list[dict] = []
        async for raw_line in resp.aiter_lines():
            line = raw_line.rstrip("\n")
            if line.startswith("event: solution"):
                saw_solution = True
            elif line.startswith("data: "):
                try:
                    body = json.loads(line[len("data: "):])
                    data_seen.append(body)
                except json.JSONDecodeError:
                    continue
            if saw_solution and any(
                b.get("status") in {"feasible", "optimal", "infeasible",
                                    "cancelled", "error", "unknown"}
                for b in data_seen
            ):
                break
        assert saw_solution
        assert any(b.get("jobId") == job_id for b in data_seen)


@pytest.mark.asyncio
async def test_stream_404_for_unknown_job(client: AsyncClient) -> None:
    r = await client.get("/solutions/does-not-exist/stream")
    assert r.status_code == 404
    assert r.json()["code"] == "job.notFound"


@pytest.mark.asyncio
async def test_stream_replays_terminal_for_finished_job(
    client: AsyncClient, toy_01_body: dict
) -> None:
    # Run a solve to completion, then open the stream — should still get one event.
    r = await client.post(
        "/solve", json={"instance": toy_01_body, "params": {"timeLimitSeconds": 5}}
    )
    job_id = r.json()["jobId"]
    # Wait for terminal.
    for _ in range(100):
        r = await client.get(f"/solution/{job_id}")
        if r.json().get("status") in {
            "feasible",
            "optimal",
            "infeasible",
            "unknown",
            "timeout",
            "cancelled",
            "error",
        }:
            break
        await asyncio.sleep(0.1)

    async with client.stream("GET", f"/solutions/{job_id}/stream", timeout=10.0) as resp:
        assert resp.status_code == 200
        lines: list[str] = []
        async for raw in resp.aiter_lines():
            lines.append(raw)
            # Break after seeing any solution event.
            joined = "\n".join(lines)
            if "event: solution" in joined:
                break
        assert any(line.startswith("event: solution") for line in lines)
