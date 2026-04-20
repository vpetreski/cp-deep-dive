import { useMemo } from "react";
import { Link, useParams } from "react-router";
import { useQuery } from "@tanstack/react-query";
import {
  ArrowLeftIcon,
  AlertTriangleIcon,
  LightbulbIcon,
  PlayIcon,
} from "lucide-react";

import type { Route } from "./+types/jobs.$jobId.infeasibility";
import { AppShell } from "~/components/app-shell";
import { Badge } from "~/components/ui/badge";
import { Button } from "~/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "~/components/ui/card";
import { Skeleton } from "~/components/ui/skeleton";
import { ErrorDisplay } from "~/components/error-display";
import { SolveStatusBadge } from "~/components/status-badge";
import { useApi, useBackend } from "~/lib/backend";
import { queryKeys } from "~/lib/query-keys";
import type { Violation } from "~/lib/types";

export function meta({ params }: Route.MetaArgs) {
  return [
    { title: `Why infeasible? — Job ${params.jobId}` },
  ];
}

/**
 * Canonical hard-constraint codes in the NSP spec (docs/knowledge/nurse-scheduling).
 * We map back from the server's reported `code` to a human explanation + a
 * hint for what to try. This is the best we can do without an actual IIS
 * (irreducible infeasible subsystem) from CP-SAT — solve the "unsat" by
 * pointing the user at the likely-offending family.
 */
const HARD_CONSTRAINT_HELP: Record<
  string,
  { label: string; what: string; hint: string }
> = {
  "HC-1": {
    label: "Coverage demand",
    what: "Some (day, shift) cell requires more nurses than you have qualified staff available.",
    hint: "Lower the required count or add nurses with the matching skill.",
  },
  "HC-2": {
    label: "One shift per day",
    what: "A nurse was assigned more than one shift on the same day.",
    hint: "This is a solver invariant — if reported, it's likely a model bug.",
  },
  "HC-3": {
    label: "Forbidden transitions",
    what: "A forbidden shift transition (e.g. Night → Day) was scheduled.",
    hint: "Relax `forbiddenTransitions` or increase rest time between shifts.",
  },
  "HC-4": {
    label: "Max consecutive days",
    what: "A nurse was scheduled more than `maxConsecutiveWorkingDays` in a row.",
    hint: "Raise the per-nurse cap or spread required shifts across more staff.",
  },
  "HC-5": {
    label: "Min rest time",
    what: "Two back-to-back shifts left fewer than the required rest hours.",
    hint: "Lower `minRestHours` in metadata, or stagger shifts further apart.",
  },
  "HC-6": {
    label: "Skill match",
    what: "A nurse was assigned a shift whose required skill they don't have.",
    hint: "Add the missing skill to the nurse or move the requirement to another shift.",
  },
  "HC-7": {
    label: "Unavailable day",
    what: "A nurse was assigned a shift on a day marked as unavailable.",
    hint: "Remove the day from `nurse.unavailable` or assign another nurse.",
  },
  "HC-8": {
    label: "Weekly shift bounds",
    what: "A nurse's total shifts in a week exceeded `maxShiftsPerWeek` or fell below `minShiftsPerWeek`.",
    hint: "Widen the per-week window or redistribute workload across the ward.",
  },
};

