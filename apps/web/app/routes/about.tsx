import { useQueries } from "@tanstack/react-query";
import {
  BookOpenIcon,
  BoxIcon,
  CheckIcon,
  ExternalLinkIcon,
  GitForkIcon,
  LoaderIcon,
  OctagonXIcon,
} from "lucide-react";

import type { Route } from "./+types/about";
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
import { makeApiClient } from "~/lib/api";
import { APP_VERSION } from "~/lib/version";

export function meta(_: Route.MetaArgs) {
  return [
    { title: "About — NSP Scheduler" },
    {
      name: "description",
      content:
        "How the Nurse Scheduling Problem is modeled, which solver powers it, and which backend you're talking to.",
    },
  ];
}

interface BackendTarget {
  key: "python" | "kotlin";
  label: string;
  baseUrl: string;
  stack: string;
}

function getTargets(): BackendTarget[] {
  const pyUrl = import.meta.env.VITE_PY_API_URL ?? "http://localhost:8000";
  const ktUrl = import.meta.env.VITE_KT_API_URL ?? "http://localhost:8080";
  return [
    {
      key: "python",
      label: "Python",
      baseUrl: pyUrl,
      stack: "FastAPI + OR-Tools CP-SAT",
    },
    {
      key: "kotlin",
      label: "Kotlin",
      baseUrl: ktUrl,
      stack: "Ktor 3 + cpsat-kt (OR-Tools CP-SAT)",
    },
  ];
}

export default function About() {
  const targets = getTargets();
  const ssr = typeof window === "undefined";

  const queries = useQueries({
    queries: targets.flatMap((t) => [
      {
        queryKey: ["about", "health", t.key, t.baseUrl],
        queryFn: ({ signal }: { signal?: AbortSignal }) =>
          makeApiClient(t.baseUrl).getHealth(signal),
        enabled: !ssr,
        staleTime: 15_000,
        retry: false,
      },
      {
        queryKey: ["about", "version", t.key, t.baseUrl],
        queryFn: ({ signal }: { signal?: AbortSignal }) =>
          makeApiClient(t.baseUrl).getVersion(signal),
        enabled: !ssr,
        staleTime: 15_000,
        retry: false,
      },
    ]),
  });

  return (
    <AppShell>
      <div className="flex flex-col gap-8">
        <header className="flex flex-col gap-2">
          <span className="text-xs font-medium tracking-widest text-muted-foreground uppercase">
            About
          </span>
          <h1 className="text-3xl font-semibold tracking-tight">
            NSP Scheduler
          </h1>
          <p className="max-w-2xl text-sm text-muted-foreground">
            A learning-grade yet practical reference implementation of the
            Nurse Scheduling Problem, solved with Google OR-Tools CP-SAT via
            twin backends (Python FastAPI and Kotlin Ktor 3) and a single
            React 19 + React Router v7 frontend.
          </p>
        </header>

        <div className="grid gap-6 md:grid-cols-2">
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2 text-base">
                <BookOpenIcon className="size-4" />
                What is NSP?
              </CardTitle>
              <CardDescription>
                Assign nurses to shifts over a planning horizon so that every
                (day, shift) cell has enough qualified staff, without violating
                labour rules.
              </CardDescription>
            </CardHeader>
            <CardContent className="flex flex-col gap-3 text-sm">
              <p>
                <span className="font-medium">Hard constraints (HC-1 … HC-8)</span>
                {" "}are the rules that every schedule must satisfy — coverage
                demand, one shift per day, forbidden transitions, max
                consecutive days, rest windows, skill matches, unavailability,
                and weekly shift bounds.
              </p>
              <p>
                <span className="font-medium">Soft constraints (SC-1 … SC-5)</span>
                {" "}are the quality dials — individual preferences, fairness,
                workload balance, weekend distribution, and consecutive days
                off. The solver minimises a weighted sum of their violations.
              </p>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2 text-base">
                <BoxIcon className="size-4" />
                How the solver works
              </CardTitle>
              <CardDescription>
                CP-SAT is a hybrid CP + SAT + LP solver from Google's OR-Tools.
                It wins at combinatorial problems like scheduling.
              </CardDescription>
            </CardHeader>
            <CardContent className="flex flex-col gap-3 text-sm">
              <p>
                Each (nurse, day, shift) tuple is a 0/1 decision variable. Hard
                constraints are linear inequalities over those variables; soft
                constraints contribute to a weighted objective. A built-in
                portfolio of LNS, probing, and lazy-clause-generation workers
                explores the search space in parallel.
              </p>
              <p className="text-muted-foreground">
                You can tune the time limit, worker count, and weights on the
                <em> Solve configuration</em> page.
              </p>
            </CardContent>
          </Card>
        </div>

        <section className="flex flex-col gap-3">
          <h2 className="text-lg font-semibold tracking-tight">Backends</h2>
          <p className="text-sm text-muted-foreground">
            Both backends implement the same OpenAPI 3.1 contract. You can
            switch between them anywhere in the app — your query cache is
            scoped by backend, so there's no cross-contamination.
          </p>
          <div className="grid gap-4 md:grid-cols-2">
            {targets.map((t, i) => {
              const health = queries[i * 2];
              const version = queries[i * 2 + 1];
              return (
                <BackendCard
                  key={t.key}
                  target={t}
                  health={health}
                  version={version}
                />
              );
            })}
          </div>
        </section>

        <Card>
          <CardHeader>
            <CardTitle className="text-base">Links & reference</CardTitle>
            <CardDescription>
              Everything you need to dig deeper or reproduce the project.
            </CardDescription>
          </CardHeader>
          <CardContent className="grid gap-3 sm:grid-cols-2">
            <LinkRow
              href="/openapi.yaml"
              label="OpenAPI 3.1 spec"
              description="Wire contract shared by both backends and the frontend."
            />
            <LinkRow
              href="https://developers.google.com/optimization/cp/cp_solver"
              label="OR-Tools CP-SAT"
              description="Google's reference documentation for the solver."
              external
            />
            <LinkRow
              href="https://github.com/"
              label="Source on GitHub"
              description="Repository with Python, Kotlin, MiniZinc, and docs."
              external
              icon={<GitForkIcon className="size-4" />}
            />
            <LinkRow
              href="/health-check"
              label="Health check console"
              description="Ping the currently-selected backend's /health endpoint."
            />
          </CardContent>
        </Card>

        <footer className="flex flex-wrap items-center justify-between gap-2 rounded-md border border-border bg-muted/30 px-4 py-3 text-xs text-muted-foreground">
          <span>
            Frontend <span className="font-mono">v{APP_VERSION}</span>
            {" · "}
            React Router v7 · Vite · Tailwind
          </span>
          <span>
            Keyboard shortcuts: press{" "}
            <kbd className="rounded border border-border bg-background px-1 font-mono">
              ?
            </kbd>
          </span>
        </footer>
      </div>
    </AppShell>
  );
}

