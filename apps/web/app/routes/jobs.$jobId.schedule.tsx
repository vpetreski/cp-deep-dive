import { useMemo, useState } from "react";
import { Link, useParams } from "react-router";
import { useQuery } from "@tanstack/react-query";
import {
  ArrowLeftIcon,
  DownloadIcon,
  TableIcon,
  ChartGanttIcon,
  ChartPieIcon,
} from "lucide-react";
import { toast } from "sonner";

import type { Route } from "./+types/jobs.$jobId.schedule";
import { AppShell } from "~/components/app-shell";
import { Button } from "~/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "~/components/ui/card";
import { Skeleton } from "~/components/ui/skeleton";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "~/components/ui/tabs";
import { ErrorDisplay } from "~/components/error-display";
import { ScheduleTable } from "~/components/schedule-table";
import { GanttChart } from "~/components/gantt-chart";
import { SummaryStats } from "~/components/summary-stats";
import { ViolationsPanel } from "~/components/violations-panel";
import { ShiftLegend } from "~/components/shift-legend";
import { SolveStatusBadge } from "~/components/status-badge";
import { useApi, useBackend } from "~/lib/backend";
import { queryKeys } from "~/lib/query-keys";
import { downloadBlob, formatNumber, formatPercent, formatSeconds } from "~/lib/format";
import type { NspInstance, Schedule, SolveResponse } from "~/lib/types";

export function meta({ params }: Route.MetaArgs) {
  return [
    { title: `Schedule ${params.jobId} — NSP Scheduler` },
  ];
}

type TabKey = "table" | "gantt" | "summary";

export default function ScheduleView() {
  const { jobId = "" } = useParams();
  const api = useApi();
  const { backend, baseUrl } = useBackend();
  const [tab, setTab] = useState<TabKey>("table");

  const solutionQuery = useQuery({
    queryKey: queryKeys.solution(backend, baseUrl, jobId),
    queryFn: ({ signal }) => api.getSolution(jobId, signal),
    enabled: typeof window !== "undefined" && !!jobId,
  });

  const solution = solutionQuery.data;
  const instanceId = solution?.schedule?.instanceId;

  const instanceQuery = useQuery({
    queryKey: queryKeys.instance(backend, baseUrl, instanceId ?? ""),
    queryFn: ({ signal }) => api.getInstance(instanceId as string, signal),
    enabled:
      typeof window !== "undefined" &&
      !!instanceId &&
      solutionQuery.isSuccess,
  });

  const instance = instanceQuery.data;
  const schedule = solution?.schedule;

  const hardViolations = useMemo(
    () => (schedule?.violations ?? []).filter((v) => v.severity === "hard"),
    [schedule?.violations],
  );
  const softViolations = useMemo(
    () => (schedule?.violations ?? []).filter((v) => v.severity !== "hard"),
    [schedule?.violations],
  );

  function onDownloadCsv() {
    if (!schedule || !instance) return;
    const csv = scheduleToCsv(instance, schedule);
    downloadBlob(`${instance.id}-${jobId}.csv`, csv, "text/csv");
    toast.success("CSV downloaded", {
      description: `${schedule.assignments.length} rows exported.`,
    });
  }

  function onDownloadJson() {
    if (!solution) return;
    downloadBlob(
      `${jobId}.json`,
      JSON.stringify(solution, null, 2),
      "application/json",
    );
  }

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

        {solutionQuery.isPending ? (
          <LoadingState />
        ) : solutionQuery.isError ? (
          <ErrorDisplay
            error={solutionQuery.error}
            onRetry={() => void solutionQuery.refetch()}
          />
        ) : !solution || !schedule ? (
          <Card>
            <CardHeader>
              <CardTitle className="text-base">No schedule yet</CardTitle>
              <CardDescription>
                This job hasn't produced any assignments. Return to the solve
                progress page to monitor it.
              </CardDescription>
            </CardHeader>
            <CardContent>
              <Button asChild variant="outline">
                <Link to={`/jobs/${encodeURIComponent(jobId)}`}>
                  View progress
                </Link>
              </Button>
            </CardContent>
          </Card>
        ) : (
          <>
            <header className="flex flex-wrap items-start justify-between gap-3">
              <div className="flex flex-col gap-1">
                <div className="flex items-center gap-2">
                  <h1 className="text-2xl font-semibold tracking-tight">
                    Schedule
                  </h1>
                  <SolveStatusBadge status={solution.status} />
                </div>
                <p className="font-mono text-xs text-muted-foreground">
                  {jobId}
                  {schedule.instanceId && (
                    <>
                      {" · "}
                      <Link
                        to={`/instances/${encodeURIComponent(schedule.instanceId)}`}
                        className="underline underline-offset-2"
                      >
                        {schedule.instanceId}
                      </Link>
                    </>
                  )}
                </p>
              </div>
              <div className="flex flex-wrap items-center gap-2">
                <Button variant="outline" onClick={onDownloadJson}>
                  <DownloadIcon />
                  JSON
                </Button>
                <Button onClick={onDownloadCsv} disabled={!instance}>
                  <DownloadIcon />
                  CSV
                </Button>
              </div>
            </header>

            <SolutionStats solution={solution} />

            {instanceQuery.isPending && !instance ? (
              <Skeleton className="h-64" />
            ) : !instance ? (
              <ErrorDisplay
                error={
                  instanceQuery.error ??
                  new Error("Could not load the source instance for this schedule.")
                }
                onRetry={() => void instanceQuery.refetch()}
              />
            ) : (
              <>
                <ShiftLegend shifts={instance.shifts} />

                <Tabs value={tab} onValueChange={(v) => setTab(v as TabKey)}>
                  <TabsList>
                    <TabsTrigger value="table">
                      <TableIcon className="size-4" />
                      Table
                    </TabsTrigger>
                    <TabsTrigger value="gantt">
                      <ChartGanttIcon className="size-4" />
                      Gantt
                    </TabsTrigger>
                    <TabsTrigger value="summary">
                      <ChartPieIcon className="size-4" />
                      Summary
                    </TabsTrigger>
                  </TabsList>

                  <TabsContent value="table" className="flex flex-col gap-3">
                    <ScheduleTable instance={instance} schedule={schedule} />
                    <p className="text-xs text-muted-foreground">
                      Use the arrow keys to navigate cells. Weekend columns are
                      dimmed. A dot (·) means the nurse is off that day.
                    </p>
                  </TabsContent>

                  <TabsContent value="gantt" className="flex flex-col gap-3">
                    <GanttChart instance={instance} schedule={schedule} />
                    <p className="text-xs text-muted-foreground">
                      Bars are positioned by the shift's start time and scaled
                      to its duration. Hover for details.
                    </p>
                  </TabsContent>

                  <TabsContent value="summary" className="flex flex-col gap-4">
                    <SummaryStats instance={instance} schedule={schedule} />
                    {softViolations.length > 0 && (
                      <Card>
                        <CardHeader>
                          <CardTitle className="text-base">
                            Soft-constraint penalties
                          </CardTitle>
                          <CardDescription>
                            Each row is a soft-constraint violation the solver
                            accepted because it improved the overall objective.
                          </CardDescription>
                        </CardHeader>
                        <CardContent>
                          <ViolationsPanel
                            violations={softViolations}
                            severity="soft"
                          />
                        </CardContent>
                      </Card>
                    )}
                  </TabsContent>
                </Tabs>

                {hardViolations.length > 0 && (
                  <Card className="border-destructive/30 bg-destructive/5">
                    <CardHeader>
                      <CardTitle className="text-base">
                        Hard-constraint violations
                      </CardTitle>
                      <CardDescription>
                        This schedule broke {hardViolations.length} hard rule
                        {hardViolations.length === 1 ? "" : "s"} — review below.
                      </CardDescription>
                    </CardHeader>
                    <CardContent>
                      <ViolationsPanel
                        violations={hardViolations}
                        severity="hard"
                      />
                    </CardContent>
                  </Card>
                )}
              </>
            )}
          </>
        )}
      </div>
    </AppShell>
  );
}

