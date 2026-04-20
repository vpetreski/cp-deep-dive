import { useMemo } from "react";
import type { NspInstance } from "~/lib/types";
import { cn } from "~/lib/utils";

/**
 * Compact heatmap of required coverage. Rows = shift, columns = day, cell =
 * required count. Meant for the instance-detail preview; read-only.
 */
export function CoverageMatrix({
  instance,
  className,
}: {
  instance: NspInstance;
  className?: string;
}) {
  const matrix = useMemo(() => {
    const byKey = new Map<string, number>();
    for (const c of instance.coverage) {
      byKey.set(`${c.day}:${c.shiftId}`, c.required);
    }
    return byKey;
  }, [instance]);

  const maxReq = useMemo(() => {
    let m = 0;
    for (const v of matrix.values()) if (v > m) m = v;
    return m || 1;
  }, [matrix]);

  return (
    <div
      className={cn(
        "overflow-auto rounded-xl border border-border bg-card",
        className,
      )}
    >
      <table
        className="min-w-full border-collapse text-[11px]"
        aria-label="Coverage requirements"
      >
        <thead>
          <tr>
            <th className="sticky left-0 top-0 z-10 bg-muted/80 px-2 py-1.5 text-left font-medium backdrop-blur">
              Shift
            </th>
            {Array.from({ length: instance.horizonDays }, (_, d) => (
              <th
                key={d}
                className="sticky top-0 min-w-8 bg-muted/80 px-1 py-1.5 text-center font-medium backdrop-blur"
              >
                {d}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {instance.shifts.map((s) => (
            <tr key={s.id} className="border-t border-border">
              <th
                scope="row"
                className="sticky left-0 z-10 bg-card px-2 py-1 text-left font-mono"
              >
                {s.id}{" "}
                <span className="text-muted-foreground font-sans">
                  {s.label}
                </span>
              </th>
              {Array.from({ length: instance.horizonDays }, (_, d) => {
                const req = matrix.get(`${d}:${s.id}`) ?? 0;
                const intensity = req === 0 ? 0 : req / maxReq;
                return (
                  <td
                    key={d}
                    className="border-l border-border p-0 text-center"
                  >
                    <div
                      className="mx-0.5 my-0.5 flex h-6 items-center justify-center rounded font-mono"
                      style={{
                        backgroundColor:
                          req === 0
                            ? undefined
                            : `color-mix(in oklab, var(--primary) ${Math.round(
                                10 + intensity * 40,
                              )}%, transparent)`,
                        color:
                          req === 0 ? undefined : "var(--primary-foreground)",
                      }}
                      title={`Day ${d} · ${s.label}: requires ${req}`}
                    >
                      {req || "·"}
                    </div>
                  </td>
                );
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
