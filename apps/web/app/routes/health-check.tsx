import { Link } from "react-router";
import { useQuery } from "@tanstack/react-query";
import { RefreshCwIcon } from "lucide-react";

import type { Route } from "./+types/health-check";
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
import { useApi, useBackend } from "~/lib/backend";
import { queryKeys } from "~/lib/query-keys";

export function meta(_: Route.MetaArgs) {
  return [
    { title: "Health check — NSP Scheduler" },
    {
      name: "description",
      content: "Pings the configured backend's /health endpoint.",
    },
  ];
}

export default function HealthCheck() {
  const { backend, baseUrl } = useBackend();
  const api = useApi();

  const healthQuery = useQuery({
    queryKey: queryKeys.health(backend, baseUrl),
    queryFn: ({ signal }) => api.getHealth(signal),
    enabled: typeof window !== "undefined",
    retry: false,
  });
  const versionQuery = useQuery({
    queryKey: queryKeys.version(backend, baseUrl),
    queryFn: ({ signal }) => api.getVersion(signal),
    enabled: typeof window !== "undefined",
    retry: false,
  });

  const pending = healthQuery.isPending || healthQuery.isFetching;

  return (
    <AppShell>
      <div className="mx-auto flex max-w-3xl flex-col gap-6">
        <header className="flex flex-col gap-1">
          <span className="text-xs font-medium tracking-widest text-muted-foreground uppercase">
            Diagnostics
          </span>
          <h1 className="text-2xl font-semibold tracking-tight">
            Backend health check
          </h1>
          <p className="text-sm text-muted-foreground">
            Active backend: <strong>{backend}</strong>. Configure URLs via{" "}
            <code className="font-mono text-xs">VITE_PY_API_URL</code> and{" "}
            <code className="font-mono text-xs">VITE_KT_API_URL</code>.
          </p>
        </header>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-base">
              GET <span className="font-mono">{baseUrl}/health</span>
              <StatusPill
                pending={pending}
                error={healthQuery.isError}
                ok={healthQuery.isSuccess}
              />
            </CardTitle>
            <CardDescription>
              This route is a lightweight connectivity probe. It does not
              require a database or solver worker to be up.
            </CardDescription>
          </CardHeader>
          <CardContent className="flex flex-col gap-3">
            <div className="flex flex-wrap items-center gap-2">
              <Button
                type="button"
                onClick={() => {
                  void healthQuery.refetch();
                  void versionQuery.refetch();
                }}
                disabled={pending}
              >
                <RefreshCwIcon className={pending ? "animate-spin" : undefined} />
                Refresh
              </Button>
              <Button asChild variant="ghost">
                <Link to="/">Back to home</Link>
              </Button>
            </div>

            <pre className="max-h-96 overflow-auto rounded-md border border-border bg-muted/50 p-4 text-xs leading-relaxed">
              {renderBody({
                data: healthQuery.data,
                error: healthQuery.error,
                pending,
              })}
            </pre>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-base">
              GET <span className="font-mono">{baseUrl}/version</span>
            </CardTitle>
            <CardDescription>
              Backend build metadata — service name, version, OR-Tools version.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <pre className="max-h-60 overflow-auto rounded-md border border-border bg-muted/50 p-4 text-xs leading-relaxed">
              {renderBody({
                data: versionQuery.data,
                error: versionQuery.error,
                pending: versionQuery.isPending || versionQuery.isFetching,
              })}
            </pre>
          </CardContent>
        </Card>
      </div>
    </AppShell>
  );
}

function StatusPill({
  pending,
  error,
  ok,
}: {
  pending: boolean;
  error: boolean;
  ok: boolean;
}) {
  if (pending) {
    return (
      <Badge variant="outline" className="font-mono text-[10px] uppercase">
        pending
      </Badge>
    );
  }
  if (error) {
    return (
      <Badge variant="destructive" className="font-mono text-[10px] uppercase">
        error
      </Badge>
    );
  }
  if (ok) {
    return (
      <Badge className="bg-emerald-500/15 text-emerald-700 font-mono text-[10px] uppercase dark:text-emerald-300">
        ok
      </Badge>
    );
  }
  return null;
}

function renderBody({
  data,
  error,
  pending,
}: {
  data: unknown;
  error: unknown;
  pending: boolean;
}): string {
  if (pending) return "Fetching…";
  if (error) {
    const msg =
      error instanceof Error ? error.message : String(error);
    return [
      "// Request failed.",
      "// If the backend isn't running yet, start the Python or Kotlin server.",
      "",
      msg,
    ].join("\n");
  }
  if (data === undefined) return "// no data";
  return JSON.stringify(data, null, 2);
}
