# 07 — UI / UX

> **Status:** LOCKED v1.0
> **Last updated:** 2026-04-19

## Purpose

The information architecture, page-by-page behaviour, design tokens, and accessibility contract for the frontend. Detail is sufficient for implementation without further design review; any material deviation requires a spec amendment.

## Stack

- **Vite 6** — build tooling
- **React 19** — UI framework
- **React Router v7** (framework mode) — routing + data loading
- **TanStack Query 5** — server-state caching and SSE integration
- **Tailwind 4** — styling tokens
- **shadcn/ui** — base component primitives (buttons, dialogs, tables, form controls)
- **Recharts or VisX** (decision deferred to Chapter 16) — Gantt rendering

## Information architecture

```
/                              -> Home (upload instance OR pick example)
/instances                     -> Instances list
/instances/:instanceId         -> Instance detail (stats, solve button)
/instances/:instanceId/solve   -> Solve configuration dialog + submission
/solves/:jobId                 -> Solve progress (live stream)
/solves/:jobId/schedule        -> Schedule viewer (roster / Gantt toggle)
/solves/:jobId/infeasibility   -> Infeasibility report (when status = infeasible)
/about                         -> About, version, backend selector, links
```

The application shell (header + footer) renders on every route. The header contains the app title, backend selector, and a link to `/about`. The footer contains a link to the OpenAPI document and the app version.

## Primary navigation

```
+------------------------------------------------------------------------------+
|  NSP Scheduler                       Backend: [ Python  v ]     Version: 1.0 |
+------------------------------------------------------------------------------+
|                                                                              |
|  [ page content here ]                                                       |
|                                                                              |
+------------------------------------------------------------------------------+
|  OpenAPI  |  Docs  |  GitHub                                 cp-deep-dive    |
+------------------------------------------------------------------------------+
```

---

## Page-by-page walkthrough

### Home

Goal: get the scheduler from "landed on the URL" to "solving an instance" in under 30 seconds.

```
+------------------------------------------------------------------------------+
|  Nurse Scheduling App                                                        |
|  Upload a ward instance or pick an example to get started.                   |
|                                                                              |
|   +--------------------------------------------------------------------+     |
|   |                                                                    |     |
|   |             Drop a JSON instance here, or click to browse          |     |
|   |                                                                    |     |
|   |                   [ Browse files... ]                              |     |
|   |                                                                    |     |
|   +--------------------------------------------------------------------+     |
|                                                                              |
|   Examples                                                                   |
|   +----------------------------+  +----------------------------+             |
|   | toy-01                     |  | toy-02                     |             |
|   | 3 nurses x 7 days x 2      |  | 5 nurses x 14 days x 3     |             |
|   | shifts. Tiny reference.    |  | shifts. Realistic ward.    |             |
|   | [ Open ]                   |  | [ Open ]                   |             |
|   +----------------------------+  +----------------------------+             |
|                                                                              |
|   Warning: Do not upload real nurse names. Use pseudonyms for                |
|   public deployments.                                                        |
+------------------------------------------------------------------------------+
```

Behaviour:
- Drag-and-drop zone accepts `.json` files only; other types show an inline error.
- Browse button opens the file picker filtered to `.json`.
- Invalid files trigger a schema-validation error surfaced as a red toast plus an inline field indicator where possible.
- Example cards call `POST /instances` with the bundled JSON body, then navigate to `/instances/{id}`.

States:
- **Empty** (default): copy above.
- **Loading** (upload in progress): spinner over the drop zone; keyboard focus stays on the browse button.
- **Error** (schema failure): red toast with the `Error.message`; drop zone still focusable.

### Instances list

Goal: let the learner find a previously uploaded instance without re-uploading.

