import type { Shift } from "./types";

// Pre-defined palettes — keyed by slot index so we cycle through even when
// shift ids don't match the M/D/N toy pattern. Colours echo the spec tokens
// in specs/nsp-app/07-ui-ux.md §Design tokens (both light + dark).

const PALETTE: string[] = [
  // Morning
  "bg-amber-100 text-amber-900 border-amber-300 dark:bg-amber-900/40 dark:text-amber-100 dark:border-amber-700",
  // Day
  "bg-sky-100 text-sky-900 border-sky-300 dark:bg-sky-900/40 dark:text-sky-100 dark:border-sky-700",
  // Night
  "bg-indigo-100 text-indigo-900 border-indigo-300 dark:bg-indigo-900/50 dark:text-indigo-100 dark:border-indigo-700",
  // Extras
  "bg-emerald-100 text-emerald-900 border-emerald-300 dark:bg-emerald-900/40 dark:text-emerald-100 dark:border-emerald-700",
  "bg-pink-100 text-pink-900 border-pink-300 dark:bg-pink-900/40 dark:text-pink-100 dark:border-pink-700",
  "bg-fuchsia-100 text-fuchsia-900 border-fuchsia-300 dark:bg-fuchsia-900/40 dark:text-fuchsia-100 dark:border-fuchsia-700",
];

export const SHIFT_STYLES = {
  off:
    "bg-muted/40 text-muted-foreground border-border dark:bg-muted/30",
};

const BAR_PALETTE: string[] = [
  "bg-amber-400 dark:bg-amber-500",
  "bg-sky-400 dark:bg-sky-500",
  "bg-indigo-400 dark:bg-indigo-500",
  "bg-emerald-400 dark:bg-emerald-500",
  "bg-pink-400 dark:bg-pink-500",
  "bg-fuchsia-400 dark:bg-fuchsia-500",
];

function indexOfShift(shiftId: string, shifts: Shift[]): number {
  const idx = shifts.findIndex((s) => s.id === shiftId);
  return idx < 0 ? 0 : idx;
}

export function shiftClassName(
  shiftId: string | null | undefined,
  shifts: Shift[],
): string {
  if (!shiftId) return SHIFT_STYLES.off;
  const idx = indexOfShift(shiftId, shifts);
  return PALETTE[idx % PALETTE.length];
}

export function shiftBarClassName(
  shiftId: string,
  shifts: Shift[],
): string {
  const idx = indexOfShift(shiftId, shifts);
  return BAR_PALETTE[idx % BAR_PALETTE.length];
}
