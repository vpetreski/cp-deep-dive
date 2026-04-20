import { useMemo, useState } from "react";
import type { NspInstance, Schedule, Shift } from "~/lib/types";
import { cn } from "~/lib/utils";
import { shiftBarClassName } from "~/lib/shifts";
import { shiftTimeRange } from "~/lib/format";

interface Segment {
  nurseId: string;
  nurseName: string;
  day: number;
  shift: Shift;
  startMinutes: number; // minutes from day 0 start
  endMinutes: number; // minutes from day 0 start
}

function buildSegments(instance: NspInstance, schedule: Schedule): Segment[] {
  const shifts = new Map(instance.shifts.map((s) => [s.id, s] as const));
  const nurseNames = new Map(
    instance.nurses.map((n) => [n.id, n.name ?? n.id] as const),
  );
  const segs: Segment[] = [];
  for (const a of schedule.assignments) {
    if (!a.shiftId) continue;
    const s = shifts.get(a.shiftId);
    if (!s) continue;
    const start = s.startMinutes ?? 0;
    const duration = s.durationMinutes ?? 480;
    const startMinutes = a.day * 24 * 60 + start;
    const endMinutes = startMinutes + duration;
    segs.push({
      nurseId: a.nurseId,
      nurseName: nurseNames.get(a.nurseId) ?? a.nurseId,
      day: a.day,
      shift: s,
      startMinutes,
      endMinutes,
    });
  }
  return segs;
}

const DAY_PIXEL_WIDTH = 96; // compact density; responsive via container scroll
const ROW_HEIGHT = 36;

export interface GanttChartProps {
  instance: NspInstance;
  schedule: Schedule;
  className?: string;
}

export function GanttChart({
  instance,
  schedule,
  className,
}: GanttChartProps) {
  const segments = useMemo(
    () => buildSegments(instance, schedule),
    [instance, schedule],
  );
  const totalDays = instance.horizonDays;
  const timelineWidth = totalDays * DAY_PIXEL_WIDTH;
  const [hoverKey, setHoverKey] = useState<string | null>(null);

  return (
    <div
      className={cn(
        "overflow-auto rounded-xl border border-border bg-card",
        className,
      )}
    >
      <div
        className="relative"
        style={{
          minWidth: `${timelineWidth + 120}px`,
        }}
      >
        {/* Header row with day markers */}
        <div
          className="sticky top-0 z-10 flex border-b border-border bg-muted/80 backdrop-blur"
          style={{ height: 32 }}
        >
          <div
            className="sticky left-0 border-r border-border bg-muted/80 px-2 py-1 text-xs font-medium"
            style={{ width: 120 }}
          >
            Nurse
          </div>
          <div className="relative flex-1">
            {Array.from({ length: totalDays }, (_, d) => (
              <div
                key={d}
                className="absolute top-0 flex h-full items-center justify-start border-l border-border px-1 text-[10px] text-muted-foreground"
                style={{
                  left: d * DAY_PIXEL_WIDTH,
                  width: DAY_PIXEL_WIDTH,
                }}
              >
                Day {d}
              </div>
            ))}
          </div>
        </div>

        {/* Rows */}
        <div>
          {instance.nurses.map((n, rowIdx) => {
            const rowSegments = segments.filter((s) => s.nurseId === n.id);
            return (
              <div
                key={n.id}
                className={cn(
                  "flex items-center border-b border-border",
                  rowIdx % 2 === 1 && "bg-muted/20",
                )}
                style={{ height: ROW_HEIGHT }}
              >
                <div
                  className="sticky left-0 z-[5] flex h-full items-center border-r border-border bg-card px-2 text-xs"
                  style={{ width: 120 }}
                >
                  <span className="truncate font-medium">
                    {n.name ?? n.id}
                  </span>
                </div>
                <div
                  className="relative flex-1"
                  style={{ height: ROW_HEIGHT, width: timelineWidth }}
                >
                  {/* Day grid lines */}
                  {Array.from({ length: totalDays }, (_, d) => (
                    <div
                      key={d}
                      aria-hidden
                      className="absolute top-0 h-full border-l border-border/60"
                      style={{ left: d * DAY_PIXEL_WIDTH }}
                    />
                  ))}
                  {/* Segments */}
                  {rowSegments.map((seg, i) => {
                    const barClass = shiftBarClassName(
                      seg.shift.id,
                      instance.shifts,
                    );
                    const leftPx =
                      (seg.startMinutes / (24 * 60)) * DAY_PIXEL_WIDTH;
                    const widthPx =
                      ((seg.endMinutes - seg.startMinutes) / (24 * 60)) *
                      DAY_PIXEL_WIDTH;
                    const key = `${seg.nurseId}-${seg.day}-${seg.shift.id}-${i}`;
                    const timeLabel = shiftTimeRange(seg.shift);
                    const aria =
                      `${seg.nurseName}, day ${seg.day}, ${seg.shift.label} shift` +
                      (timeLabel ? ` ${timeLabel}` : "");
                    return (
                      <button
                        key={key}
                        type="button"
                        aria-label={aria}
                        title={aria}
                        onMouseEnter={() => setHoverKey(key)}
                        onMouseLeave={() => setHoverKey(null)}
                        onFocus={() => setHoverKey(key)}
                        onBlur={() => setHoverKey(null)}
                        className={cn(
                          "absolute top-1 flex h-[calc(100%-8px)] items-center overflow-hidden rounded-md px-1.5 text-[10px] font-semibold text-foreground/80 outline-none transition-transform focus-visible:ring-2 focus-visible:ring-ring dark:text-foreground/90",
                          barClass,
                          hoverKey === key && "scale-[1.02] shadow-md",
                        )}
                        style={{ left: leftPx, width: Math.max(widthPx, 24) }}
                      >
                        <span className="truncate">{seg.shift.id}</span>
                      </button>
                    );
                  })}
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