```
+------------------------------------------------------------------------------+
|  Instances                                            [ + Upload new ]       |
|                                                                              |
|  +------------+-----------+---------+---------+---------+--------------+     |
|  | Id         | Name      | Source  | Horizon | Nurses  | Uploaded     |     |
|  +------------+-----------+---------+---------+---------+--------------+     |
|  | toy-01     | Toy 1     | toy     | 7       | 3       | 2 days ago   |     |
|  | toy-02     | Toy 2     | toy     | 14      | 5       | 2 days ago   |     |
|  | ins-abcdef | Ward A    | custom  | 28      | 30      | 12 min ago   |     |
|  | ins-...    | ...       | ...     | ...     | ...     | ...          |     |
|  +------------+-----------+---------+---------+---------+--------------+     |
|                                                                              |
|  [ < Prev ]  Showing 1-20 of 87  [ Next > ]                                  |
+------------------------------------------------------------------------------+
```

Behaviour:
- Default page size 20; `Next` loads using `nextCursor` from the backend.
- Clicking a row navigates to `/instances/{id}`.
- Upload button navigates to `/`.

### Instance detail

Goal: confirm the instance is what the user uploaded, then kick off a solve.

```
+------------------------------------------------------------------------------+
|  Instance: toy-02                                                            |
|  Source: toy     Horizon: 14 days     Nurses: 5     Shifts: 3                |
|  Coverage slots: 42                                                          |
|                                                                              |
|  Summary                                                                     |
|  +----------------------------------------------------------------------+    |
|  | Shifts                                                               |    |
|  | M  Morning  07:00-15:00                                              |    |
|  | D  Day      15:00-23:00                                              |    |
|  | N  Night    23:00-07:00                                              |    |
|  |                                                                      |    |
|  | Nurses                                                               |    |
|  | N1 Alice    general                   36 h/week                      |    |
|  | N2 Bob      general, icu              36 h/week                      |    |
|  | N3 Carmen   general, pedi             36 h/week                      |    |
|  | N4 Diego    general                   30 h/week                      |    |
|  | N5 Elif     general, icu, senior      40 h/week                      |    |
|  +----------------------------------------------------------------------+    |
|                                                                              |
|  [ v  Show raw JSON  ]                                                       |
|                                                                              |
|                                                          [ Delete ]  [ Solve ] |
+------------------------------------------------------------------------------+
```

Behaviour:
- "Show raw JSON" expands a scrollable, monospaced code block.
- "Solve" opens the solve-configuration dialog (modal on desktop, full-page on mobile).
- "Delete" shows a confirmation dialog before calling `DELETE /instances/{id}` and navigating to `/instances`.

### Solve configuration

Goal: collect time limit, worker count, and objective weights in one dialog.

```
+------------------------------------------------------------------------------+
|  Solve: toy-02                                                               |
|                                                                              |
|  Backend:              ( ) Python    ( ) Kotlin                              |
|                                                                              |
|  Time limit (seconds): [  60  ]        Range 1-3600                          |
|  Search workers:       [   4  ]        0 = auto                              |
|  Random seed:          [  n/a ]        Optional                              |
|                                                                              |
|  Objective weights                                                           |
|   SC-1 Preferences           [ 10 ]                                          |
|   SC-2 Fairness              [  5 ]                                          |
|   SC-3 Workload balance      [  2 ]                                          |
|   SC-4 Weekend distribution  [  3 ]                                          |
|   SC-5 Consecutive days-off  [  1 ]                                          |
|                                                                              |
|                                                    [ Cancel ]  [ Start solve ] |
+------------------------------------------------------------------------------+
```

Behaviour:
- The backend selector defaults to the header's current backend.
- "Start solve" issues `POST /solve` and navigates to `/solves/{jobId}`.

### Solve progress

Goal: reassure the user that something is happening and let them stop early if satisfied.

