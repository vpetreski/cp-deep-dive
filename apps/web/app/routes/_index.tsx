import { useNavigate } from "react-router";
import { useMutation } from "@tanstack/react-query";
import { toast } from "sonner";
import {
  ArrowRightIcon,
  SparklesIcon,
  ActivityIcon,
  KeyboardIcon,
} from "lucide-react";

import type { Route } from "./+types/_index";
import { AppShell } from "~/components/app-shell";
import { InstanceUpload } from "~/components/instance-upload";
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "~/components/ui/card";
import { Button } from "~/components/ui/button";
import { Badge } from "~/components/ui/badge";
import { Separator } from "~/components/ui/separator";
import { EXAMPLE_INSTANCES } from "~/lib/examples";
import { useApi } from "~/lib/backend";
import { NspApiError } from "~/lib/api";
import type { NspInstance } from "~/lib/types";

export function meta(_: Route.MetaArgs) {
  return [
    { title: "NSP Scheduler — Home" },
    {
      name: "description",
      content:
        "Upload a Nurse Scheduling Problem instance or pick a toy example to run it against the CP-SAT solver.",
    },
  ];
}

export default function Home() {
  const navigate = useNavigate();
  const api = useApi();

  const uploadMutation = useMutation({
    mutationFn: (instance: NspInstance) => api.createInstance(instance),
    onSuccess: (created) => {
      toast.success("Instance uploaded", {
        description: `Created ${created.id}.`,
      });
      navigate(`/instances/${encodeURIComponent(created.id)}`);
    },
    onError: (err) => {
      const msg =
        err instanceof NspApiError
          ? `${err.message}${err.code ? ` (${err.code})` : ""}`
          : err instanceof Error
            ? err.message
            : String(err);
      toast.error("Upload failed", { description: msg });
    },
  });

  return (
    <AppShell>
      <section className="flex flex-col gap-10">
        <Hero />
        <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_360px]">
          <div className="flex flex-col gap-6">
            <Card>
              <CardHeader>
                <CardTitle className="text-xl">
                  Start a new schedule
                </CardTitle>
                <CardDescription>
                  Drop a JSON instance or paste one in. Files are validated
                  against the NSP instance schema before upload.
                </CardDescription>
              </CardHeader>
              <CardContent>
                <InstanceUpload
                  onSubmit={async (instance) => {
                    await uploadMutation.mutateAsync(instance);
                  }}
                  pending={uploadMutation.isPending}
                />
              </CardContent>
            </Card>
            <ExamplesSection
              onPick={(instance) => uploadMutation.mutate(instance)}
              pending={uploadMutation.isPending}
            />
          </div>
          <aside className="flex flex-col gap-4">
            <Card>
              <CardHeader>
                <CardTitle className="text-sm">
                  Do not upload real nurse names
                </CardTitle>
                <CardDescription>
                  This deployment is a teaching tool. Use pseudonyms or generic
                  ids — avoid uploading PII, shift notes, or contract text from
                  a live roster.
                </CardDescription>
              </CardHeader>
            </Card>
            <FeatureCards />
          </aside>
        </div>
      </section>
    </AppShell>
  );
}

function Hero() {
  return (
    <section className="flex flex-col gap-4 pt-2">
      <Badge variant="outline" className="w-fit">
        NSP · v1.0
      </Badge>
      <h1 className="text-3xl font-semibold tracking-tight md:text-4xl">
        Schedule a hospital ward in under a minute.
      </h1>
      <p className="max-w-2xl text-base text-muted-foreground md:text-lg">
        Upload a Nurse Scheduling Problem instance, configure the solver, and
        watch a CP-SAT search live-stream the best schedule it can find. Pick
        between Python (FastAPI) and Kotlin (Ktor 3) backends with the toggle
        in the header — same contract, same output.
      </p>
    </section>
  );
}

function ExamplesSection({
  onPick,
  pending,
}: {
  onPick: (instance: NspInstance) => void;
  pending: boolean;
}) {
  return (
    <section className="flex flex-col gap-3">
      <div className="flex items-center justify-between">
        <h2 className="text-sm font-semibold uppercase tracking-wider text-muted-foreground">
          Or try a bundled example
        </h2>
      </div>
      <div className="grid gap-4 md:grid-cols-2">
        {EXAMPLE_INSTANCES.map((ex) => (
          <Card key={ex.id}>
            <CardHeader>
              <CardTitle className="text-base">{ex.name ?? ex.id}</CardTitle>
              <CardDescription>
                <span className="font-mono text-xs">{ex.id}</span>
                <span className="mx-1 text-muted-foreground">·</span>
                {ex.horizonDays} days, {ex.nurses.length} nurses,{" "}
                {ex.shifts.length} shifts, {ex.coverage.length} coverage slots
              </CardDescription>
            </CardHeader>
            <CardFooter className="justify-end">
              <Button
                size="sm"
                onClick={() => onPick(ex)}
                disabled={pending}
                aria-label={`Load ${ex.name ?? ex.id}`}
              >
                Load <ArrowRightIcon />
              </Button>
            </CardFooter>
          </Card>
        ))}
      </div>
    </section>
  );
}

function FeatureCards() {
  const items = [
    {
      icon: SparklesIcon,
      title: "Live solver streams",
      body: "Every POST /solve opens a Server-Sent Events channel. Watch objective, bound, and gap improve in real time.",
    },
    {
      icon: ActivityIcon,
      title: "Two backends, one UI",
      body: "Switch Python ↔ Kotlin at any time — both implement the same OpenAPI contract byte-for-byte.",
    },
    {
      icon: KeyboardIcon,
      title: "Fully keyboard-driven",
      body: "Press ? for shortcuts. g h goes home, g i to instances. Schedule cells navigate with arrow keys.",
    },
  ];
  return (
    <div className="flex flex-col gap-3">
      {items.map(({ icon: Icon, title, body }) => (
        <Card key={title} size="sm">
          <CardContent className="flex gap-3 py-3">
            <div className="mt-0.5 grid size-8 place-items-center rounded-md bg-primary/10 text-primary">
              <Icon className="size-4" />
            </div>
            <div className="flex flex-col gap-0.5">
              <p className="text-sm font-medium">{title}</p>
              <p className="text-xs text-muted-foreground">{body}</p>
            </div>
          </CardContent>
        </Card>
      ))}
      <Separator />
      <p className="text-xs text-muted-foreground">
        Stack: Vite · React 19 · React Router v7 · TanStack Query 5 · shadcn/ui ·
        Tailwind 4.
      </p>
    </div>
  );
}
