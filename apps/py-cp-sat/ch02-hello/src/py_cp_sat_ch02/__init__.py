"""Chapter 2 — Hello, CP-SAT.

A three-line CP-SAT model to introduce the API:
    3x + 2y = 12
    x + y   <= 5
    x, y    in [0, 10]
    maximize x + y

Import via ``from py_cp_sat_ch02.main import solve, Solution``; the package itself
intentionally does not re-export them so ``python -m py_cp_sat_ch02.main`` doesn't
trip the `sys.modules` double-import warning.
"""

__all__: list[str] = []
