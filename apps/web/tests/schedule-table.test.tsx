import { render, screen, fireEvent } from "@testing-library/react";
import { describe, it, expect } from "vitest";

import { ScheduleTable } from "~/components/schedule-table";
import { exampleToy01 } from "~/lib/examples";
import type { Schedule } from "~/lib/types";

const schedule: Schedule = {
  instanceId: "toy-01",
  assignments: [
    { nurseId: "N1", day: 0, shiftId: "D" },
    { nurseId: "N1", day: 1, shiftId: null },
    { nurseId: "N2", day: 0, shiftId: "N" },
    { nurseId: "N3", day: 2, shiftId: "D" },
  ],
};

describe("ScheduleTable", () => {
  it("renders a cell per nurse × day and labels the chosen shift", () => {
    render(<ScheduleTable instance={exampleToy01} schedule={schedule} />);
    // Day shift cell for N1
    const n1day0 = screen.getByRole("gridcell", {
      name: /Alice, day 0, Day shift/i,
    });
    expect(n1day0).toBeInTheDocument();
    // Off cell for N1 day 1
    expect(
      screen.getByRole("gridcell", { name: /Alice, day 1, off/i }),
    ).toBeInTheDocument();
  });

  it("supports arrow-key navigation between cells", () => {
    render(<ScheduleTable instance={exampleToy01} schedule={schedule} />);
    const firstCell = screen
      .getAllByRole("gridcell")
      .find((el) => el.getAttribute("data-row") === "0" && el.getAttribute("data-col") === "0");
    expect(firstCell).toBeTruthy();
    (firstCell as HTMLElement).focus();
    fireEvent.keyDown(firstCell as HTMLElement, { key: "ArrowRight" });
    const next = document.querySelector<HTMLElement>(
      'td[data-row="0"][data-col="1"]',
    );
    expect(next).toBeTruthy();
    expect(document.activeElement).toBe(next);
  });
});
