import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";

import { SummaryStats } from "~/components/summary-stats";
import { exampleToy01 } from "~/lib/examples";
import type { Schedule } from "~/lib/types";

describe("SummaryStats", () => {
  it("computes coverage percent from required vs filled", () => {
    // toy-01 needs 1 D + 1 N every day for 7 days = 14 slots
    // Fill 7 D shifts → 50%
    const assignments = Array.from({ length: 7 }, (_, d) => ({
      nurseId: "N1",
      day: d,
      shiftId: "D",
    }));
    const schedule: Schedule = { instanceId: "toy-01", assignments };
    render(<SummaryStats instance={exampleToy01} schedule={schedule} />);
    expect(screen.getByText(/50\.0%/)).toBeInTheDocument();
    expect(screen.getByText("7 / 14 slots")).toBeInTheDocument();
  });

  it("reports 100% when nothing is required", () => {
    const instance = { ...exampleToy01, coverage: [] };
    const schedule: Schedule = { instanceId: "toy-01", assignments: [] };
    render(<SummaryStats instance={instance} schedule={schedule} />);
    // SummaryStats prints "100.0%" (decimal) and "0 / 0 slots"
    expect(screen.getByText(/100\.0%/)).toBeInTheDocument();
  });
});
