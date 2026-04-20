import type { NspInstance, Shift } from "./types";

export function formatHoursMinutes(minutes: number | undefined): string {
  if (typeof minutes !== "number" || !Number.isFinite(minutes)) return "—";
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return `${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}`;
}

export function shiftTimeRange(shift: Shift): string {
  const { startMinutes, durationMinutes } = shift;
  if (typeof startMinutes !== "number" || typeof durationMinutes !== "number") {
    return "";
  }
  const endMinutes = (startMinutes + durationMinutes) % (24 * 60);
  return `${formatHoursMinutes(startMinutes)}–${formatHoursMinutes(endMinutes)}`;
}

export function shiftDurationHours(shift: Shift): number {
  if (!shift.durationMinutes) return 0;
  return shift.durationMinutes / 60;
}

export function coverageSlotCount(instance: NspInstance): number {
  return instance.coverage.length;
}

export function formatNumber(n: number | undefined, digits = 0): string {
  if (typeof n !== "number" || !Number.isFinite(n)) return "—";
  return n.toLocaleString(undefined, {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  });
}

export function formatPercent(n: number | undefined, digits = 1): string {
  if (typeof n !== "number" || !Number.isFinite(n)) return "—";
  return `${(n * 100).toFixed(digits)}%`;
}

export function formatSeconds(n: number | undefined): string {
  if (typeof n !== "number" || !Number.isFinite(n)) return "—";
  return `${n.toFixed(1)} s`;
}

export function relativeTime(iso: string | undefined): string {
  if (!iso) return "—";
  const then = new Date(iso).getTime();
  if (Number.isNaN(then)) return iso;
  const now = Date.now();
  const diffSec = Math.round((now - then) / 1000);
  if (diffSec < 60) return "just now";
  if (diffSec < 3600) return `${Math.round(diffSec / 60)} min ago`;
  if (diffSec < 86_400) return `${Math.round(diffSec / 3600)} h ago`;
  return `${Math.round(diffSec / 86_400)} d ago`;
}

export function downloadBlob(filename: string, data: string, mime = "text/csv"): void {
  const blob = new Blob([data], { type: mime });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 0);
}
