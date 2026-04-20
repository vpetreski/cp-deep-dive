# Chapter 16 — Web UI: Vite + React 19 + React Router v7 (framework mode)

> **Phase 7: End-to-end application** · Estimated: ~6h · Status: ready-to-start · Last updated: 2026-04-19

## Goal

Build a lightweight, modern web UI for the NSP app: upload/select an instance, solve with live streaming, display the schedule grid with coverage and fairness KPIs, manually edit cells, re-validate, toggle between the Python and Kotlin backends — all with file-system routing, loaders/actions, React 19 Suspense, and TanStack Query.

## Before you start

- **Prerequisites:** Chapters 14 (locked spec), 15 (twin backends both running).
- **Required reading:**
  - `specs/nsp-app/07-ui-ux.md` — wireframes and user flows.
  - `apps/shared/openapi.yaml` — the contract your client consumes.
  - [React Router v7 framework-mode docs](https://reactrouter.com/start/framework/installation) — the Remix successor.
  - [React 19 release notes](https://react.dev/blog/2024/12/05/react-19) — especially `use()` and Suspense.
  - [TanStack Query v5 docs](https://tanstack.com/query/v5/docs/framework/react/overview).
  - shadcn/ui component catalog: <https://ui.shadcn.com/docs/components/>
- **Environment:** Node 22 LTS; `apps/web/` as a Vite project with React Router v7 framework mode.

## Concepts introduced this chapter

- **File-system routing (RR7 framework mode)** — routes live in `app/routes/*.tsx`; file paths become URLs.
- **Loaders** — server functions that run before rendering, prefetch data, return typed responses.
- **Actions** — server functions that handle form submissions; type-safe replacement for ad-hoc POSTs.
- **React 19 `use()` + Suspense** — read promises inline; the boundary shows a fallback while they resolve.
- **TanStack Query** — cache, refetch, and reconcile async state; the client-side cache layer above raw fetch.
- **EventSource / SSE in the browser** — native `EventSource` for streaming incumbents.
- **shadcn/ui** — component collection you copy-into-your-repo (not an npm install); Tailwind-based.
- **Tailwind 4** — zero-config via `@tailwindcss/vite` plugin.
- **Backend toggle** — the UI can switch between Python and Kotlin APIs by changing a base URL.

## 1. Intuition

The UI's job is to make the solver feel *instant*. No spinners-only-then-final-result; you show the incumbent as it improves, with the bound climbing alongside it. A nurse manager sees "best obj: 148" at 3s, then 142 at 7s, then 139 at 15s; by 20s the bound has caught up and she knows the answer is near-optimal.

On the architecture side, RR7 framework-mode means your UI screens are regular React components with colocated *loaders* that prefetch data before the component mounts. It's more like a Rails app than a classic SPA: each URL owns its data, the loader runs on the server (or edge), and by the time React renders, everything's already there.

Why *not* Next.js: Vite is faster in dev, has no vendor-specific primitives, and the RR7 team shipped the Remix successor explicitly for this kind of modern SSR/CSR hybrid without Next's magic.

## 2. Formal definition

### 2.1 Route map

```
app/
├── root.tsx                     # shell: header, theme toggle, backend toggle
├── routes/
│   ├── _index.tsx               # /        — home, recent instances
│   ├── instances.upload.tsx     # /instances/upload  — upload form
│   ├── instances.$id.tsx        # /instances/:id     — instance summary + solve button
│   ├── runs.$runId.tsx          # /runs/:runId       — live solve screen
│   ├── schedules.$id.tsx        # /schedules/:id     — grid viewer/editor
│   └── schedules.$id.edit.tsx   # /schedules/:id/edit
```

### 2.2 State model

- **Server state**: `tanstack-query` caches `GET /instances`, `GET /solve/{runId}`, `GET /schedules/{id}`.
- **Stream state**: `EventSource` opened for `/solve/{runId}/stream`, buffered into a React state slice on each event.
- **UI state**: React state for modals, editing mode, backend toggle.
- **Route state**: URL is the source of truth for "which instance am I looking at"; no client-side state duplication.

### 2.3 Backend toggle

A top-right dropdown `[Python ▾] | [Kotlin]` switches the base URL:

```typescript
// apps/web/app/lib/backend.ts
export type BackendId = "py" | "kt";
export const BACKENDS = {
  py: { label: "Python (FastAPI)", baseUrl: import.meta.env.VITE_PY_API_URL ?? "http://localhost:8000" },
  kt: { label: "Kotlin (Ktor)",   baseUrl: import.meta.env.VITE_KT_API_URL ?? "http://localhost:8080" },
} as const;
```

The selected backend lives in a `BackendContext`; every fetcher reads it.

### 2.4 Cross-language mapping

Not applicable — the UI is TypeScript only. Both backends are consumed via the generated types from `openapi-typescript` (Chapter 15, Exercise 15-E).

## 3. Worked example by hand

Nothing to hand-solve. The "worked example" is the ordered interaction: upload → solve → grid → edit → re-validate. You should be able to describe each step in one sentence; if you can't, the wireframes in `specs/nsp-app/07-ui-ux.md` aren't detailed enough.

## 4. Python implementation

Not applicable.

## 5. Kotlin implementation (via `cpsat-kt`)

Not applicable for this chapter (UI is TypeScript).

### 5 (alt). TypeScript / React 19 implementation

```
apps/web/
├── package.json
├── vite.config.ts
├── tsconfig.json
├── app/
│   ├── root.tsx
│   ├── entry.client.tsx
│   ├── entry.server.tsx
│   ├── lib/
│   │   ├── backend.ts
│   │   ├── queryClient.ts
│   │   └── api.ts
│   ├── components/
│   │   ├── ScheduleGrid.tsx
│   │   ├── KpiBar.tsx
│   │   └── SolveProgress.tsx
│   └── routes/
│       ├── _index.tsx
│       ├── instances.upload.tsx
│       ├── instances.$id.tsx
│       ├── runs.$runId.tsx
│       └── schedules.$id.tsx
└── public/
```

```typescript
// apps/web/app/lib/api.ts
import type { paths, components } from "./openapi-types";   // generated in Ch 15 Ex E
import { BACKENDS, type BackendId } from "./backend";

export type Instance = components["schemas"]["Instance"];
export type Schedule = components["schemas"]["Schedule"];
export type SolveRequest = paths["/solve"]["post"]["requestBody"]["content"]["application/json"];
export type SolveResponse = paths["/solve"]["post"]["responses"]["202"]["content"]["application/json"];

async function request<T>(backend: BackendId, path: string, init?: RequestInit): Promise<T> {
  const url = `${BACKENDS[backend].baseUrl}${path}`;
  const res = await fetch(url, {
    ...init,
    headers: { "Content-Type": "application/json", ...(init?.headers ?? {}) },
  });
  if (!res.ok) throw new Error(`API ${res.status}: ${await res.text()}`);
  return res.json() as Promise<T>;
}

export const api = {
  uploadInstance: (b: BackendId, body: Instance) =>
    request<{ id: string }>(b, "/instances", { method: "POST", body: JSON.stringify(body) }),
  getInstance: (b: BackendId, id: string) =>
    request<Instance>(b, `/instances/${id}`),
  startSolve: (b: BackendId, body: SolveRequest) =>
    request<SolveResponse>(b, "/solve", { method: "POST", body: JSON.stringify(body) }),
  cancelSolve: (b: BackendId, runId: string) =>
    fetch(`${BACKENDS[b].baseUrl}/solve/${runId}`, { method: "DELETE" }),
};
```

```typescript
// apps/web/app/lib/queryClient.ts
import { QueryClient } from "@tanstack/react-query";

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: { staleTime: 30_000, refetchOnWindowFocus: false },
  },
});
```

```tsx
// apps/web/app/root.tsx
import { Outlet, Scripts, ScrollRestoration, Links, Meta } from "react-router";
import { QueryClientProvider } from "@tanstack/react-query";
import { queryClient } from "./lib/queryClient";
import { BackendProvider, BackendToggle } from "./components/BackendToggle";
import "./styles/tailwind.css";

export default function App() {
  return (
    <html lang="en">
      <head>
        <Meta />
        <Links />
        <title>NSP App</title>
      </head>
      <body className="min-h-dvh bg-background text-foreground">
        <QueryClientProvider client={queryClient}>
          <BackendProvider>
            <header className="flex items-center justify-between p-4 border-b">
              <a href="/" className="font-semibold">NSP App</a>
              <BackendToggle />
            </header>
            <main className="p-6">
              <Outlet />
            </main>
          </BackendProvider>
        </QueryClientProvider>
        <ScrollRestoration />
        <Scripts />
      </body>
    </html>
  );
}
```

```tsx
// apps/web/app/routes/instances.$id.tsx
import { useLoaderData, useParams, type LoaderFunctionArgs, Link } from "react-router";
import { api } from "~/lib/api";
import { useBackend } from "~/components/BackendToggle";
import { Button } from "~/components/ui/button";

export async function loader({ params, request }: LoaderFunctionArgs) {
  const url = new URL(request.url);
  const backend = (url.searchParams.get("backend") ?? "py") as "py" | "kt";
  return api.getInstance(backend, params.id!);
}

export default function InstancePage() {
  const instance = useLoaderData<typeof loader>();
  const { id } = useParams();
  const { backend } = useBackend();

  return (
    <section className="max-w-3xl">
      <h1 className="text-2xl font-semibold mb-2">{instance.name ?? id}</h1>
      <ul className="text-sm text-muted-foreground mb-6">
        <li>{instance.nurses.length} nurses</li>
        <li>{instance.horizon.days} days</li>
        <li>{instance.shifts.length} shifts</li>
      </ul>
      <Button asChild>
        <Link to={`/runs/new?instanceId=${id}&backend=${backend}`}>Solve</Link>
      </Button>
    </section>
  );
}
```

```tsx
// apps/web/app/routes/runs.$runId.tsx
import { useEffect, useState } from "react";
import { useParams } from "react-router";
import { useBackend } from "~/components/BackendToggle";
import { BACKENDS } from "~/lib/backend";
import { KpiBar } from "~/components/KpiBar";
import { SolveProgress } from "~/components/SolveProgress";

type Incumbent = {
  objective: number;
  bound: number | null;
  gap: number | null;
  elapsedSeconds: number;
};

type Final = {
  status: "OPTIMAL" | "FEASIBLE" | "INFEASIBLE" | "CANCELLED" | "TIMEOUT";
  objective: number | null;
  bound: number | null;
  elapsedSeconds: number;
};

export default function RunPage() {
  const { runId } = useParams();
  const { backend } = useBackend();

  const [incumbents, setIncumbents] = useState<Incumbent[]>([]);
  const [final, setFinal] = useState<Final | null>(null);

  useEffect(() => {
    if (!runId) return;
    const url = `${BACKENDS[backend].baseUrl}/solve/${runId}/stream`;
    const es = new EventSource(url);

    es.addEventListener("incumbent", (e) => {
      const data = JSON.parse((e as MessageEvent).data) as Incumbent;
      setIncumbents((prev) => [...prev, data]);
    });
    es.addEventListener("final", (e) => {
      const data = JSON.parse((e as MessageEvent).data) as Final;
      setFinal(data);
      es.close();
    });
    es.onerror = () => es.close();

    return () => es.close();
  }, [runId, backend]);

  const latest = incumbents.at(-1);
  return (
    <section>
      <h1 className="text-2xl font-semibold mb-4">Run {runId}</h1>
      <KpiBar
        status={final?.status ?? "RUNNING"}
        objective={latest?.objective ?? null}
        bound={latest?.bound ?? null}
        gap={latest?.gap ?? null}
        elapsed={latest?.elapsedSeconds ?? 0}
      />
      <SolveProgress incumbents={incumbents} />
    </section>
  );
}
```

```tsx
// apps/web/app/components/ScheduleGrid.tsx
import clsx from "clsx";
import type { Schedule, Instance } from "~/lib/api";

type Props = {
  instance: Instance;
  schedule: Schedule;
  onEditCell?: (nurseId: string, day: number, shift: string | null) => void;
  violations?: Array<{ nurseId: string; day: number; rule: string }>;
};

export function ScheduleGrid({ instance, schedule, onEditCell, violations = [] }: Props) {
  const violationByCell = new Map<string, string>();
  for (const v of violations) {
    violationByCell.set(`${v.nurseId}|${v.day}`, v.rule);
  }
  const byCell = new Map<string, string>();
  for (const a of schedule.assignments) {
    byCell.set(`${a.nurse}|${a.day}`, a.shift);
  }

  return (
    <table className="border-collapse w-full text-xs font-mono">
      <thead>
        <tr>
          <th className="sticky left-0 bg-background">Nurse</th>
          {Array.from({ length: instance.horizon.days }, (_, d) => (
            <th key={d} className="px-1 font-normal">d{String(d).padStart(2, "0")}</th>
          ))}
        </tr>
      </thead>
      <tbody>
        {instance.nurses.map((n) => (
          <tr key={n.id}>
            <th className="sticky left-0 bg-background text-left pr-2">{n.id}</th>
            {Array.from({ length: instance.horizon.days }, (_, d) => {
              const shift = byCell.get(`${n.id}|${d}`) ?? "off";
              const violation = violationByCell.get(`${n.id}|${d}`);
              return (
                <td
                  key={d}
                  title={violation}
                  className={clsx(
                    "border p-1 text-center cursor-pointer",
                    shift === "off" && "bg-muted text-muted-foreground",
                    shift === "D" && "bg-sky-100",
                    shift === "E" && "bg-amber-100",
                    shift === "N" && "bg-indigo-100",
                    violation && "outline outline-2 outline-red-500",
                  )}
                  onClick={() => onEditCell?.(n.id, d, shift === "off" ? null : shift)}
                >
                  {shift === "off" ? "·" : shift}
                </td>
              );
            })}
          </tr>
        ))}
      </tbody>
    </table>
  );
}
```

```tsx
// apps/web/app/components/SolveProgress.tsx
import { LineChart, Line, XAxis, YAxis, Tooltip } from "recharts";

type Props = { incumbents: { objective: number; bound: number | null; elapsedSeconds: number }[] };

export function SolveProgress({ incumbents }: Props) {
  const data = incumbents.map((i) => ({
    t: i.elapsedSeconds,
    obj: i.objective,
    bound: i.bound ?? i.objective,
  }));
  return (
    <LineChart data={data} width={640} height={240}>
      <XAxis dataKey="t" name="sec" />
      <YAxis />
      <Tooltip />
      <Line type="stepAfter" dataKey="obj" stroke="currentColor" dot={false} />
      <Line type="stepAfter" dataKey="bound" stroke="currentColor" strokeDasharray="4 2" dot={false} />
    </LineChart>
  );
}
```

### Manual edit flow

`schedules.$id.edit.tsx` mounts `ScheduleGrid` with `onEditCell` wired to a local state. A "Validate" button POSTs to `/schedules/:id/validate`, which returns a list of violations; those feed back into `ScheduleGrid`'s `violations` prop → the violating cells glow red.

```tsx
// apps/web/app/routes/schedules.$id.edit.tsx (sketch)
const [draft, setDraft] = useState(schedule);
const [violations, setViolations] = useState<Violation[]>([]);

async function onValidate() {
  const resp = await fetch(`${baseUrl}/schedules/${id}/validate`, {
    method: "POST",
    body: JSON.stringify(draft),
  });
  setViolations((await resp.json()).violations);
}
```

## 6. MiniZinc implementation

Not applicable.

## 7. Comparison & takeaways

Compared to a Next.js build:

| Axis | Vite + RR7 framework mode | Next.js 15 (App Router) |
|---|---|---|
| Dev speed | Vite (fast HMR) | Turbopack (fast, still catching up on edge cases) |
| Routing | File-system (`app/routes/*.tsx`) | File-system (`app/**/page.tsx`) |
| Server functions | Loaders/actions are explicit | Server Components + Server Actions |
| Vendor lock-in | None | Significant (Vercel-leaning) |
| Streaming | Via SSE in client; RR7 server streaming available | Built-in RSC streaming |
| LOC for this app | ~1500 | ~1800 (more boilerplate for RSC/Client split) |

Compared to a classic React SPA + React Query:

| Axis | RR7 framework mode | SPA + RQ |
|---|---|---|
| Initial payload | SSR (smaller) | CSR (bigger, slower first paint) |
| Per-route data loading | Loaders co-located | Manual `useQuery` in each component |
| Type safety of routes | Strong | Weaker (route params typed via hook) |
| SEO | Native | Needs Vite SSR plugin |

**Key insight:** For an app with 5–10 routes and async-heavy data, RR7's loader/action model **removes the top 3 bugs** you'd hit in a hand-rolled SPA: double-fetch, race conditions, back-navigation stale data. Worth the learning curve.

## 8. Exercises

**Exercise 16-A: Dark/light theme toggle.** Wire a theme selector in the header. Persist in `localStorage`. Use shadcn's `theme-provider` or equivalent; respect `prefers-color-scheme` on first visit.

<details><summary>Hint</summary>
```tsx
const [theme, setTheme] = useState<"light" | "dark">(() =>
  (localStorage.getItem("theme") as "light" | "dark") ??
  (matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light"));
useEffect(() => {
  document.documentElement.classList.toggle("dark", theme === "dark");
  localStorage.setItem("theme", theme);
}, [theme]);
```
</details>

**Exercise 16-B: Undo manual edits.** Maintain a history stack in `schedules.$id.edit.tsx`; a toolbar with Undo/Redo (cmd-Z). Limit to 50 steps.

<details><summary>Hint</summary>
Two stacks: `past`, `future`. Every edit pushes current to `past`, clears `future`. Undo pops `past` → `future`. Use `useReducer` with actions `{type: 'edit', …} | {type: 'undo'} | {type: 'redo'}`.
</details>

**Exercise 16-C: Schedule diff viewer.** Given two schedules (typically "solved" vs "manually edited"), show a grid where changed cells are highlighted. Tooltip: "was X, now Y."

<details><summary>Hint</summary>
Render `ScheduleGrid` with a `diffAgainst` prop. Compute `Map<cellKey, {before, after}>`; style cells where `before !== after`. Use color-coded "was"/"now" badges on hover.
</details>

**Exercise 16-D: Backend comparison view.** Add a route `/runs/new/compare?instanceId=...` that kicks off both backends in parallel, streams both, and shows side-by-side KPI bars + stacked `SolveProgress`. Let the user pick which schedule to keep.

<details><summary>Hint</summary>
Two `EventSource`s. Two state slices. One render. Useful for Chapter 18's retrospective.
</details>

**Exercise 16-E: Accessibility pass.** Run axe-core (`@axe-core/react`) in dev. Fix every violation surfaced. Add keyboard navigation for the grid (arrow keys to move, enter to open edit popover).

<details><summary>Hint</summary>
`onKeyDown` on the `<table>`; maintain `focusedCell` state; compute next cell based on arrow key; `tabIndex={-1}` on cells + focus management via `ref`. Also ensure every icon button has `aria-label`.
</details>

## 9. Self-check

<details><summary>Q1: How do you show why a cell is highlighted red?</summary>
Attach a `title` (tooltip) and/or a click-activated popover to the cell. The popover text comes from the validator's response (`/schedules/:id/validate` returns an array of `{nurseId, day, rule, message}`). Map rule codes to user-friendly messages in a shared dictionary.
</details>

<details><summary>Q2: How does streaming improve UX vs a spinner?</summary>
Users see progress (obj going down, bound coming up). They can cancel earlier when they see diminishing returns. Perceived responsiveness is massively higher — watching a counter fall feels like work is happening; a spinner feels like a hang.
</details>

<details><summary>Q3: Why loaders instead of `useQuery` in the component body?</summary>
Loaders run before the component renders, on the server if you're SSR-ing. That means no loading-state flash, no race with navigation, and prefetching on link-hover is free. `useQuery` still earns its place for post-mount refetches and mutations.
</details>

<details><summary>Q4: What's the top gotcha with EventSource in React?</summary>
Cleanup: if you don't `es.close()` in the `useEffect` return, each navigation leaks an open connection. In StrictMode dev, effects run twice — verify `es.close()` is idempotent. Also ensure the `useEffect` deps array includes `runId` and `backend`.
</details>

<details><summary>Q5: Why does the backend toggle live in a Context rather than the URL?</summary>
It's cross-cutting: every API call reads it, and the user should toggle globally without page-reload. URL is a decent alternative (`?backend=kt`) — do that too if you want the state to survive hard reloads, but Context is the primary.
</details>

## 10. What this unlocks

With a working UI against both backends, you're ready to **containerize and deploy** in **Chapter 17**.

## 11. Further reading

- [React Router v7 framework-mode docs](https://reactrouter.com/start/framework/installation) — file-system routing, loaders, actions.
- [React 19 blog post](https://react.dev/blog/2024/12/05/react-19) — `use()`, Server Components, Actions.
- [TanStack Query v5 overview](https://tanstack.com/query/v5/docs/framework/react/overview).
- [Vite 6 release](https://vite.dev/blog/announcing-vite6) — environment API, modern defaults.
- [Tailwind 4 blog](https://tailwindcss.com/blog/tailwindcss-v4) — zero-config, faster builds.
- [shadcn/ui components](https://ui.shadcn.com/docs/components/) — especially `button`, `dialog`, `select`, `tooltip`.
- [Recharts](https://recharts.org/en-US/examples) — line charts for solve progress.
- [MDN EventSource](https://developer.mozilla.org/en-US/docs/Web/API/EventSource).
- [axe-core for React](https://github.com/dequelabs/axe-core-npm/tree/develop/packages/react) — accessibility linting.
