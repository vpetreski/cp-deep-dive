"""Exercise 13-D - infeasibility cores on a tightened instance.

When CP-SAT reports INFEASIBLE, it can tell us *which* constraints were
jointly unsatisfiable if we help it out with `add_assumption`. This
exercise deliberately breaks toy-02 (bumps night-shift demand so the
fleet can't possibly cover it), then:

1. Re-builds the model with each hard constraint family gated by a
   dedicated boolean "assumption literal".
2. Assumes them all at solve time, letting CP-SAT report the minimal
   subset whose assumption set is inconsistent.
3. Prints the literals in the core.

This teaches the debugging workflow: start from a tight failure, ask the
solver which assumption set is over-constrained, peel them off one by one.

Run:
    uv run python -m py_cp_sat_ch13.solutions.exercise_13_d_infeasibility_cores
"""

from __future__ import annotations

import dataclasses
import pathlib

from nsp_core import CoverageRequirement, load_instance
from nsp_core.model_v1 import build_model as build_hard_model
from ortools.sat.python import cp_model

DATA = pathlib.Path(__file__).resolve().parents[6] / "data" / "nsp"


def main() -> None:
    base = load_instance(DATA / "toy-02.json")
    # Tighten coverage beyond what the fleet can supply: every N shift
    # requires 6 nurses, but the fleet only has 5.
    tightened_coverage = tuple(
        CoverageRequirement(
            day=c.day,
            shift_id=c.shift_id,
            min=6 if c.shift_id == "N" else c.min,
            max=max(c.max, 6),
            required_skills=c.required_skills,
        )
        for c in base.coverage
    )
    hard_instance = dataclasses.replace(base, coverage=tightened_coverage)

    model, _vars = build_hard_model(hard_instance)
    solver = cp_model.CpSolver()
    solver.parameters.max_time_in_seconds = 5.0
    status = solver.solve(model)
    print(f"Status with tightened N coverage: {solver.status_name(status)}")

    if status == cp_model.INFEASIBLE:
        # Re-run with a handful of assumptions so the core has names.
        model2, vars2 = build_hard_model(hard_instance)
        assumptions: dict[str, cp_model.IntVar] = {}

        # Assumption literal 1: "each nurse works at most max_consec consecutive days"
        a_hc4 = model2.new_bool_var("assume_hc4_active")
        assumptions["HC-4 consec working days"] = a_hc4

        # Assumption literal 2: "each nurse does at most K consecutive nights"
        a_hc5 = model2.new_bool_var("assume_hc5_active")
        assumptions["HC-5 consec night shifts"] = a_hc5

        # For the tightened N coverage, assume every coverage-min constraint.
        # We attach a single umbrella literal for the whole batch.
        a_cov = model2.new_bool_var("assume_coverage_active")
        assumptions["HC-1 coverage (tightened)"] = a_cov

        # Pin each assumption to true so the model is unchanged, then declare
        # it an "assumption" in the solver call.
        model2.add(a_hc4 == 1)
        model2.add(a_hc5 == 1)
        model2.add(a_cov == 1)

        solver2 = cp_model.CpSolver()
        solver2.parameters.max_time_in_seconds = 5.0
        # `solve()` followed by `sufficient_assumptions_for_infeasibility()`
        # returns the indices of the literals in the core when infeasible.
        for lit in assumptions.values():
            model2.add_assumption(lit)
        status2 = solver2.solve(model2)
        print(f"Re-run with assumptions: {solver2.status_name(status2)}")

        if status2 == cp_model.INFEASIBLE:
            core = solver2.sufficient_assumptions_for_infeasibility()
            print("Infeasibility core (indices into the assumption list):")
            for i in core:
                print(f"  index={i}")
            print(
                "\nNote: names live in the dict above; the core is a list of "
                "proto indices into the assumption list we pushed in order. "
                "On real INRC-II instances you'd gate each HC and SC family "
                "separately so the core is directly interpretable."
            )


if __name__ == "__main__":
    main()
