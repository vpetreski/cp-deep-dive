import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";

import { GanttChart } from "~/components/gantt-chart";
import { exampleToy02 } from "~/lib/examples";
import type { Schedule } from "~/lib/types";

const schedule: Schedule = {
  instanceId: "toy-02",
  assignments: [
    { nurseId: "N1", day: 0, shiftId: "M" },
    { nurseId: "N2", day: 0, shiftId: "D" },
    { nurseId: "N3", day: 0, shiftId: "N" },
    { nurseId: "N1", day: 3, shiftId: "D" },
  ],
};

describe("GanttChart", () => {
  it("renders one row per nurse and a bar per assignment", () => {
    render(<GanttChart instance={exampleToy02} schedule={schedule} />);
    // Nurse rows — one per nurse in the instance
    for (const n of exampleToy02.nurses) {
      expect(screen.getByText(n.name ?? n.id)).toBeInTheDocument();
    }
    // Bars — aria-labelled with nurse + shift
    expect(
      screen.getByRole("button", { name: /Alice, day 0, Morning shift/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /Carmen, day 0, Night shift/i }),
    ).toBeInTheDocument();
    // A day-3 D shift for Alice
    expect(
      screen.getByRole("button", { name: /Alice, day 3, Day shift/i }),
    ).toBeInTheDocument();
  });

  it("renders day headers across the horizon", () => {
    render(<GanttChart instance={exampleToy02} schedule={schedule} />);
    expect(screen.getByText("Day 0")).toBeInTheDocument();
    expect(screen.getByText("Day 13")).toBeInTheDocument();
  });
});
