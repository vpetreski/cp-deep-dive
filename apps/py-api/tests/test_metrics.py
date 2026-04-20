from __future__ import annotations

import pytest
from httpx import AsyncClient


@pytest.mark.asyncio
async def test_metrics_prometheus_text_format(client: AsyncClient) -> None:
    r = await client.get("/metrics")
    assert r.status_code == 200
    assert r.headers["content-type"].startswith("text/plain")
    body = r.text
    # Prometheus exposition format signatures.
    assert "# HELP" in body
    assert "# TYPE" in body
    assert "nsp_solve_total" in body
    assert "nsp_instances_total" in body
