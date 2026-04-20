import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate, useParams } from "react-router";
import { useMutation, useQuery } from "@tanstack/react-query";
import {
  ArrowLeftIcon,
  Loader2Icon,
  OctagonXIcon,
  CheckIcon,
} from "lucide-react";
import { toast } from "sonner";

import type { Route } from "./+types/jobs.$jobId._index";
import { AppShell } from "~/components/app-shell";
import { Button } from "~/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "~/components/ui/card";
import { Progress } from "~/components/ui/progress";
import { Separator } from "~/components/ui/separator";
import { ObjectiveChart } from "~/components/objective-chart";
import { SolveStatusBadge } from "~/components/status-badge";
import { ErrorDisplay } from "~/components/error-display";
import { useApi, useBackend } from "~/lib/backend";
import { queryKeys } from "~/lib/query-keys";
import { useSolveStream } from "~/lib/sse";
import type { ObjectivePoint, SolveStatus } from "~/lib/types";
import {
  formatNumber,
  formatPercent,
  formatSeconds,
} from "~/lib/format";

export function meta({ params }: Route.MetaArgs) {
  return [
    { title: `Job ${params.jobId} — NSP Scheduler` },
  ];
}

const TERMINAL: SolveStatus[] = [
  "optimal",
  "feasible",
  "infeasible",
  "unknown",
  "modelInvalid",
  "cancelled",
  "error",
];