```
+------------------------------------------------------------------------------+
|  Solve: job-ad83-...                                       [ Cancel solve ]  |
|                                                                              |
|  Status:   running                                                           |
|  Elapsed:  12.4 s / 60 s                                                     |
|  Objective:  138                                                             |
|  Best bound: 135                                                             |
|  Gap:        2.2%                                                            |
|                                                                              |
|  Objective over time                                                         |
|   200 |                                                                      |
|   175 |  *                                                                   |
|   150 |     *                                                                |
|   125 |           *    *    *    *                                           |
|   100 |________*____________________________________________                 |
|         0     3     6     9    12    15    18    21   (seconds)              |
|                                                                              |
|  [ ]  Auto-open schedule when optimal found                                  |
+------------------------------------------------------------------------------+
```

Behaviour:
- SSE connection opens on mount; disconnects on unmount.
- Each `solution` event updates the numeric panel and appends a point to the chart.
- On terminal status, the page auto-navigates to `/solves/{jobId}/schedule` (if checkbox set and status is `optimal` or `feasible`), or to `/solves/{jobId}/infeasibility` (if status is `infeasible`).
- "Cancel solve" calls `POST /solve/{jobId}/cancel` and stays on the page while the cancellation propagates.

### Schedule viewer — roster

Goal: show the full roster in a table that is skimmable at a glance.

```
+------------------------------------------------------------------------------+
|  Schedule: toy-02   status=optimal  objective=138  gap=0.0%  time=1.2s       |
|                                                                              |
|  View: (*) Roster   ( ) Gantt                              [ Download CSV ]  |
|                                                                              |
|   Nurse |  0  |  1  |  2  |  3  |  4  |  5  |  6  |  7  |  8  |  9  | ... | |
|  -------|-----|-----|-----|-----|-----|-----|-----|-----|-----|-----|-----| |
|   Alice |  M  |  M  | off |  M  |  M  | off | off |  M  |  M  | off | ... | |
|   Bob   |  D  |  D  |  D  | off |  D  |  D  | off |  D  |  D  |  D  | ... | |
|   Carmen|  N  | off |  M  |  D  | off |  D  |  N  | off |  M  |  N  | ... | |
|   Diego |  M  |  D  |  N  | off |  N  | off | off |  D  |  M  |  D  | ... | |
|   Elif  | off |  N  |  D  |  N  |  M  |  N  |  D  |  N  | off |  M  | ... | |
|                                                                              |
|  Legend:  [ M Morning ]  [ D Day ]  [ N Night ]  [ off ]                     |
|                                                                              |
|  Violations (soft)                                                           |
|   SC-1  Alice preferred Sunday off, was scheduled for M on day 6.  penalty 5 |
|   SC-4  Weekend spread: 3 vs 1 (max - min).                        penalty 4 |
+------------------------------------------------------------------------------+
```

Behaviour:
- Sticky headers (first column and first row).
- Cells are colour-coded by shift (see design tokens below).
- Keyboard navigation: arrow keys move between cells; Enter opens a tooltip with the shift's full label and time.

### Schedule viewer — Gantt

Goal: show shift overlaps and cross-day transitions.

```
+------------------------------------------------------------------------------+
|  Schedule: toy-02   [ Gantt ]                              [ Download CSV ]  |
|                                                                              |
|         00:00    06:00   12:00   18:00   24:00     (day 0)                   |
|   Alice    .        [===M===]      .       .                                 |
|   Bob      .         .      [===D===]      .                                 |
|   Carmen   .                            [===N====>                           |
|   Diego    .         [===M===]      .       .                                |
|   Elif   <====]     off                                                      |
|                                                                              |
|         00:00    06:00   12:00   18:00   24:00     (day 1)                   |
|   Alice    .        [===M===]      .       .                                 |
|   ...                                                                        |
+------------------------------------------------------------------------------+
```

Behaviour:
- One lane per nurse per day; vertical stacking of days.
- Night shifts crossing midnight render as a continuous bar across the day boundary.
- Hovering a bar reveals a tooltip: nurse, shift label, start, end.

### Infeasibility report

Goal: explain *why* no schedule was found.

