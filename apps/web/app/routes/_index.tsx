import { Link } from "react-router";
import type { Route } from "./+types/_index";
import { Button } from "~/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "~/components/ui/card";
import { BackendSwitch } from "~/components/backend-switch";

export function meta(_: Route.MetaArgs) {
  return [
    { title: "cp-deep-dive — NSP" },
    {
      name: "description",
      content:
        "Constraint Programming deep dive with OR-Tools CP-SAT — the Nurse Scheduling Problem, Python + Kotlin twins, with a Vite + React Router v7 frontend.",
    },
  ];
}

export default function Home() {
  return (
    <main className="min-h-dvh bg-background text-foreground">
      <div className="mx-auto flex w-full max-w-5xl flex-col gap-12 px-6 py-16 md:py-24">
        <header className="flex items-start justify-between gap-4">
          <div className="flex flex-col gap-1">
            <span className="text-xs font-medium tracking-widest text-muted-foreground uppercase">
              cp-deep-dive
            </span>
            <span className="text-xs text-muted-foreground">
              Phase 7 · NSP application
            </span>
          </div>
          <BackendSwitch />
        </header>

        <section className="flex flex-col gap-6">
          <h1 className="text-4xl font-semibold leading-tight tracking-tight md:text-5xl">
            Nurse Scheduling, solved with
            <span className="text-muted-foreground"> CP-SAT.</span>
          </h1>
          <p className="max-w-2xl text-base text-muted-foreground md:text-lg">
            A study companion for Constraint Programming with Google OR-Tools.
            Two backends — FastAPI and Ktor 3 — speak the same OpenAPI
            contract. This frontend talks to either, so you can compare the two
            side by side while working through the chapters.
          </p>
          <div className="flex flex-wrap gap-3">
            <Button asChild size="lg">
              <Link to="/health-check">Check backend health</Link>
            </Button>
            <Button asChild size="lg" variant="outline">
              <a
                href="https://developers.google.com/optimization/cp/cp_solver"
                target="_blank"
                rel="noreferrer"
              >
                OR-Tools CP-SAT docs
              </a>
            </Button>
          </div>
        </section>

        <section className="grid gap-4 md:grid-cols-3">
          <Card>
            <CardHeader>
              <CardTitle>Phase 7 · coming soon</CardTitle>
              <CardDescription>
                Instance upload, streaming solve progress, roster visualization.
              </CardDescription>
            </CardHeader>
            <CardContent className="text-sm text-muted-foreground">
              Built strictly from <code>specs/nsp-app/</code> once the spec is
              locked in Chapter 14.
            </CardContent>
          </Card>
          <Card>
            <CardHeader>
              <CardTitle>Two backends, one UI</CardTitle>
              <CardDescription>
                Switch between Python and Kotlin with the toggle — same API
                contract, same endpoints.
              </CardDescription>
            </CardHeader>
            <CardContent className="text-sm text-muted-foreground">
              Contract lives in <code>apps/shared/openapi.yaml</code>.
            </CardContent>
          </Card>
          <Card>
            <CardHeader>
              <CardTitle>Stack</CardTitle>
              <CardDescription>
                Vite · React 19 · React Router v7 · Tailwind 4 · shadcn/ui ·
                TanStack Query 5.
              </CardDescription>
            </CardHeader>
            <CardContent className="text-sm text-muted-foreground">
              Typed end to end. <code>npm run typecheck</code> is the CI gate.
            </CardContent>
          </Card>
        </section>
      </div>
    </main>
  );
}
