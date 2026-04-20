# tools/

Standalone utilities — each is runnable via `uv run python tools/<script>.py`.

## Scripts

### `validate-schedule.py`

Validate a produced schedule against an NSP instance (hard constraints only).

```bash
uv run python tools/validate-schedule.py \
    --instance data/nsp/toy-01.json \
    --schedule path/to/schedule.json
```

Exit codes:
- `0` — schedule passes every hard constraint
- `1` — at least one violation reported

Schedule JSON format:

```json
{
  "assignments": [
    {"nurseId": "N1", "day": 0, "shiftId": "D"},
    {"nurseId": "N2", "day": 0, "shiftId": "N"}
  ]
}
```

### Setup scripts (already on this path)

- `setup-memory-link.sh` — symlink project memory (run once per machine)
- `setup-qmd-hook.sh` — install QMD post-commit reindex hook (run once per machine)
- `setup-all.sh` — convenience wrapper that runs every `setup-*.sh` in order
  and prints what to do next