```
+------------------------------------------------------------------------------+
|  No schedule found: toy-XX                                                   |
|                                                                              |
|  The solver could not satisfy all hard constraints on this instance.         |
|                                                                              |
|  Failing constraints                                                         |
|                                                                              |
|   HC-1 Coverage                                                              |
|   The demand on day 4 shift N requires 1 nurse, but all 3 nurses are         |
|   blocked by other hard constraints (2 are on fixed-off, 1 has already       |
|   reached max consecutive days).                                             |
|                                                                              |
|   HC-4 Max consecutive working days                                          |
|   The coverage on days 8-13 exceeds the pool's collective capacity given     |
|   the 5-day consecutive limit.                                               |
|                                                                              |
|  [ Back to instance ]                                                        |
+------------------------------------------------------------------------------+
```

Behaviour:
- Page renders when `SolveResponse.status = "infeasible"`.
- Identifies failing HC codes from `SolveResponse.schedule.violations` (where `severity = "hard"`).
- "Back to instance" navigates to `/instances/{instanceId}`.

### About

Goal: disclose version, links, health.

```
+------------------------------------------------------------------------------+
|  About                                                                       |
|                                                                              |
|  Nurse Scheduling App v1.0                                                   |
|  Backends:                                                                   |
|    Python    http://localhost:8000   [ healthy ]                             |
|    Kotlin    http://localhost:8080   [ healthy ]                             |
|                                                                              |
|  OR-Tools: 9.11.4210                                                         |
|                                                                              |
|  Links                                                                       |
|   - OpenAPI document                                                         |
|   - GitHub repository                                                        |
|   - cp-deep-dive curriculum                                                  |
+------------------------------------------------------------------------------+
```

---

## Design tokens

### Colours (light theme)

| Token | Hex | Usage |
|---|---|---|
| `--bg` | `#ffffff` | Page background |
| `--bg-subtle` | `#f5f5f7` | Card/panel backgrounds |
| `--fg` | `#0f172a` | Primary text |
| `--fg-muted` | `#475569` | Secondary text |
| `--border` | `#e2e8f0` | Dividers, cell borders |
| `--primary` | `#2563eb` | Primary actions, links |
| `--success` | `#16a34a` | Optimal / success state |
| `--warning` | `#d97706` | Feasible-not-optimal, gap |
| `--danger` | `#dc2626` | Infeasibility, errors |
| `--shift-morning` | `#fde68a` | M shift cell |
| `--shift-day` | `#bae6fd` | D shift cell |
| `--shift-night` | `#c7d2fe` | N shift cell |
| `--shift-off` | `#f1f5f9` | Off-day cell |

### Colours (dark theme)

| Token | Hex | Usage |
|---|---|---|
| `--bg` | `#0f172a` | Page background |
| `--bg-subtle` | `#1e293b` | Card/panel backgrounds |
| `--fg` | `#f8fafc` | Primary text |
| `--fg-muted` | `#94a3b8` | Secondary text |
| `--border` | `#334155` | Dividers, cell borders |
| `--primary` | `#60a5fa` | Primary actions, links |
| `--success` | `#4ade80` | Optimal / success state |
| `--warning` | `#fbbf24` | Feasible-not-optimal, gap |
| `--danger` | `#f87171` | Infeasibility, errors |
| `--shift-morning` | `#78350f` | M shift cell |
| `--shift-day` | `#075985` | D shift cell |
| `--shift-night` | `#3730a3` | N shift cell |
| `--shift-off` | `#1e293b` | Off-day cell |

All colour combinations meet the WCAG 2.1 AA 4.5:1 contrast ratio for body text and 3:1 for large text.

### Typography scale

| Token | Size | Line-height | Usage |
|---|---|---|---|
| `--text-xs` | 12 px | 16 px | Table cells, metadata |
| `--text-sm` | 14 px | 20 px | Secondary body |
| `--text-base` | 16 px | 24 px | Primary body |
| `--text-lg` | 18 px | 28 px | Subsection headings |
| `--text-xl` | 20 px | 28 px | Card titles |
| `--text-2xl` | 24 px | 32 px | Page headings |
| `--text-3xl` | 30 px | 36 px | Hero copy |

