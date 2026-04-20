from __future__ import annotations

import pathlib

import pytest
from httpx import AsyncClient


@pytest.mark.asyncio
async def test_openapi_yaml_byte_identical(client: AsyncClient, repo_root_path: str) -> None:
    r = await client.get("/openapi.yaml")
    assert r.status_code == 200
    assert r.headers["content-type"].startswith("application/yaml")
    served = r.content
    source = (pathlib.Path(repo_root_path) / "apps" / "shared" / "openapi.yaml").read_bytes()
    assert served == source
