"""Chapter 06 — Global constraints tour.

Runs one small demo per global constraint covered in the chapter:
    - ``add_circuit`` on an 8-city TSP
    - ``add_allowed_assignments`` (Table) on a nurse/ward policy
    - ``add_element`` on a cost-array lookup
    - ``add_automaton`` on "no more than 3 consecutive nights"
    - ``add_inverse`` on a 5-nurse/5-task bijection
    - manual lex-leq symmetry breaking (count reduction)
    - ``add_reservoir_constraint`` on a tiny flow-over-time example
"""

from __future__ import annotations

from dataclasses import dataclass

from py_cp_sat_ch06.automaton import AutomatonResult, label_name, solve_night_pattern
from py_cp_sat_ch06.element import ElementResult, solve_min_cost_pick
from py_cp_sat_ch06.inverse import InverseResult, solve_simple_bijection
from py_cp_sat_ch06.lex_leq import count_schedules
from py_cp_sat_ch06.reservoir import ReservoirResult, solve_reservoir_schedule
from py_cp_sat_ch06.table_skills import TableResult, solve_best_skill
from py_cp_sat_ch06.tsp import TspResult, solve_tsp


@dataclass(frozen=True)
class ChapterDemo:
    """Every demo result bundled together for the smoke test."""

    tsp: TspResult
    table: TableResult
    element: ElementResult
    automaton: AutomatonResult
    inverse: InverseResult
    lex_without: int
    lex_with: int
    reservoir: ReservoirResult


def solve() -> ChapterDemo:
    """Run every demo once with deterministic defaults."""
    return ChapterDemo(
        tsp=solve_tsp(),
        table=solve_best_skill(),
        element=solve_min_cost_pick(),
        automaton=solve_night_pattern(),
        inverse=solve_simple_bijection(n=5, pinned={0: 2}),
        lex_without=count_schedules(n_nurses=3, days=5, min_work=2, use_lex=False),
        lex_with=count_schedules(n_nurses=3, days=5, min_work=2, use_lex=True),
        reservoir=solve_reservoir_schedule(),
    )


def main() -> None:
    demo = solve()

    print("=== Circuit: TSP (8 cities) ===")
    print(f"Status: {demo.tsp.status}  length: {demo.tsp.length}")
    print(f"Tour:   {demo.tsp.tour}")
    print()

    print("=== Table: best-skill nurse/ward lookup ===")
    print(
        f"Status: {demo.table.status}  nurse={demo.table.nurse} "
        f"ward={demo.table.ward} skill={demo.table.skill}"
    )
    print()

    print("=== Element: minimum-cost pick ===")
    print(
        f"Status: {demo.element.status}  index={demo.element.index} cost={demo.element.cost}"
    )
    print()

    print("=== Automaton: 14-day schedule, ≤ 3 consecutive nights ===")
    print(f"Status:   {demo.automaton.status}")
    print(f"Schedule: {' '.join(label_name(v) for v in demo.automaton.schedule)}")
    print()

    print("=== Inverse: tasks ↔ nurses bijection (task 0 pinned to nurse 2) ===")
    print(f"Status:         {demo.inverse.status}")
    print(f"nurse_of_task:  {demo.inverse.nurse_of_task}")
    print(f"task_of_nurse:  {demo.inverse.task_of_nurse}")
    print()

    print("=== LexLeq: symmetry breaking on 3 nurses × 5 days ===")
    print(f"Solutions without lex: {demo.lex_without}")
    print(f"Solutions with lex:    {demo.lex_with}")
    ratio = demo.lex_without / demo.lex_with if demo.lex_with else float("inf")
    print(f"Ratio:                 {ratio:.2f}x (expected ≈ 3! = 6)")
    print()

    print("=== Reservoir: deltas (5, -3, 4, -6) in [0, 10] over horizon=20 ===")
    print(f"Status: {demo.reservoir.status}")
    print(f"Times:  {demo.reservoir.event_times}")


if __name__ == "__main__":
    main()
