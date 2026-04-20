% 0/1 Knapsack — declarative twin of
% apps/py-cp-sat/ch05-optimization/src/py_cp_sat_ch05/knapsack.py.

int: n;
array[1..n] of int: weight;
array[1..n] of int: value;
int: capacity;
array[1..n] of var 0..1: x;

constraint sum(i in 1..n)(weight[i] * x[i]) <= capacity;
solve maximize sum(i in 1..n)(value[i] * x[i]);

output [
  "value = ", show(sum(i in 1..n)(value[i] * x[i])), "\n",
  "weight = ", show(sum(i in 1..n)(weight[i] * x[i])), "\n",
  "pick = ", show([i | i in 1..n where fix(x[i]) == 1])
];
