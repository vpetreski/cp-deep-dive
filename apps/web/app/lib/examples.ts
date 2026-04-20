import type { NspInstance } from "./types";

/**
 * Bundled example instances for offline / no-backend exploration.
 *
 * These mirror data/nsp/toy-01.json and toy-02.json, translated to the
 * wire-format shape (apps/shared/schemas/nsp-instance.schema.json). The toy
 * schema in data/nsp/ uses `demand/min/max` + `shift.start/end`; the wire
 * format uses `coverage/required` + `startMinutes/durationMinutes`.
 */

function hhmmToMinutes(hhmm: string): number {
  const [h, m] = hhmm.split(":").map((s) => parseInt(s, 10));
  return h * 60 + m;
}

function durationMinutes(start: string, end: string): number {
  const s = hhmmToMinutes(start);
  const e = hhmmToMinutes(end);
  return e >= s ? e - s : 24 * 60 - s + e;
}

export const exampleToy01: NspInstance = {
  id: "toy-01",
  name: "Toy 1 — tiny reference ward",
  source: "toy",
  horizonDays: 7,
  shifts: [
    {
      id: "D",
      label: "Day",
      startMinutes: hhmmToMinutes("07:00"),
      durationMinutes: durationMinutes("07:00", "19:00"),
    },
    {
      id: "N",
      label: "Night",
      startMinutes: hhmmToMinutes("19:00"),
      durationMinutes: durationMinutes("19:00", "07:00"),
    },
  ],
  nurses: [
    { id: "N1", name: "Alice", skills: ["general"] },
    { id: "N2", name: "Bob", skills: ["general"] },
    { id: "N3", name: "Carmen", skills: ["general", "pedi"] },
  ],
  coverage: Array.from({ length: 7 }, (_, d) => [
    { day: d, shiftId: "D", required: 1 },
    { day: d, shiftId: "N", required: 1 },
  ]).flat(),
  forbiddenTransitions: [["N", "D"]],
  metadata: { minRestHours: 11, maxConsecutiveWorkingDays: 5 },
};

export const exampleToy02: NspInstance = {
  id: "toy-02",
  name: "Toy 2 — realistic ward",
  source: "toy",
  horizonDays: 14,
  shifts: [
    {
      id: "M",
      label: "Morning",
      startMinutes: hhmmToMinutes("07:00"),
      durationMinutes: durationMinutes("07:00", "15:00"),
    },
    {
      id: "D",
      label: "Day",
      startMinutes: hhmmToMinutes("15:00"),
      durationMinutes: durationMinutes("15:00", "23:00"),
    },
    {
      id: "N",
      label: "Night",
      startMinutes: hhmmToMinutes("23:00"),
      durationMinutes: durationMinutes("23:00", "07:00"),
    },
  ],
  nurses: [
    { id: "N1", name: "Alice", skills: ["general"] },
    { id: "N2", name: "Bob", skills: ["general", "icu"] },
    { id: "N3", name: "Carmen", skills: ["general", "pedi"] },
    { id: "N4", name: "Diego", skills: ["general"] },
    { id: "N5", name: "Elif", skills: ["general", "icu", "senior"] },
  ],
  coverage: Array.from({ length: 14 }, (_, d) => [
    { day: d, shiftId: "M", required: 1 },
    { day: d, shiftId: "D", required: 1 },
    { day: d, shiftId: "N", required: 1 },
  ]).flat(),
  forbiddenTransitions: [
    ["N", "M"],
    ["N", "D"],
    ["D", "M"],
  ],
  metadata: { minRestHours: 11, maxConsecutiveWorkingDays: 5 },
};

export const EXAMPLE_INSTANCES: readonly NspInstance[] = [
  exampleToy01,
  exampleToy02,
];

export function getExampleById(id: string): NspInstance | undefined {
  return EXAMPLE_INSTANCES.find((ex) => ex.id === id);
}