export default function InfeasibilityReport() {
  const { jobId = "" } = useParams();
  const api = useApi();
  const { backend, baseUrl } = useBackend();

  const query = useQuery({
    queryKey: queryKeys.solution(backend, baseUrl, jobId),
    queryFn: ({ signal }) => api.getSolution(jobId, signal),
    enabled: typeof window !== "undefined" && !!jobId,
  });

  const solution = query.data;
  const hardViolations = useMemo<Violation[]>(() => {
    if (!solution?.schedule?.violations) return [];
    return solution.schedule.violations.filter((v) => v.severity === "hard");
  }, [solution]);

  const grouped = useMemo(() => {
    const map = new Map<string, Violation[]>();
    for (const v of hardViolations) {
      const key = v.code || "UNKNOWN";
      const existing = map.get(key);
      if (existing) existing.push(v);
      else map.set(key, [v]);
    }
    return [...map.entries()].sort((a, b) => b[1].length - a[1].length);
  }, [hardViolations]);

  const instanceId = solution?.schedule?.instanceId;

  return (
    <AppShell>
      <div className="flex flex-col gap-6">
        <Link
          to={`/jobs/${encodeURIComponent(jobId)}`}
          className="inline-flex items-center gap-1 text-xs text-muted-foreground hover:text-foreground transition-colors w-fit"
        >
          <ArrowLeftIcon className="size-3" />
          Back to solve progress
        </Link>

        {query.isPending ? (
          <LoadingState />
        ) : query.isError ? (
          <ErrorDisplay
            error={query.error}
            onRetry={() => void query.refetch()}
          />
        ) : !solution ? (
          <ErrorDisplay error={new Error("Job not found.")} />
        ) : (
          <>
            <header className="flex flex-wrap items-start justify-between gap-3">
              <div className="flex flex-col gap-1">
                <div className="flex items-center gap-2">
                  <h1 className="text-2xl font-semibold tracking-tight">
                    Why is this infeasible?
                  </h1>
                  <SolveStatusBadge status={solution.status} />
                </div>
                <p className="font-mono text-xs text-muted-foreground">
                  {jobId}
                  {instanceId && (
                    <>
                      {" · "}
                      <Link
                        to={`/instances/${encodeURIComponent(instanceId)}`}
                        className="underline underline-offset-2"
                      >
                        {instanceId}
                      </Link>
                    </>
                  )}
                </p>
              </div>
              {instanceId && (
                <Button asChild>
                  <Link to={`/instances/${encodeURIComponent(instanceId)}/solve`}>
                    <PlayIcon />
                    Try a new solve
                  </Link>
                </Button>
              )}
            </header>

            <Card className="border-destructive/30 bg-destructive/5">
              <CardHeader>
                <CardTitle className="flex items-center gap-2 text-base">
                  <AlertTriangleIcon className="size-4 text-destructive" />
                  Solver reported infeasibility
                </CardTitle>
                <CardDescription>
                  {solution.error ? (
                    <>
                      The solver returned: <span className="font-mono">{solution.error}</span>
                    </>
                  ) : (
                    <>
                      The solver explored the search space and determined that
                      no schedule satisfies every hard constraint. The breakdown
                      below shows which constraint families contributed.
                    </>
                  )}
                </CardDescription>
              </CardHeader>
            </Card>

            {grouped.length === 0 ? (
              <Card>
                <CardHeader>
                  <CardTitle className="text-base">
                    No per-constraint detail
                  </CardTitle>
                  <CardDescription>
                    The backend flagged the problem as infeasible but didn't
                    return a minimal offending subset. Review the guidance
                    below.
                  </CardDescription>
                </CardHeader>
                <CardContent>
                  <GenericGuidance />
                </CardContent>
              </Card>
            ) : (
              <div className="flex flex-col gap-3">
                {grouped.map(([code, vs]) => {
                  const help = HARD_CONSTRAINT_HELP[code];
                  return (
                    <Card
                      key={code}
                      className="border-destructive/30 bg-destructive/5"
                    >
                      <CardHeader>
                        <CardTitle className="flex flex-wrap items-center gap-2 text-base">
                          <Badge
                            variant="destructive"
                            className="font-mono"
                          >
                            {code}
                          </Badge>
                          {help?.label ?? "Unknown constraint"}
                          <span className="ml-auto text-xs font-normal text-muted-foreground">
                            {vs.length} occurrence{vs.length === 1 ? "" : "s"}
                          </span>
                        </CardTitle>
                        {help?.what && (
                          <CardDescription>{help.what}</CardDescription>
                        )}
                      </CardHeader>
                      <CardContent className="flex flex-col gap-3">
                        {help?.hint && (
                          <div className="flex items-start gap-2 rounded-md border border-border bg-card px-3 py-2 text-sm">
                            <LightbulbIcon className="mt-0.5 size-4 text-amber-500" />
                            <span>{help.hint}</span>
                          </div>
                        )}
                        <ul className="flex flex-col divide-y divide-border rounded-md border border-border bg-card text-sm">
                          {vs.slice(0, 6).map((v, i) => (
                            <li
                              key={`${code}-${i}`}
                              className="flex flex-wrap items-center gap-2 p-3"
                            >
                              {v.nurseId && (
                                <span className="text-xs text-muted-foreground">
                                  nurse <span className="font-mono">{v.nurseId}</span>
                                </span>
                              )}
                              {typeof v.day === "number" && (
                                <span className="text-xs text-muted-foreground">
                                  · day {v.day}
                                </span>
                              )}
                              <span className="text-sm">{v.message}</span>
                            </li>
                          ))}
                          {vs.length > 6 && (
                            <li className="p-3 text-xs text-muted-foreground">
                              …and {vs.length - 6} more
                            </li>
                          )}
                        </ul>
                      </CardContent>
                    </Card>
                  );
                })}
              </div>
            )}

            <Card>
              <CardHeader>
                <CardTitle className="text-base">What you can do</CardTitle>
                <CardDescription>
                  Infeasibility is usually caused by one of three things —
                  over-demand, over-restriction, or a missing skill. Try one of
                  these adjustments.
                </CardDescription>
              </CardHeader>
              <CardContent>
                <GenericGuidance />
              </CardContent>
            </Card>
          </>
        )}
      </div>
    </AppShell>
  );
}

function GenericGuidance() {
  return (
    <ul className="flex flex-col gap-2 text-sm">
      <li className="flex items-start gap-2">
        <span className="mt-1 inline-block size-1.5 rounded-full bg-primary" />
        <span>
          <span className="font-medium">Lower coverage demand</span>{" "}
          — drop one nurse from an over-required (day, shift) cell.
        </span>
      </li>
      <li className="flex items-start gap-2">
        <span className="mt-1 inline-block size-1.5 rounded-full bg-primary" />
        <span>
          <span className="font-medium">Relax rest windows</span>{" "}
          — reduce <code className="font-mono text-xs">metadata.minRestHours</code>
          {" "}or raise <code className="font-mono text-xs">maxConsecutiveWorkingDays</code>.
        </span>
      </li>
      <li className="flex items-start gap-2">
        <span className="mt-1 inline-block size-1.5 rounded-full bg-primary" />
        <span>
          <span className="font-medium">Add a skill</span>{" "}
          — if HC-6 is tripping, the schedule is likely short on a qualified
          nurse for a particular shift.
        </span>
      </li>
      <li className="flex items-start gap-2">
        <span className="mt-1 inline-block size-1.5 rounded-full bg-primary" />
        <span>
          <span className="font-medium">Remove forbidden transitions</span>{" "}
          — `forbiddenTransitions` like Night → Day cut the search space hard;
          drop one if the demand pattern forces the pairing.
        </span>
      </li>
      <li className="flex items-start gap-2">
        <span className="mt-1 inline-block size-1.5 rounded-full bg-primary" />
        <span>
          <span className="font-medium">Widen unavailability</span>{" "}
          — a nurse with too many blocked days + high demand is a common
          culprit.
        </span>
      </li>
    </ul>
  );
}

function LoadingState() {
  return (
    <div className="flex flex-col gap-4">
      <Skeleton className="h-10 w-64" />
      <Skeleton className="h-24" />
      <Skeleton className="h-40" />
    </div>
  );
}
