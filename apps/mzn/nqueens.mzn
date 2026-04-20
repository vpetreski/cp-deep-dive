% N-Queens in MiniZinc — three alldifferents, declarative twin of
% apps/py-cp-sat/ch04-puzzles/src/py_cp_sat_ch04/n_queens.py.

include "alldifferent.mzn";

int: n;
array[1..n] of var 1..n: q;

constraint alldifferent(q);
constraint alldifferent([q[i] + i | i in 1..n]);
constraint alldifferent([q[i] - i | i in 1..n]);

solve satisfy;

output ["q = ", show(q)];
