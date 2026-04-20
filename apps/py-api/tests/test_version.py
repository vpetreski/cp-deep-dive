from __future__ import annotations

import pytest
from httpx import AsyncClient


@pytest.mark.asyncio
async def test_version_reports_fields(client: AsyncClient) -> None:
    r = await client.get("/version")
    assert r.status_code == 200
    body = r.json()
    assert body["version"] == "1.0.0"
    assert isinstance(body["ortools"], str)
    assert body["ortools"]  # non-empty
    assert body["runtime"].startswith("python ")
    assert body["service"] == "py-api"
