# apps/web — cp-deep-dive NSP frontend

Vite + React 19 + React Router v7 (framework mode) + TypeScript + Tailwind 4 +
shadcn/ui + TanStack Query. This is the UI for the NSP application built in
Phase 7 of [`../../docs/plan.md`](../../docs/plan.md).

> **Scope today:** scaffolding only. The real app (instance upload, solve
> streaming, roster grid) lands after `specs/nsp-app/` is locked in Chapter 14.
> The home page is a hero card, and `/health-check` pings whichever backend is
> active via the toggle.

## Quickstart

```bash
npm install
cp .env.example .env.local   # adjust VITE_*_API_URL as needed
npm run dev                  # http://localhost:5173
```

## Scripts

| Command             | What it does                                                        |
| ------------------- | ------------------------------------------------------------------- |
| `npm run dev`       | Start the RR7 dev server on port 5173                               |
| `npm run build`     | Production build (`build/client` + `build/server`)                  |
| `npm run start`     | Serve the production build via `@react-router/serve`                |
| `npm run typecheck` | `react-router typegen && tsc` — must pass in CI                     |
| `npm run lint`      | ESLint 9 flat config (TypeScript + React + hooks)                   |
| `npm test`          | Vitest unit tests (jsdom)                                           |
| `npm run test:e2e`  | Placeholder — wired up if Playwright is installed later             |

## Environment variables

Read at **build time** by Vite (`import.meta.env.VITE_*`).

| Var                 | Default                  | Used in                          |
| ------------------- | ------------------------ | -------------------------------- |
| `VITE_PY_API_URL`   | `http://localhost:8000`  | Python (FastAPI) backend base URL |
| `VITE_KT_API_URL`   | `http://localhost:8080`  | Kotlin (Ktor 3) backend base URL  |

See [`.env.example`](./.env.example).

## Project layout

```
app/
├── app.css                  # Tailwind 4 + shadcn theme tokens
├── root.tsx                 # HTML shell + QueryClient + BackendProvider
├── routes.ts                # RR7 route config
├── routes/
│   ├── _index.tsx           # home (hero + backend toggle)
│   └── health-check.tsx     # pings <base>/health via TanStack Query
├── components/
│   ├── backend-switch.tsx   # python | kotlin toggle
│   └── ui/                  # shadcn/ui components (button, card, dialog, input)
└── lib/
    ├── backend.tsx          # BackendContext + provider
    ├── query-client.ts      # QueryClient factory
    └── utils.ts             # shadcn `cn()` helper
tests/
├── setup.ts                 # @testing-library/jest-dom registration
└── backend-switch.test.tsx  # toggle smoke test
```

## Backend toggle

`BackendProvider` exposes `backend`, `setBackend`, and `baseUrl` via the
`useBackend()` hook. Selection is persisted to `localStorage` so the choice
survives page reloads. Later phases will use the same context for all
data-fetching queries (one place to redirect every call to Python or Kotlin).

## Shared API contract

The backend endpoints hit here (`/health`, `/version`, `/solve`,
`/solution/{jobId}`, `/solutions/{jobId}/stream`) are defined in
[`apps/shared/openapi.yaml`](../shared/openapi.yaml). Both backends implement
the same contract, and this frontend talks to either one interchangeably.

## Docker

```bash
docker build -t cp-deep-dive-web .
docker run --rm -p 3000:3000 \
  -e VITE_PY_API_URL=http://host.docker.internal:8000 \
  -e VITE_KT_API_URL=http://host.docker.internal:8080 \
  cp-deep-dive-web
```

Note: because Vite inlines `VITE_*` at build time, pass them as `--build-arg`
for production images. The runtime `-e` flags shown above only matter if you
rebuild inside the container.
