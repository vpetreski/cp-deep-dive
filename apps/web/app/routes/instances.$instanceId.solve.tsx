import { Link, useNavigate, useParams } from "react-router";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery } from "@tanstack/react-query";
import { z } from "zod";
import { ArrowLeftIcon, Loader2Icon, PlayIcon } from "lucide-react";
import { toast } from "sonner";

import type { Route } from "./+types/instances.$instanceId.solve";
import { AppShell } from "~/components/app-shell";
import { Button } from "~/components/ui/button";
import { Input } from "~/components/ui/input";
import { Label } from "~/components/ui/label";
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "~/components/ui/card";
import { Separator } from "~/components/ui/separator";
import { Skeleton } from "~/components/ui/skeleton";
import { ErrorDisplay } from "~/components/error-display";
import { useApi, useBackend } from "~/lib/backend";
import { queryKeys } from "~/lib/query-keys";
import { getExampleById } from "~/lib/examples";
import type { Backend } from "~/lib/backend";
import type { NspInstance, SolveRequest } from "~/lib/types";
import { BackendSwitch } from "~/components/backend-switch";

export function meta({ params }: Route.MetaArgs) {
  return [
    { title: `Solve ${params.instanceId} — NSP Scheduler` },
  ];
}

// The form binds to string inputs (HTML numeric inputs emit strings) and we
// normalize to numbers ourselves. Zod pipes give us validation + coercion
// without running into zod v4 / hookform resolvers input/output-type drift.
const numInRange = (min: number, max: number) =>
  z
    .union([z.number(), z.string()])
    .transform((v, ctx) => {
      const n = typeof v === "number" ? v : Number(v);
      if (!Number.isFinite(n) || !Number.isInteger(n)) {
        ctx.addIssue({ code: "custom", message: "must be an integer" });
        return z.NEVER;
      }
      if (n < min || n > max) {
        ctx.addIssue({
          code: "custom",
          message: `must be between ${min} and ${max}`,
        });
        return z.NEVER;
      }
      return n;
    });

const FormSchema = z.object({
  maxTimeSeconds: numInRange(1, 3600),
  numSearchWorkers: numInRange(0, 64),
  randomSeed: z
    .union([z.literal(""), z.number(), z.string()])
    .optional()
    .transform((v, ctx) => {
      if (v === undefined || v === "") return undefined;
      const n = typeof v === "number" ? v : Number(v);
      if (!Number.isFinite(n) || !Number.isInteger(n) || n < 0) {
        ctx.addIssue({ code: "custom", message: "must be a non-negative integer" });
        return z.NEVER;
      }
      return n;
    }),
  SC1: numInRange(0, 1000),
  SC2: numInRange(0, 1000),
  SC3: numInRange(0, 1000),
  SC4: numInRange(0, 1000),
  SC5: numInRange(0, 1000),
});

type FormInput = z.input<typeof FormSchema>;
type FormOutput = z.output<typeof FormSchema>;

const SOFT_CONSTRAINTS: {
  id: "SC1" | "SC2" | "SC3" | "SC4" | "SC5";
  label: string;
  description: string;
  defaultWeight: number;
}[] = [
  {
    id: "SC1",
    label: "Preferences",
    description: "Honour per-nurse shift / day-off requests.",
    defaultWeight: 10,
  },
  {
    id: "SC2",
    label: "Fairness",
    description: "Equal number of shifts across nurses.",
    defaultWeight: 5,
  },
  {
    id: "SC3",
    label: "Workload balance",
    description: "Spread total scheduled hours evenly.",
    defaultWeight: 2,
  },
  {
    id: "SC4",
    label: "Weekend distribution",
    description: "Share Saturday/Sunday shifts fairly.",
    defaultWeight: 3,
  },
  {
    id: "SC5",
    label: "Consecutive days off",
    description: "Cluster days off instead of isolating them.",
    defaultWeight: 1,
  },
];

