import { useMemo } from "react";
import type { ObjectivePoint } from "~/lib/types";
import { cn } from "~/lib/utils";

/**
 * Lightweight SVG sparkline for objective-over-time. Deliberately not a
 * Recharts/VisX dep — the chart is plotted from ~0–500 points of (t, y)
 * tuples streamed over SSE and we don't need legends, zoom, or stacking.
 */
export function ObjectiveChart({
  points,
  className,
  height = 120,
}: {
  points: ObjectivePoint[];
  className?: string;
  height?: number;
}) {
  const path = useMemo(() => buildPath(points, height), [points, height]);

  if (points.length === 0) {
    return (
      <div
        className={cn(
          "flex items-center justify-center rounded-lg border border-dashed border-border bg-muted/30 text-xs text-muted-foreground",
          className,
        )}
        style={{ height }}
      >
        Waiting for first solver update…
      </div>
    );
  }

  const last = points[points.length - 1];
  const min = Math.min(...points.map((p) => p.objective));
  const max = Math.max(...points.map((p) => p.objective));

  return (
    <div className={cn("flex flex-col gap-1", className)}>
      <div className="flex items-baseline justify-between text-xs text-muted-foreground">
        <span>Objective over time</span>
        <span className="font-mono tabular-nums text-foreground">
          best {last.objective.toFixed(0)}
          {typeof last.bestBound === "number" && (
            <>
              {" / bound "}
              {last.bestBound.toFixed(0)}
            </>
          )}
        </span>
      </div>
      <svg
        viewBox={`0 0 ${path.width} ${height}`}
        preserveAspectRatio="none"
        className="h-[var(--h)] w-full rounded-md border border-border bg-muted/10"
        style={{ ["--h" as string]: `${height}px` }}
        role="img"
        aria-label={`Objective range: ${min.toFixed(0)} to ${max.toFixed(0)}`}
      >
        {path.area && (
          <path
            d={path.area}
            fill="var(--primary)"
            fillOpacity="0.12"
            stroke="none"
          />
        )}
        {path.line && (
          <path
            d={path.line}
            fill="none"
            stroke="var(--primary)"
            strokeWidth="1.5"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        )}
        {path.bound && (
          <path
            d={path.bound}
            fill="none"
            stroke="var(--muted-foreground)"
            strokeWidth="1"
            strokeDasharray="3 3"
            strokeLinecap="round"
          />
        )}
      </svg>
      <div className="flex justify-between text-[10px] text-muted-foreground">
        <span>t = 0 s</span>
        <span>t = {last.t.toFixed(1)} s</span>
      </div>
    </div>
  );
}

function buildPath(
  points: ObjectivePoint[],
  height: number,
): { line: string; area: string; bound: string | null; width: number } {
  const width = 320;
  if (points.length === 0) return { line: "", area: "", bound: null, width };
  const xs = points.map((p) => p.t);
  const ys = points.map((p) => p.objective);
  const bounds = points
    .map((p) => p.bestBound)
    .filter((b): b is number => typeof b === "number");
  const minX = 0;
  const maxX = Math.max(1, xs[xs.length - 1]);
  const minY = Math.min(...ys, ...(bounds.length ? bounds : [Infinity]));
  const maxY = Math.max(...ys, ...(bounds.length ? bounds : [-Infinity]));
  const spanY = Math.max(1, maxY - minY);

  const pad = 4;
  const innerH = height - 2 * pad;

  const toX = (t: number) =>
    minX === maxX ? width / 2 : ((t - minX) / (maxX - minX)) * width;
  const toY = (y: number) => pad + innerH - ((y - minY) / spanY) * innerH;

  const line = points
    .map((p, i) => `${i === 0 ? "M" : "L"}${toX(p.t).toFixed(1)},${toY(p.objective).toFixed(1)}`)
    .join(" ");

  const area = `${line} L${toX(points[points.length - 1].t).toFixed(1)},${height - pad} L${toX(points[0].t).toFixed(1)},${height - pad} Z`;

  const boundLine = bounds.length
    ? points
        .filter((p) => typeof p.bestBound === "number")
        .map(
          (p, i) =>
            `${i === 0 ? "M" : "L"}${toX(p.t).toFixed(1)},${toY(p.bestBound as number).toFixed(1)}`,
        )
        .join(" ")
    : null;

  return { line, area, bound: boundLine, width };
}
