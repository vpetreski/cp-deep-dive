# NSP instance data

JSON files describing concrete Nurse Scheduling Problem (NSP) instances, plus the
JSON Schema (2020-12) they conform to.

## Files

| File | Description |
|---|---|
| `schema.json` | JSON Schema 2020-12 for NSP instances |
| `toy-01.json` | 3 nurses × 7 days × 2 shifts (D, N). Feasible. Used by Chapter 10 / early Chapter 11 tests. |
| `toy-02.json` | 5 nurses × 14 days × 3 shifts (M, D, N). Feasible. Used in Chapter 11 as the "hard constraints only" target. |

Larger benchmark instances (NSPLib, INRC-I, INRC-II) live under subfolders created in
Chapter 13.

## Format (informal)

```jsonc
{
  "horizonDays": 7,                         // integer, >= 1
  "shifts": [                               // fixed-window shifts
    {"id": "D", "name": "Day",   "start": "07:00", "end": "19:00"},
    {"id": "N", "name": "Night", "start": "19:00", "end": "07:00"}
  ],
  "nurses": [
    {"id": "N1", "name": "Alice", "skills": ["general"], "contractHoursPerWeek": 36}
  ],
  "demand": [                                // required coverage per (day, shift)
    {"day": 0, "shiftId": "D", "min": 1, "max": 2, "requiredSkills": []}
  ],
  "forbiddenTransitions": [["N", "D"]],      // shift s1 at day d then s2 at day d+1 is illegal
  "minRestHours": 11,                        // EU Working Time Directive
  "maxConsecutiveWorkingDays": 5,
  "preferences": [                           // soft — ignored by v1 solvers
    {"nurseId": "N1", "day": 5, "shiftId": "N", "weight": -10}
  ],
  "fixedOff": [                              // hard — nurse n MUST be off on day d
    {"nurseId": "N1", "day": 4}
  ]
}
```

Day indices are 0-based: `0 == Monday` of week 0, wrapping weekly (so `day % 7 == 5` is
Saturday, `day % 7 == 6` is Sunday). Shift times are informational for the v1/v2
solvers; the Boolean shift-grid model uses `forbiddenTransitions` + `minRestHours` to
encode rest.

See [`docs/knowledge/nurse-scheduling/overview.md`](../../docs/knowledge/nurse-scheduling/overview.md)
§ 3–§ 4 for how these fields map to CP-SAT constraints.

## Validate

```bash
uv run python -c "
import json, jsonschema
inst = json.load(open('data/nsp/toy-01.json'))
schema = json.load(open('data/nsp/schema.json'))
jsonschema.validate(inst, schema)
print('OK')
"
```