export default function SolveConfig() {
  const { instanceId = "" } = useParams();
  const api = useApi();
  const { backend, baseUrl } = useBackend();
  const navigate = useNavigate();

  const instanceQuery = useQuery({
    queryKey: queryKeys.instance(backend, baseUrl, instanceId),
    queryFn: ({ signal }) => api.getInstance(instanceId, signal),
    enabled: typeof window !== "undefined" && !!instanceId,
  });
  const fallback: NspInstance | undefined = getExampleById(instanceId);
  const instance = instanceQuery.data ?? fallback;

  const form = useForm<FormValues>({
    resolver: zodResolver(FormSchema),
    defaultValues: {
      maxTimeSeconds: 60,
      numSearchWorkers: 4,
      randomSeed: "",
      SC1: 10,
      SC2: 5,
      SC3: 2,
      SC4: 3,
      SC5: 1,
    },
  });

  const mutation = useMutation({
    mutationFn: (body: SolveRequest) => api.postSolve(body),
    onSuccess: (accepted) => {
      toast.success("Solve started", {
        description: `Job ${accepted.jobId} accepted.`,
      });
      navigate(`/jobs/${encodeURIComponent(accepted.jobId)}`);
    },
    onError: (err) => {
      toast.error("Solve could not be started", {
        description: err instanceof Error ? err.message : String(err),
      });
    },
  });

  function onSubmit(values: FormValues) {
    if (!instance) return;
    const seed =
      values.randomSeed === "" ? undefined : Number(values.randomSeed);
    const body: SolveRequest = {
      instance,
      params: {
        maxTimeSeconds: values.maxTimeSeconds,
        numSearchWorkers: values.numSearchWorkers,
        ...(seed !== undefined ? { randomSeed: seed } : {}),
        objectiveWeights: {
          SC1: values.SC1,
          SC2: values.SC2,
          SC3: values.SC3,
          SC4: values.SC4,
          SC5: values.SC5,
        },
      },
    };
    mutation.mutate(body);
  }

  return (
    <AppShell>
      <div className="mx-auto flex max-w-3xl flex-col gap-6">
        <Link
          to={`/instances/${encodeURIComponent(instanceId)}`}
          className="inline-flex items-center gap-1 text-xs text-muted-foreground hover:text-foreground transition-colors w-fit"
        >
          <ArrowLeftIcon className="size-3" />
          Back to instance
        </Link>

        <header className="flex flex-col gap-1">
          <h1 className="text-2xl font-semibold tracking-tight">
            Solve configuration
          </h1>
          <p className="text-sm text-muted-foreground">
            {instance ? (
              <>
                Schedule <span className="font-mono">{instance.id}</span> —{" "}
                {instance.horizonDays} days, {instance.nurses.length} nurses,{" "}
                {instance.coverage.length} coverage slots.
              </>
            ) : (
              "Configure the solver before starting."
            )}
          </p>
        </header>

        {instanceQuery.isPending && !fallback ? (
          <Skeleton className="h-80" />
        ) : instanceQuery.isError && !fallback ? (
          <ErrorDisplay
            error={instanceQuery.error}
            onRetry={() => void instanceQuery.refetch()}
          />
        ) : (
          <form onSubmit={form.handleSubmit(onSubmit)} className="flex flex-col gap-6">
            <Card>
              <CardHeader>
                <CardTitle className="text-base">Solver</CardTitle>
                <CardDescription>
                  Wall-clock budget, worker parallelism, and optional seed for
                  reproducibility.
                </CardDescription>
              </CardHeader>
              <CardContent className="flex flex-col gap-4">
                <BackendFieldRow backend={backend} />
                <Separator />
                <div className="grid gap-4 sm:grid-cols-3">
                  <NumberField
                    id="maxTimeSeconds"
                    label="Time limit (s)"
                    hint="1 – 3600"
                    min={1}
                    max={3600}
                    {...form.register("maxTimeSeconds")}
                  />
                  <NumberField
                    id="numSearchWorkers"
                    label="Workers"
                    hint="0 = auto"
                    min={0}
                    max={64}
                    {...form.register("numSearchWorkers")}
                  />
                  <NumberField
                    id="randomSeed"
                    label="Random seed"
                    hint="optional"
                    min={0}
                    {...form.register("randomSeed")}
                  />
                </div>
                <FormErrors errors={form.formState.errors} />
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle className="text-base">Objective weights</CardTitle>
                <CardDescription>
                  Penalty multipliers for each soft constraint family. Higher
                  values bias the search harder against those violations.
                </CardDescription>
              </CardHeader>
              <CardContent className="flex flex-col gap-3">
                {SOFT_CONSTRAINTS.map((sc) => (
                  <div
                    key={sc.id}
                    className="grid grid-cols-[1fr_auto] items-start gap-3 rounded-md border border-border bg-muted/20 p-3"
                  >
                    <div className="flex flex-col gap-1">
                      <div className="flex items-center gap-2 text-sm font-medium">
                        <span className="font-mono text-xs text-muted-foreground">
                          {sc.id.replace("SC", "SC-")}
                        </span>
                        {sc.label}
                      </div>
                      <p className="text-xs text-muted-foreground">
                        {sc.description}
                      </p>
                    </div>
                    <NumberField
                      id={sc.id}
                      label=""
                      hint=""
                      min={0}
                      max={1000}
                      className="w-24"
                      {...form.register(sc.id)}
                      aria-label={`${sc.label} weight`}
                    />
                  </div>
                ))}
              </CardContent>
              <CardFooter className="justify-end gap-2">
                <Button asChild variant="outline">
                  <Link
                    to={`/instances/${encodeURIComponent(instanceId)}`}
                  >
                    Cancel
                  </Link>
                </Button>
                <Button
                  type="submit"
                  disabled={mutation.isPending || !instance}
                >
                  {mutation.isPending ? (
                    <>
                      <Loader2Icon className="animate-spin" />
                      Starting…
                    </>
                  ) : (
                    <>
                      <PlayIcon />
                      Start solve
                    </>
                  )}
                </Button>
              </CardFooter>
            </Card>
          </form>
        )}
      </div>
    </AppShell>
  );
}