interface QueryLike {
  data?: unknown;
  error: unknown;
  isPending: boolean;
  isFetching: boolean;
  isError: boolean;
}

interface VersionShape {
  version?: string;
  ortools?: string;
  runtime?: string;
  service?: string;
}
interface HealthShape {
  status?: string;
  service?: string;
}

function BackendCard({
  target,
  health,
  version,
}: {
  target: BackendTarget;
  health: QueryLike;
  version: QueryLike;
}) {
  const healthData = (health.data as HealthShape | undefined) ?? undefined;
  const versionData = (version.data as VersionShape | undefined) ?? undefined;
  const status: "ok" | "pending" | "error" =
    health.isPending || health.isFetching
      ? "pending"
      : health.isError
        ? "error"
        : "ok";

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          {target.label}
          <StatusPill status={status} />
        </CardTitle>
        <CardDescription>
          <span className="font-mono text-xs">{target.baseUrl}</span>
          <br />
          {target.stack}
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-2 text-xs">
        <Row
          label="Service"
          value={
            versionData?.service ??
            healthData?.service ??
            (target.key === "python" ? "py-api" : "kt-api")
          }
        />
        <Row
          label="API version"
          value={versionData?.version ?? "—"}
          mono
        />
        <Row
          label="OR-Tools"
          value={versionData?.ortools ?? "—"}
          mono
        />
        {versionData?.runtime && (
          <Row label="Runtime" value={versionData.runtime} mono />
        )}
        {health.isError && (
          <p className="mt-1 text-[11px] text-destructive">
            {(health.error as Error | undefined)?.message ??
              "Backend unreachable."}
          </p>
        )}
      </CardContent>
    </Card>
  );
}

function Row({
  label,
  value,
  mono,
}: {
  label: string;
  value: string;
  mono?: boolean;
}) {
  return (
    <div className="flex items-center justify-between gap-3">
      <span className="text-muted-foreground">{label}</span>
      <span className={mono ? "font-mono" : undefined}>{value}</span>
    </div>
  );
}

function StatusPill({ status }: { status: "ok" | "pending" | "error" }) {
  if (status === "pending") {
    return (
      <Badge
        variant="outline"
        className="gap-1 font-mono text-[10px] uppercase"
      >
        <LoaderIcon className="size-3 animate-spin" />
        checking
      </Badge>
    );
  }
  if (status === "error") {
    return (
      <Badge
        variant="destructive"
        className="gap-1 font-mono text-[10px] uppercase"
      >
        <OctagonXIcon className="size-3" />
        offline
      </Badge>
    );
  }
  return (
    <Badge className="gap-1 bg-emerald-500/15 text-emerald-700 font-mono text-[10px] uppercase dark:text-emerald-300">
      <CheckIcon className="size-3" />
      online
    </Badge>
  );
}

function LinkRow({
  href,
  label,
  description,
  external,
  icon,
}: {
  href: string;
  label: string;
  description: string;
  external?: boolean;
  icon?: React.ReactNode;
}) {
  return (
    <Button
      asChild
      variant="outline"
      className="h-auto justify-start whitespace-normal px-3 py-2 text-left"
    >
      <a
        href={href}
        target={external ? "_blank" : undefined}
        rel={external ? "noreferrer" : undefined}
      >
        <span className="flex items-start gap-2">
          {icon ?? <ExternalLinkIcon className="mt-0.5 size-4 shrink-0" />}
          <span className="flex flex-col items-start">
            <span className="text-sm font-medium">{label}</span>
            <span className="text-xs text-muted-foreground">
              {description}
            </span>
          </span>
        </span>
      </a>
    </Button>
  );
}
