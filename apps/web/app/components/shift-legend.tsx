import type { Shift } from "~/lib/types";
import { shiftTimeRange } from "~/lib/format";
import { SHIFT_STYLES, shiftClassName } from "~/lib/shifts";

export function ShiftLegend({ shifts }: { shifts: Shift[] }) {
  return (
    <div className="flex flex-wrap items-center gap-2 text-xs">
      {shifts.map((s) => (
        <div
          key={s.id}
          className={`inline-flex items-center gap-1.5 rounded-md border px-2 py-1 ${shiftClassName(s.id, shifts)}`}
        >
          <span className="font-mono font-semibold">{s.id}</span>
          <span className="text-muted-foreground">{s.label}</span>
          {s.startMinutes !== undefined && (
            <span className="text-muted-foreground">
              {shiftTimeRange(s)}
            </span>
          )}
        </div>
      ))}
      <div
        className={`inline-flex items-center gap-1.5 rounded-md border px-2 py-1 ${SHIFT_STYLES.off}`}
      >
        <span className="font-mono font-semibold">·</span>
        <span className="text-muted-foreground">Off</span>
      </div>
    </div>
  );
}