interface NumberFieldProps
  extends Omit<React.ComponentProps<typeof Input>, "name"> {
  id: string;
  label: string;
  hint?: string;
  name?: string;
}

function NumberField({
  id,
  label,
  hint,
  className,
  ...props
}: NumberFieldProps) {
  return (
    <div className="flex flex-col gap-1.5">
      {label && <Label htmlFor={id}>{label}</Label>}
      <Input
        id={id}
        type="number"
        inputMode="numeric"
        className={className}
        {...props}
      />
      {hint && <p className="text-[11px] text-muted-foreground">{hint}</p>}
    </div>
  );
}

function BackendFieldRow({ backend }: { backend: Backend }) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-3 rounded-md border border-border bg-muted/20 px-3 py-2">
      <div className="flex flex-col">
        <Label className="text-sm">Backend</Label>
        <p className="text-xs text-muted-foreground">
          Currently <span className="font-mono">{backend}</span>. The solve is
          dispatched to that backend; switch here if needed.
        </p>
      </div>
      <BackendSwitch />
    </div>
  );
}

function FormErrors({
  errors,
}: {
  errors: Record<string, { message?: string } | undefined>;
}) {
  const messages = Object.entries(errors)
    .map(([k, v]) => (v?.message ? `${k}: ${v.message}` : null))
    .filter(Boolean) as string[];
  if (messages.length === 0) return null;
  return (
    <ul className="list-disc pl-5 text-xs text-destructive">
      {messages.map((m, i) => (
        <li key={i}>{m}</li>
      ))}
    </ul>
  );
}
