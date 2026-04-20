import { Link } from "react-router";
import { useQuery } from "@tanstack/react-query";
import type { Route } from "./+types/health-check";
import { Button } from "~/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "~/components/ui/card";
import { BackendSwitch } from "~/components/backend-switch";
import { useBackend } from "~/lib/backend";

export function meta(_: Route.MetaArgs) {
  return [
    { title: "Health check · cp-deep-dive" },
    {
      name: "description",
      content: "Pings the configured backend's /health endpoint.",
    },
  ];
}

interface HealthPayload {
  status?: string;
  service?: string;
  [key: string]: unknown;
}

async function fetchHealth(baseUrl: string): Promise<HealthPayload> {
  const res = await fetch(`${baseUrl.replace(/\/$/, "")}/health`, {
    headers: { Accept: "application/json" },
  });
  if (!res.ok) {
    throw new Error(`HTTP ${res.status} ${res.statusText}`);
  }
  return (await res.json()) as HealthPayload;
}

export default function HealthCheck() {
  const { backend, baseUrl } = useBackend();

  const query = useQuery({
    queryKey: ["health", backend, baseUrl],
    queryFn: () => fetchHealth(baseUrl),
    enabled: typeof window !== "undefined", // client-only fetch
  });

  return (
    <main className="min-h-dvh bg-background text-foreground">
      <div className="mx-auto flex w-full max-w-3xl flex-col gap-8 px-6 py-16">
        <header className="flex items-start justify-between gap-4">
          <div className="flex flex-col gap-1">
            <span className="text-xs font-medium tracking-widest text-muted-foreground uppercase">
              Diagnostics
            </span>
            <h1 className="text-2xl font-semibold tracking-tight md:text-3xl">
              Backend health check
            </h1>
          </div>
          <BackendSwitch />
        </header>

        <Card>
          <CardHeader>
            <CardTitle>
              GET {baseUrl}/health
            </CardTitle>
            <CardDescription>
              Active backend: <strong>{backend}</strong>. Configure URLs via{" "}
              <code>VITE_PY_API_URL</code> and <code>VITE_KT_API_URL</code>.
            </CardDescription>
          </CardHeader>
          <CardContent className="flex flex-col gap-4">
            <div className="flex flex-wrap items-center gap-3">
              <Button
                type="button"
                onClick={() => query.refetch()}
                disabled={query.isFetching}
              >
                {query.isFetching ? "Checking…" : "Refresh"}
              </Button>
              <Button asChild variant="ghost">
                <Link to="/">Back to home</Link>
              </Button>
              <StatusBadge
                pending={query.isPending || query.isFetching}
                error={query.isError}
              />
            </div>

            <pre className="max-h-96 overflow-auto rounded-md border border-border bg-muted/50 p-4 text-xs leading-relaxed">
              {renderBody(query.data, query.error, query.isPending)}
            </pre>
          </CardContent>
        </Card>
      </div>
    </main>
  );
}

function StatusBadge({
  pending,
  error,
}: {
  pending: boolean;
  error: boolean;
}) {
  const tone = pending
    ? "bg-muted text-muted-foreground"
    : error
      ? "bg-destructive/10 text-destructive"
      : "bg-primary/10 text-primary";
  const label = pending ? "pending" : error ? "error" : "ok";
  return (
    <span
      className={`inline-flex items-center rounded-md px-2 py-0.5 text-xs font-medium ${tone}`}
    >
      {label}
    </span>
  );
}

function renderBody(
  data: HealthPayload | undefined,
  error: unknown,
  pending: boolean,
): string {
  if (pending) return "Fetching…";
  if (error) {
    const msg =
      error instanceof Error ? error.message : String(error);
    return [
      "// Request failed.",
      "// The backend is not up yet — this is expected during Phase 7 scaffolding.",
      "",
      msg,
    ].join("\n");
  }
  if (!data) return "// no data";
  return JSON.stringify(data, null, 2);
}
