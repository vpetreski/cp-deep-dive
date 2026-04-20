import { useMemo } from "react";
import type { NspInstance, Schedule } from "~/lib/types";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "~/components/ui/card";
import { shiftDurationHours } from "~/lib/format";

interface CoverageCell {
  day: number;
  shiftId: string;
  required: number;
  filled: number;
}

export interface SummaryStatsProps {
  instance: NspInstance;
  schedule: Schedule;
}

export function SummaryStats({ instance, schedule }: SummaryStatsProps) {
  const stats = useMemo(() => compute(instance, schedule), [instance, schedule]);

  return (
    <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
      <StatCard title="Coverage fulfilment">
        <div className="flex items-baseline gap-2">
          <span className="text-2xl font-semibold">
            {stats.coveragePercent.toFixed(1)}%
          </span>
          <span className="text-xs text-muted-foreground">
            {stats.coverageFilled} / {stats.coverageRequired} slots
          </span>
        </div>
        <p className="mt-1 text-xs text-muted-foreground">
          Proportion of required (day, shift) cells that have enough nurses.
        </p>
      </StatCard>

      <StatCard title="Hours balance">
        <div className="flex items-baseline gap-2">
          <span className="text-2xl font-semibold">
            {stats.hoursRange.toFixed(1)} h
          </span>
          <span className="text-xs text-muted-foreground">
            max − min across nurses
          </span>
        </div>
        <p className="mt-1 text-xs text-muted-foreground">
          Range of scheduled hours. Lower = more fair.
        </p>
      </StatCard>

      <StatCard title="Preferences honoured">
        <div className="flex items-baseline gap-2">
          <span className="text-2xl font-semibold">
            {stats.prefTotal === 0
              ? "—"
              : `${stats.prefPercent.toFixed(1)}%`}
          </span>
          <span className="text-xs text-muted-foreground">
            {stats.prefHonoured} / {stats.prefTotal}
          </span>
        </div>
        <p className="mt-1 text-xs text-muted-foreground">
          Weighted nurse preferences (SC-1) satisfied.
        </p>
      </StatCard>
    </div>
  );
}

function StatCard({
  title,
  children,
}: {
  title: string;
  children: React.ReactNode;
}) {
  return (
    <Card size="sm">
      <CardHeader>
        <CardTitle className="text-xs font-medium uppercase tracking-wider text-muted-foreground">
          {title}
        </CardTitle>
      </CardHeader>
      <CardContent className="pb-3">{children}</CardContent>
    </Card>
  );
}

interface ComputedStats {
  coverageRequired: number;
  coverageFilled: number;
  coveragePercent: number;
  hoursRange: number;
  prefHonoured: number;
  prefTotal: number;
  prefPercent: number;
}

function compute(instance: NspInstance, schedule: Schedule): ComputedStats {
  // Coverage fill
  const filledByCell = new Map<string, number>();
  for (const a of schedule.assignments) {
    if (!a.shiftId) continue;
    const key = `${a.day}:${a.shiftId}`;
    filledByCell.set(key, (filledByCell.get(key) ?? 0) + 1);
  }
  const cells: CoverageCell[] = instance.coverage.map((c) => ({
    day: c.day,
    shiftId: c.shiftId,
    required: c.required,
    filled: filledByCell.get(`${c.day}:${c.shiftId}`) ?? 0,
  }));
  const coverageRequired = cells.reduce((s, c) => s + c.required, 0);
  const coverageFilled = cells.reduce(
    (s, c) => s + Math.min(c.filled, c.required),
    0,
  );
  const coveragePercent =
    coverageRequired === 0 ? 100 : (coverageFilled / coverageRequired) * 100;

  // Hours per nurse
  const hoursPerNurse = new Map<string, number>();
  for (const n of instance.nurses) hoursPerNurse.set(n.id, 0);
  const shiftIndex = new Map(instance.shifts.map((s) => [s.id, s] as const));
  for (const a of schedule.assignments) {
    if (!a.shiftId) continue;
    const s = shiftIndex.get(a.shiftId);
    if (!s) continue;
    const h = shiftDurationHours(s);
    hoursPerNurse.set(a.nurseId, (hoursPerNurse.get(a.nurseId) ?? 0) + h);
  }
  const hoursValues = [...hoursPerNurse.values()];
  const hoursRange =
    hoursValues.length === 0
      ? 0
      : Math.max(...hoursValues) - Math.min(...hoursValues);

  // Preferences (scan per-nurse nested preferences)
  let prefHonoured = 0;
  let prefTotal = 0;
  const assignmentIndex = new Map<string, string | null>();
  for (const a of schedule.assignments) {
    assignmentIndex.set(`${a.nurseId}:${a.day}`, a.shiftId ?? null);
  }
  for (const n of instance.nurses) {
    for (const p of n.preferences ?? []) {
      prefTotal += 1;
      const actual = assignmentIndex.get(`${n.id}:${p.day}`);
      const wantsShift = p.weight > 0 && actual === p.shiftId;
      const avoidsShift = p.weight < 0 && actual !== p.shiftId;
      if (wantsShift || avoidsShift) prefHonoured += 1;
    }
  }
  const prefPercent =
    prefTotal === 0 ? 100 : (prefHonoured / prefTotal) * 100;

  return {
    coverageRequired,
    coverageFilled,
    coveragePercent,
    hoursRange,
    prefHonoured,
    prefTotal,
    prefPercent,
  };
}
