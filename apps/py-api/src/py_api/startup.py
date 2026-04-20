"""One-time app-startup tasks: DB schema, seed data, solver pool bootstrap."""

from __future__ import annotations

import json
import logging

from nsp_core.loader import parse_instance
from sqlmodel import select

from py_api.config import Settings
from py_api.db import Database, InstanceRow
from py_api.serialize import instance_to_dict

log = logging.getLogger("py_api.startup")


async def seed_instances(db: Database, settings: Settings) -> None:
    """Load ``data/nsp/toy-*.json`` into the DB if not already present."""
    seed_dir = settings.seed_dir
    if not seed_dir.is_dir():
        log.info("seed dir %s missing — skipping", seed_dir)
        return
    for path in sorted(seed_dir.glob("toy-*.json")):
        try:
            raw = json.loads(path.read_text())
        except json.JSONDecodeError as exc:  # pragma: no cover
            log.warning("seed %s: invalid JSON (%s)", path, exc)
            continue
        try:
            inst = parse_instance(raw)
        except Exception as exc:  # pragma: no cover
            log.warning("seed %s: schema/load error (%s)", path, exc)
            continue
        canonical = instance_to_dict(inst)
        async with db.session() as sess:
            existing_id = (
                await sess.execute(select(InstanceRow.id).where(InstanceRow.id == inst.id))
            ).scalar_one_or_none()
            if existing_id is not None:
                continue
            row = InstanceRow(
                id=inst.id,
                name=inst.name,
                source=inst.source,
                horizon_days=inst.horizon_days,
                nurse_count=len(inst.nurses),
                shift_count=len(inst.shifts),
                coverage_slot_count=len(inst.coverage),
                raw_json=canonical,
            )
            sess.add(row)
            await sess.commit()
        log.info("seeded instance %s from %s", inst.id, path.name)
