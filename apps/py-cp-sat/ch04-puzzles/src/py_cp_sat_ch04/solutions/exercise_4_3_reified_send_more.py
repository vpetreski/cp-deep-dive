"""Exercise 4.3 — SEND + MORE = MONEY with a reified ``E is odd``.

Adds the soft implication ``odd_e => M + O >= 5``. The baseline assignment
has E=5 (odd) and M+O=1, so the implication would be violated — solver
must find a different solution or prove infeasibility.
"""

from __future__ import annotations

from ortools.sat.python import cp_model


def solve_reified() -> dict[str, int] | None:
    """Return the reified-constrained assignment (or None if infeasible)."""
    model = cp_model.CpModel()
    letters = ("S", "E", "N", "D", "M", "O", "R", "Y")
    v = {letter: model.new_int_var(0, 9, letter) for letter in letters}
    model.add_all_different(list(v.values()))
    model.add(v["S"] >= 1)
    model.add(v["M"] >= 1)

    send = 1000 * v["S"] + 100 * v["E"] + 10 * v["N"] + v["D"]
    more = 1000 * v["M"] + 100 * v["O"] + 10 * v["R"] + v["E"]
    money = 10000 * v["M"] + 1000 * v["O"] + 100 * v["N"] + 10 * v["E"] + v["Y"]
    model.add(send + more == money)

    # odd_e <=> (E mod 2 == 1)
    odd_e = model.new_bool_var("odd_e")
    e_mod_2 = model.new_int_var(0, 1, "e_mod_2")
    model.add_modulo_equality(e_mod_2, v["E"], 2)
    model.add(e_mod_2 == 1).only_enforce_if(odd_e)
    model.add(e_mod_2 == 0).only_enforce_if(~odd_e)

    # odd_e => M + O >= 5
    model.add(v["M"] + v["O"] >= 5).only_enforce_if(odd_e)

    solver = cp_model.CpSolver()
    solver.parameters.random_seed = 42
    status = solver.solve(model)
    if status not in (cp_model.OPTIMAL, cp_model.FEASIBLE):
        return None
    return {k: int(solver.value(var)) for k, var in v.items()}


def main() -> None:
    sol = solve_reified()
    if sol is None:
        print("Reified constraint made the puzzle infeasible.")
        return
    print("Reified solution:", sol)
    # Puzzle equation still holds:
    a = sol
    send = 1000 * a["S"] + 100 * a["E"] + 10 * a["N"] + a["D"]
    more = 1000 * a["M"] + 100 * a["O"] + 10 * a["R"] + a["E"]
    money = 10000 * a["M"] + 1000 * a["O"] + 100 * a["N"] + 10 * a["E"] + a["Y"]
    print(f"{send} + {more} = {money}  (balance: {send + more == money})")


if __name__ == "__main__":
    main()
