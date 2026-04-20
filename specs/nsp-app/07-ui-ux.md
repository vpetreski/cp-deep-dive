# 07 — UI / UX

> **Status:** placeholder — fill in Chapter 14
> **Last updated:** 2026-04-19

## Purpose of this section

The front-end design: information architecture, the primary screens, the flows
between them, and the design-system tokens (colour/typography/spacing).
Detail-enough that Chapter 16 can implement without guessing.

## Outline of content to fill

- [ ] Information architecture (sitemap / route tree)
- [ ] Wireframes per screen (ASCII blocks or linked sketches)
  - [ ] Home / instances list
  - [ ] Instance detail (view raw + built-in constraints toggles)
  - [ ] Solve-in-progress (stream, KPIs updating live, cancel button)
  - [ ] Schedule viewer (roster grid, coverage heatmap, fairness KPIs)
  - [ ] Manual-edit mode (cell edit, violation highlights, re-validate)
  - [ ] Compare two schedules (diff view)
- [ ] Key flows (step-by-step click-paths for the top 3 user stories)
- [ ] Design tokens
  - [ ] Colour palette (semantic: primary, success, warning, danger, neutral)
  - [ ] Typography scale (body, heading, monospace)
  - [ ] Spacing scale
  - [ ] Shadow / radius
- [ ] Component inventory (which shadcn/ui primitives; any custom components
      beyond the roster grid)
- [ ] Accessibility notes (tab order, screen-reader labels for grid cells,
      colour-blind-safe coverage heatmap)

## Relevant prior art

- `docs/plan.md` Chapter 16 for the frontend stack already agreed:
  Vite 6 + React 19 + React Router v7 framework mode + Tailwind 4 +
  shadcn/ui + TanStack Query 5.
- shadcn/ui component inventory (<https://ui.shadcn.com>) — which primitives
  are available.

<!--
TO FILL IN CHAPTER 14:
- Wireframes don't need pixel perfection; ASCII blocks are fine. Focus on
  layout + information hierarchy. Example:
    +----------------------+
    |  Instances        +  |
    +----------------------+
    | toy-01      5nu 14d  |
    | toy-02      5nu 21d  |
    | inrc2-m1    30nu 28d |
    +----------------------+
- The roster-grid is the hardest custom component — budget for it in Chapter 16.
- Colour must encode meaning, not decoration. Red = constraint violation,
  yellow = soft-violation, green = preference satisfied.
- Keyboard-only flow MUST exist for manual edit mode (a11y + power-user).
-->