export default function SolveProgress() {
  const { jobId = "" } = useParams();
  const api = useApi();
  const { backend, baseUrl } = useBackend();
  const navigate = useNavigate();
  const [autoOpen, setAutoOpen] = useState(true);
  const [elapsed, setElapsed] = useState(0);
  const [streamStart] = useState(() => Date.now());

  const streamUrl =
    typeof window !== "undefined" ? api.streamUrl(jobId) : null;
  const { state, latest, events, error } = useSolveStream(streamUrl);

  // Fallback polling: covers first-load before SSE opens + late connection.
  const polled = useQuery({
    queryKey: queryKeys.solution(backend, baseUrl, jobId),
    queryFn: ({ signal }) => api.getSolution(jobId, signal),
    enabled: typeof window !== "undefined" && !!jobId,
    refetchInterval: (q) => {
      const s = q.state.data?.status;
      if (!s || TERMINAL.includes(s)) return false;
      return 2000;
    },
  });

  const current = latest ?? polled.data;

  // Elapsed-time ticker that stops when terminal.
  useEffect(() => {
    if (!current || TERMINAL.includes(current.status)) return;
    const id = window.setInterval(() => {
      setElapsed((Date.now() - streamStart) / 1000);
    }, 250);
    return () => window.clearInterval(id);
  }, [current, streamStart]);

  // Navigate to schedule / infeasibility on terminal.
  useEffect(() => {
    if (!current) return;
    if (current.status === "optimal" || current.status === "feasible") {
      if (autoOpen) {
        navigate(`/jobs/${encodeURIComponent(jobId)}/schedule`);
      }
    } else if (current.status === "infeasible") {
      navigate(`/jobs/${encodeURIComponent(jobId)}/infeasibility`);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [current?.status, autoOpen, jobId]);

  const points: ObjectivePoint[] = useMemo(() => {
    return events
      .filter((e): e is typeof e & { objective: number } =>
        typeof e.objective === "number",
      )
      .map((e) => ({
        t: e.solveTimeSeconds ?? (Date.now() - streamStart) / 1000,
        objective: e.objective,
        bestBound: e.bestBound,
      }));
  }, [events, streamStart]);

  const cancelMutation = useMutation({
    mutationFn: () => api.cancelSolve(jobId),
    onSuccess: () => toast.info("Cancellation requested"),
    onError: (err) =>
      toast.error("Cancel failed", {
        description: err instanceof Error ? err.message : String(err),
      }),
  });

  const status = current?.status;
  const pending = !current && polled.isPending;
  const sseErrored = state === "error" && !!error && !current;

  return (
    <AppShell>
      <div className="flex flex-col gap-6">
        <Link
          to="/instances"
          className="inline-flex items-center gap-1 text-xs text-muted-foreground hover:text-foreground transition-colors w-fit"
        >
          <ArrowLeftIcon className="size-3" />
          Back to instances
        </Link>

        <header className="flex flex-wrap items-start justify-between gap-3">
          <div className="flex flex-col gap-1">
            <div className="flex items-center gap-2">
              <h1 className="text-2xl font-semibold tracking-tight">Solve</h1>
              {status && <SolveStatusBadge status={status} />}
            </div>
            <p className="font-mono text-xs text-muted-foreground">{jobId}</p>
          </div>
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              onClick={() => cancelMutation.mutate()}
              disabled={
                cancelMutation.isPending ||
                !status ||
                TERMINAL.includes(status)
              }
            >
              <OctagonXIcon />
              Cancel solve
            </Button>
          </div>
        </header>

        {pending ? (
          <LoadingState />
        ) : polled.isError && !current ? (
          <ErrorDisplay
            error={polled.error}
            onRetry={() => void polled.refetch()}
          />
        ) : current ? (
          <>
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
              <Stat
                label="Objective"
                value={formatNumber(current.objective)}
              />
              <Stat
                label="Best bound"
                value={formatNumber(current.bestBound)}
              />
              <Stat label="Gap" value={formatPercent(current.gap)} />
              <Stat
                label="Solve time"
                value={formatSeconds(
                  current.solveTimeSeconds ??
                    (TERMINAL.includes(current.status) ? elapsed : undefined),
                )}
              />
            </div>

            <Card>
              <CardHeader>
                <CardTitle className="text-base">Live progress</CardTitle>
                <CardDescription>
                  Events streamed from{" "}
                  <span className="font-mono">GET /solutions/{jobId}/stream</span>
                  {sseErrored
                    ? ". Stream disconnected; falling back to polling."
                    : "."}
                </CardDescription>
              </CardHeader>
              <CardContent className="flex flex-col gap-4">
                <div className="flex flex-wrap items-center gap-3 text-xs text-muted-foreground">
                  <div className="flex items-center gap-2">
                    <span
                      className={`inline-flex size-2 rounded-full ${
                        state === "open"
                          ? "bg-emerald-500 animate-pulse"
                          : state === "connecting"
                            ? "bg-amber-500 animate-pulse"
                            : "bg-muted-foreground"
                      }`}
                      aria-hidden
                    />
                    stream {state}
                  </div>
                  <span>·</span>
                  <span>{events.length} events</span>
                  <span>·</span>
                  <span>elapsed {formatSeconds(elapsed)}</span>
                </div>
                <ObjectiveChart points={points} />
                <Separator />
                <label className="inline-flex items-center gap-2 text-xs text-muted-foreground">
                  <input
                    type="checkbox"
                    checked={autoOpen}
                    onChange={(e) => setAutoOpen(e.target.checked)}
                  />
                  Auto-open schedule when a feasible or optimal solution is
                  found
                </label>
              </CardContent>
            </Card>

            {current.schedule && current.schedule.assignments.length > 0 && (
              <Card>
                <CardHeader>
                  <CardTitle className="text-base">
                    Partial schedule preview
                  </CardTitle>
                  <CardDescription>
                    Latest known assignments (
                    {current.schedule.assignments.length} rows).
                  </CardDescription>
                </CardHeader>
                <CardContent>
                  <Progress
                    value={
                      current.gap !== undefined
                        ? Math.max(0, 100 - current.gap * 100)
                        : undefined
                    }
                  />
                  <p className="mt-3 text-xs text-muted-foreground">
                    Open the{" "}
                    <Link
                      to={`/jobs/${encodeURIComponent(jobId)}/schedule`}
                      className="underline underline-offset-2"
                    >
                      full schedule view
                    </Link>{" "}
                    to see the roster and Gantt.
                  </p>
                </CardContent>
              </Card>
            )}

            {status && TERMINAL.includes(status) && (
              <Card
                className={
                  status === "optimal" || status === "feasible"
                    ? "border-emerald-500/30 bg-emerald-500/5"
                    : "border-destructive/30 bg-destructive/5"
                }
              >
                <CardContent className="flex flex-wrap items-center justify-between gap-3 py-4">
                  <div className="flex items-center gap-2">
                    {status === "optimal" || status === "feasible" ? (
                      <CheckIcon className="size-4 text-emerald-600 dark:text-emerald-400" />
                    ) : (
                      <OctagonXIcon className="size-4 text-destructive" />
                    )}
                    <span className="text-sm font-medium">
                      Terminal status: <span className="font-mono">{status}</span>
                      {current.error ? ` — ${current.error}` : null}
                    </span>
                  </div>
                  <div className="flex gap-2">
                    {(status === "optimal" || status === "feasible") && (
                      <Button asChild>
                        <Link to={`/jobs/${encodeURIComponent(jobId)}/schedule`}>
                          View schedule
                        </Link>
                      </Button>
                    )}
                    {status === "infeasible" && (
                      <Button asChild variant="destructive">
                        <Link
                          to={`/jobs/${encodeURIComponent(jobId)}/infeasibility`}
                        >
                          Why?
                        </Link>
                      </Button>
                    )}
                  </div>
                </CardContent>
              </Card>
            )}
          </>
        ) : (
          <LoadingState />
        )}
      </div>
    </AppShell>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <Card size="sm">
      <CardContent className="py-3">
        <p className="text-[10px] uppercase tracking-wider text-muted-foreground">
          {label}
        </p>
        <p className="text-xl font-semibold tabular-nums">{value}</p>
      </CardContent>
    </Card>
  );
}

function LoadingState() {
  return (
    <div className="flex items-center gap-3 rounded-xl border border-border bg-card p-6">
      <Loader2Icon className="size-5 animate-spin text-primary" />
      <div className="flex flex-col">
        <p className="text-sm font-medium">Waiting for the solver to start…</p>
        <p className="text-xs text-muted-foreground">
          The first update should arrive within a second or two.
        </p>
      </div>
    </div>
  );
}