Font stack:
- **Sans:** `Inter`, `system-ui`, `-apple-system`, `sans-serif`.
- **Mono:** `JetBrains Mono`, `ui-monospace`, `SFMono-Regular`, `monospace`.

### Spacing scale

Base unit: 4 px. Tokens: `--space-1` (4 px), `--space-2` (8 px), `--space-3` (12 px), `--space-4` (16 px), `--space-6` (24 px), `--space-8` (32 px), `--space-12` (48 px), `--space-16` (64 px).

### Radius and elevation

| Token | Value |
|---|---|
| `--radius-sm` | 4 px |
| `--radius-md` | 8 px |
| `--radius-lg` | 12 px |
| `--shadow-sm` | `0 1px 2px rgba(0,0,0,0.05)` |
| `--shadow-md` | `0 4px 6px rgba(0,0,0,0.08)` |
| `--shadow-lg` | `0 10px 15px rgba(0,0,0,0.1)` |

---

## Responsive breakpoints

| Breakpoint | Width | Layout |
|---|---|---|
| Mobile | `< 640 px` | Single column, full-width cards, hamburger navigation, dialogs become full-page. |
| Tablet | `640-1024 px` | Two-column grids where space allows; sticky headers in the roster table. |
| Desktop | `>= 1024 px` | Primary layout above; full header and footer; dialogs remain modal. |

The roster table scrolls horizontally on viewports narrower than `min(horizonDays * 60 px + 120 px, 100vw)`.

---

## Keyboard shortcuts

| Key | Context | Action |
|---|---|---|
| `/` | Global | Focus the instance-search input (instances list). |
| `g` then `i` | Global | Navigate to `/instances`. |
| `g` then `h` | Global | Navigate to `/`. |
| `Enter` | Upload zone | Open file browser. |
| `Esc` | Dialog | Close dialog. |
| Arrow keys | Roster table | Move focus between cells. |
| `Enter` | Roster cell | Open cell tooltip (shift details). |
| `Ctrl+K` or `Cmd+K` | Global | Open command palette (v1.1 — not in v1.0). |

---

## Empty, loading, and error states

Every page defines three explicit states:

### Home
- Empty (default): upload zone + example cards.
- Loading: spinner over upload zone during `POST /instances`.
- Error: red toast with validation message.

### Instances list
- Empty (zero results): "No instances yet. Upload one or pick an example." with a link home.
- Loading: skeleton rows (three) pulsing.
- Error: inline error with a retry button.

### Instance detail
- Empty (not found): "Instance not found." with a back link.
- Loading: skeleton sections.
- Error: inline error with a retry button.

### Solve progress
- Initial (pre-first-event): spinner with "Waiting for the solver to start...".
- Active (running): live numbers and chart.
- Terminal: auto-navigate to schedule or infeasibility view.
- Error: red banner, "Solve failed: {message}", with a retry button.

### Schedule viewer
- Loading: skeleton table.
- Error: inline error with a retry button.

---

## Accessibility contract

- Minimum 4.5:1 contrast for all text.
- Visible focus indicator on every interactive element.
- All interactive elements reachable via Tab.
- Roster cells announce `aria-label`: "Alice, day 0, Morning shift".
- Gantt bars announce `aria-label`: "Alice, day 0, Morning shift 07:00 to 15:00".
- Dialogs trap focus and restore focus to the trigger on close.
- Live regions (`role="status"`) announce solve progress updates with `aria-live="polite"`.
- Reduced motion honoured: SSE-driven updates fade rather than slide.

---

## Reference links

- React Router v7 — <https://reactrouter.com/>
- TanStack Query 5 — <https://tanstack.com/query/latest>
- Tailwind CSS 4 — <https://tailwindcss.com/>
- shadcn/ui — <https://ui.shadcn.com/>
- WCAG 2.1 — <https://www.w3.org/TR/WCAG21/>
- axe-core — <https://github.com/dequelabs/axe-core>