function SolutionStats({ solution }: { solution: SolveResponse }) {
  return (
    <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
      <StatCell label="Objective" value={formatNumber(solution.objective)} />
      <StatCell label="Best bound" value={formatNumber(solution.bestBound)} />
      <StatCell label="Gap" value={formatPercent(solution.gap)} />
      <StatCell
        label="Solve time"
        value={formatSeconds(solution.solveTimeSeconds)}
      />
    </div>
  );
}

function StatCell({ label, value }: { label: string; value: string }) {
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
    <div className="flex flex-col gap-4">
      <Skeleton className="h-10 w-48" />
      <div className="grid grid-cols-4 gap-3">
        {Array.from({ length: 4 }, (_, i) => (
          <Skeleton key={i} className="h-20" />
        ))}
      </div>
      <Skeleton className="h-96" />
    </div>
  );
}

function scheduleToCsv(instance: NspInstance, schedule: Schedule): string {
  const head = ["nurseId", "nurseName", "day", "shiftId", "shiftLabel"];
  const shiftIndex = new Map(instance.shifts.map((s) => [s.id, s] as const));
  const nurseIndex = new Map(
    instance.nurses.map((n) => [n.id, n.name ?? n.id] as const),
  );
  const rows = [...schedule.assignments]
    .sort((a, b) =>
      a.nurseId === b.nurseId ? a.day - b.day : a.nurseId.localeCompare(b.nurseId),
    )
    .map((a) => {
      const shift = a.shiftId ? shiftIndex.get(a.shiftId) : undefined;
      return [
        a.nurseId,
        nurseIndex.get(a.nurseId) ?? a.nurseId,
        String(a.day),
        a.shiftId ?? "",
        shift?.label ?? "",
      ];
    });
  const all = [head, ...rows];
  return all.map((r) => r.map(csvCell).join(",")).join("\n") + "\n";
}

function csvCell(value: string): string {
  const needsQuote = /[",\r\n]/.test(value);
  const escaped = value.replace(/"/g, '""');
  return needsQuote ? `"${escaped}"` : escaped;
}
