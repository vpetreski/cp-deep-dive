import { Badge } from "~/components/ui/badge";
import type { Violation } from "~/lib/types";
import { cn } from "~/lib/utils";

export interface ViolationsPanelProps {
  violations?: Violation[];
  severity?: "hard" | "soft" | "all";
  emptyMessage?: string;
  className?: string;
}

export function ViolationsPanel({
  violations = [],
  severity = "all",
  emptyMessage = "No violations detected.",
  className,
}: ViolationsPanelProps) {
  const filtered =
    severity === "all"
      ? violations
      : violations.filter((v) => (v.severity ?? "soft") === severity);

  if (filtered.length === 0) {
    return (
      <div
        className={cn(
          "rounded-lg border border-dashed border-border bg-muted/30 p-4 text-sm text-muted-foreground",
          className,
        )}
      >
        {emptyMessage}
      </div>
    );
  }

  return (
    <ul
      className={cn(
        "flex flex-col divide-y divide-border rounded-lg border border-border bg-card text-sm",
        className,
      )}
    >
      {filtered.map((v, i) => (
        <li key={`${v.code}-${i}`} className="flex flex-col gap-1 p-3">
          <div className="flex flex-wrap items-center gap-2">
            <Badge
              variant={v.severity === "hard" ? "destructive" : "secondary"}
              className="font-mono"
            >
              {v.code}
            </Badge>
            {typeof v.penalty === "number" && v.severity !== "hard" && (
              <Badge variant="outline" className="font-mono">
                penalty {v.penalty}
              </Badge>
            )}
            {v.nurseId && (
              <span className="text-xs text-muted-foreground">
                nurse <span className="font-mono">{v.nurseId}</span>
                {typeof v.day === "number" && <> · day {v.day}</>}
              </span>
            )}
          </div>
          <p className="text-sm leading-snug">{v.message}</p>
        </li>
      ))}
    </ul>
  );
}
