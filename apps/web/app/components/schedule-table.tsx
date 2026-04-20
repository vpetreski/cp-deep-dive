import { useMemo, useRef, useEffect, useState } from "react";
import type { NspInstance, Schedule, Shift } from "~/lib/types";
import { cn } from "~/lib/utils";
import { shiftClassName, SHIFT_STYLES } from "~/lib/shifts";
import { shiftTimeRange } from "~/lib/format";

type Cell = {
  nurseId: string;
  day: number;
  shiftId: string | null;
};

function buildGrid(
  instance: NspInstance,
  schedule: Schedule,
): Cell[][] {
  const byKey = new Map<string, string | null>();
  for (const a of schedule.assignments) {
    byKey.set(`${a.nurseId}:${a.day}`, a.shiftId ?? null);
  }
  return instance.nurses.map((n) =>
    Array.from({ length: instance.horizonDays }, (_, d) => ({
      nurseId: n.id,
      day: d,
      shiftId: byKey.get(`${n.id}:${d}`) ?? null,
    })),
  );
}

function weekend(day: number): boolean {
  const mod = day % 7;
  return mod === 5 || mod === 6;
}

export interface ScheduleTableProps {
  instance: NspInstance;
  schedule: Schedule;
  className?: string;
}

export function ScheduleTable({
  instance,
  schedule,
  className,
}: ScheduleTableProps) {
  const grid = useMemo(() => buildGrid(instance, schedule), [instance, schedule]);
  const nurseLabel = (id: string) =>
    instance.nurses.find((n) => n.id === id)?.name ?? id;

  const containerRef = useRef<HTMLDivElement>(null);
  const [focus, setFocus] = useState<{ row: number; col: number }>({
    row: 0,
    col: 0,
  });

  useEffect(() => {
    const el = containerRef.current?.querySelector<HTMLTableCellElement>(
      `td[data-row="${focus.row}"][data-col="${focus.col}"]`,
    );
    if (el && document.activeElement?.tagName === "TD") {
      el.focus();
    }
  }, [focus]);

  function onKeyDown(e: React.KeyboardEvent<HTMLTableElement>) {
    const t = e.target as HTMLElement;
    if (t.tagName !== "TD") return;
    const { row, col } = focus;
    const rows = instance.nurses.length;
    const cols = instance.horizonDays;
    let next: { row: number; col: number } | null = null;
    switch (e.key) {
      case "ArrowRight":
        next = { row, col: Math.min(cols - 1, col + 1) };
        break;
      case "ArrowLeft":
        next = { row, col: Math.max(0, col - 1) };
        break;
      case "ArrowDown":
        next = { row: Math.min(rows - 1, row + 1), col };
        break;
      case "ArrowUp":
        next = { row: Math.max(0, row - 1), col };
        break;
      case "Home":
        next = { row, col: 0 };
        break;
      case "End":
        next = { row, col: cols - 1 };
        break;
      default:
        return;
    }
    if (next) {
      e.preventDefault();
      setFocus(next);
      const el = containerRef.current?.querySelector<HTMLTableCellElement>(
        `td[data-row="${next.row}"][data-col="${next.col}"]`,
      );
      el?.focus();
    }
  }

  return (
    <div ref={containerRef} className={cn("relative overflow-auto rounded-xl border border-border bg-card", className)}>
      <table
        className="min-w-full border-collapse text-xs"
        role="grid"
        aria-label="Nurse roster table"
        onKeyDown={onKeyDown}
      >
        <thead>
          <tr>
            <th
              scope="col"
              className="sticky left-0 top-0 z-20 bg-muted/80 px-2 py-2 text-left font-medium backdrop-blur"
            >
              Nurse
            </th>
            {Array.from({ length: instance.horizonDays }, (_, d) => (
              <th
                key={d}
                scope="col"
                className={cn(
                  "sticky top-0 z-10 min-w-9 bg-muted/80 px-1 py-2 text-center font-medium backdrop-blur",
                  weekend(d) && "text-muted-foreground",
                )}
              >
                <div>{d}</div>
                <div className="text-[10px] uppercase tracking-wider text-muted-foreground">
                  {["Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"][d % 7]}
                </div>
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {grid.map((row, rowIdx) => (
            <tr key={rowIdx} className="border-t border-border">
              <th
                scope="row"
                className="sticky left-0 z-10 bg-card px-2 py-1.5 text-left font-medium"
              >
                <div className="flex items-center gap-2">
                  <span className="font-mono text-[10px] text-muted-foreground">
                    {instance.nurses[rowIdx].id}
                  </span>
                  <span className="truncate">
                    {nurseLabel(instance.nurses[rowIdx].id)}
                  </span>
                </div>
              </th>
              {row.map((cell, colIdx) => (
                <ScheduleCell
                  key={colIdx}
                  cell={cell}
                  rowIdx={rowIdx}
                  colIdx={colIdx}
                  shifts={instance.shifts}
                  nurseName={nurseLabel(cell.nurseId)}
                  tabIndex={
                    rowIdx === focus.row && colIdx === focus.col ? 0 : -1
                  }
                  onFocus={() => setFocus({ row: rowIdx, col: colIdx })}
                />
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function ScheduleCell({
  cell,
  rowIdx,
  colIdx,
  shifts,
  nurseName,
  tabIndex,
  onFocus,
}: {
  cell: Cell;
  rowIdx: number;
  colIdx: number;
  shifts: Shift[];
  nurseName: string;
  tabIndex: number;
  onFocus: () => void;
}) {
  const shift = cell.shiftId
    ? shifts.find((s) => s.id === cell.shiftId)
    : undefined;
  const label = shift
    ? `${nurseName}, day ${cell.day}, ${shift.label} shift${
        shift.startMinutes !== undefined ? `, ${shiftTimeRange(shift)}` : ""
      }`
    : `${nurseName}, day ${cell.day}, off`;

  const tone = cell.shiftId
    ? shiftClassName(cell.shiftId, shifts)
    : SHIFT_STYLES.off;

  return (
    <td
      role="gridcell"
      data-row={rowIdx}
      data-col={colIdx}
      tabIndex={tabIndex}
      aria-label={label}
      title={label}
      onFocus={onFocus}
      className={cn(
        "border-l border-border p-0 text-center align-middle outline-none focus-visible:ring-2 focus-visible:ring-ring",
      )}
    >
      <div
        className={cn(
          "mx-0.5 my-0.5 rounded-md border px-1 py-1 font-mono text-[11px]",
          tone,
        )}
      >
        {cell.shiftId ?? "·"}
      </div>
    </td>
  );
}
