import { useState } from "react";
import { Link, useNavigate, useParams } from "react-router";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  ArrowLeftIcon,
  ChevronDownIcon,
  ChevronRightIcon,
  PlayIcon,
  Trash2Icon,
} from "lucide-react";
import { toast } from "sonner";

import type { Route } from "./+types/instances.$instanceId._index";
import { AppShell } from "~/components/app-shell";
import { Button } from "~/components/ui/button";
import { Badge } from "~/components/ui/badge";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "~/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "~/components/ui/dialog";
import { Skeleton } from "~/components/ui/skeleton";
import { Separator } from "~/components/ui/separator";
import { ErrorDisplay } from "~/components/error-display";
import { CoverageMatrix } from "~/components/coverage-matrix";
import { useApi, useBackend } from "~/lib/backend";
import { queryKeys } from "~/lib/query-keys";
import { shiftTimeRange } from "~/lib/format";
import { getExampleById } from "~/lib/examples";
import { NspApiError } from "~/lib/api";

export function meta({ params }: Route.MetaArgs) {
  return [
    { title: `Instance ${params.instanceId} — NSP Scheduler` },
  ];
}

export default function InstanceDetail() {
  const { instanceId = "" } = useParams();
  const api = useApi();
  const { backend, baseUrl } = useBackend();
  const qc = useQueryClient();
  const navigate = useNavigate();
  const [rawOpen, setRawOpen] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);

  const query = useQuery({
    queryKey: queryKeys.instance(backend, baseUrl, instanceId),
    queryFn: ({ signal }) => api.getInstance(instanceId, signal),
    enabled: typeof window !== "undefined" && !!instanceId,
    // For offline demos, fall back to the bundled example with the same id.
    retry: (count, err) =>
      count < 1 && err instanceof NspApiError && err.status !== 404,
  });

  const fallback = getExampleById(instanceId);
  const instance = query.data ?? (query.isError ? fallback : undefined);

  const deleteMutation = useMutation({
    mutationFn: () => api.deleteInstance(instanceId),
    onSuccess: () => {
      toast.success("Instance deleted", {
        description: `Removed ${instanceId} and its solve jobs.`,
      });
      qc.invalidateQueries({ queryKey: ["instances", backend, baseUrl] });
      navigate("/instances");
    },
    onError: (err) => {
      toast.error("Delete failed", {
        description: err instanceof Error ? err.message : String(err),
      });
    },
  });

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

        {query.isPending && !fallback ? (
          <LoadingState />
        ) : query.isError && !fallback ? (
          <ErrorDisplay
            error={query.error}
            onRetry={() => void query.refetch()}
          />
        ) : instance ? (
          <>
            <header className="flex flex-wrap items-start justify-between gap-4">
              <div className="flex flex-col gap-1">
                <div className="flex items-center gap-2">
                  <h1 className="text-2xl font-semibold tracking-tight">
                    {instance.name ?? instance.id}
                  </h1>
                  {instance.source && (
                    <Badge variant="outline" className="font-mono text-[10px]">
                      {instance.source}
                    </Badge>
                  )}
                </div>
                <p className="font-mono text-xs text-muted-foreground">
                  {instance.id}
                </p>
              </div>
              <div className="flex items-center gap-2">
                <Button
                  variant="destructive"
                  onClick={() => setConfirmDelete(true)}
                  disabled={deleteMutation.isPending}
                >
                  <Trash2Icon />
                  Delete
                </Button>
                <Button
                  asChild
                  aria-label="Start solving this instance"
                >
                  <Link
                    to={`/instances/${encodeURIComponent(instance.id)}/solve`}
                  >
                    <PlayIcon />
                    Solve
                  </Link>
                </Button>
              </div>
            </header>

            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
              <StatCell label="Horizon" value={`${instance.horizonDays} days`} />
              <StatCell label="Nurses" value={instance.nurses.length} />
              <StatCell label="Shifts" value={instance.shifts.length} />
              <StatCell
                label="Coverage slots"
                value={instance.coverage.length}
              />
            </div>

            <div className="grid gap-6 md:grid-cols-2">
              <Card>
                <CardHeader>
                  <CardTitle className="text-base">Shifts</CardTitle>
                  <CardDescription>
                    Named blocks that a nurse can work. Missing times render as
                    an abstract lane in the Gantt view.
                  </CardDescription>
                </CardHeader>
                <CardContent className="flex flex-col gap-2">
                  {instance.shifts.map((s) => (
                    <div
                      key={s.id}
                      className="flex items-center justify-between rounded-md border border-border bg-muted/20 px-3 py-2 text-sm"
                    >
                      <div className="flex items-center gap-3">
                        <span className="font-mono font-semibold">{s.id}</span>
                        <span>{s.label}</span>
                        {s.skill && (
                          <Badge variant="secondary" className="font-mono text-[10px]">
                            {s.skill}
                          </Badge>
                        )}
                      </div>
                      <span className="font-mono text-xs text-muted-foreground">
                        {shiftTimeRange(s) || "—"}
                      </span>
                    </div>
                  ))}
                </CardContent>
              </Card>

              <Card>
                <CardHeader>
                  <CardTitle className="text-base">Nurses</CardTitle>
                  <CardDescription>
                    Skills, contract hours, and unavailability windows drive
                    hard and soft constraints.
                  </CardDescription>
                </CardHeader>
                <CardContent className="flex flex-col divide-y divide-border">
                  {instance.nurses.map((n) => (
                    <div
                      key={n.id}
                      className="flex items-center justify-between py-1.5 text-sm"
                    >
                      <div className="flex items-center gap-2">
                        <span className="font-mono text-xs text-muted-foreground">
                          {n.id}
                        </span>
                        <span>{n.name ?? n.id}</span>
                      </div>
                      <div className="flex items-center gap-1">
                        {(n.skills ?? []).map((sk) => (
                          <Badge
                            key={sk}
                            variant="outline"
                            className="font-mono text-[10px]"
                          >
                            {sk}
                          </Badge>
                        ))}
                      </div>
                    </div>
                  ))}
                </CardContent>
              </Card>
            </div>

            <Card>
              <CardHeader>
                <CardTitle className="text-base">
                  Coverage requirements
                </CardTitle>
                <CardDescription>
                  Each (day, shift) cell lists the number of nurses required.
                  Darker cells need more staff.
                </CardDescription>
              </CardHeader>
              <CardContent>
                <CoverageMatrix instance={instance} />
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle className="text-base">
                  <button
                    type="button"
                    onClick={() => setRawOpen((v) => !v)}
                    className="inline-flex items-center gap-1 text-left"
                    aria-expanded={rawOpen}
                  >
                    {rawOpen ? (
                      <ChevronDownIcon className="size-4" />
                    ) : (
                      <ChevronRightIcon className="size-4" />
                    )}
                    Show raw JSON
                  </button>
                </CardTitle>
              </CardHeader>
              {rawOpen && (
                <CardContent>
                  <Separator className="mb-3" />
                  <pre className="max-h-96 overflow-auto rounded-md border border-border bg-muted p-3 text-xs">
                    <code>{JSON.stringify(instance, null, 2)}</code>
                  </pre>
                </CardContent>
              )}
            </Card>
          </>
        ) : (
          <ErrorDisplay error={new Error("Instance not found.")} />
        )}
      </div>

      <Dialog open={confirmDelete} onOpenChange={setConfirmDelete}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete instance?</DialogTitle>
            <DialogDescription>
              This will remove <span className="font-mono">{instanceId}</span>{" "}
              and all of its solve jobs. This action cannot be undone.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => setConfirmDelete(false)}
              disabled={deleteMutation.isPending}
            >
              Cancel
            </Button>
            <Button
              variant="destructive"
              onClick={() => deleteMutation.mutate()}
              disabled={deleteMutation.isPending}
            >
              Delete
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </AppShell>
  );
}

function StatCell({
  label,
  value,
}: {
  label: string;
  value: string | number;
}) {
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
      <Skeleton className="h-32" />
      <Skeleton className="h-48" />
    </div>
  );
}
