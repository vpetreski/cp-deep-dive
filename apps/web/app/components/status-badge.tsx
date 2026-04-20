import { Badge } from "~/components/ui/badge";
import type { SolveStatus } from "~/lib/types";
import { cn } from "~/lib/utils";

const TONES: Record<SolveStatus, string> = {
  pending: "bg-muted text-muted-foreground",
  running: "bg-blue-500/15 text-blue-700 dark:text-blue-300",
  feasible: "bg-amber-500/15 text-amber-700 dark:text-amber-300",
  optimal: "bg-emerald-500/15 text-emerald-700 dark:text-emerald-300",
  infeasible: "bg-destructive/15 text-destructive",
  unknown: "bg-muted text-muted-foreground",
  modelInvalid: "bg-destructive/15 text-destructive",
  cancelled: "bg-muted text-muted-foreground",
  error: "bg-destructive/15 text-destructive",
};

export function SolveStatusBadge({
  status,
  className,
}: {
  status: SolveStatus;
  className?: string;
}) {
  return (
    <Badge className={cn("font-mono uppercase", TONES[status], className)}>
      {status}
    </Badge>
  );
}
