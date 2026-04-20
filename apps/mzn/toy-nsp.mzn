% Toy Nurse Scheduling — declarative mini-NSP used by Chapter 8.
%
% Decisions: work[n, d, s] is true iff nurse n works shift s on day d.
% Hard constraints:
%   HC-1  every shift/day needs exactly one nurse.
%   HC-2  a nurse works at most one shift per day.
%   HC-3  a nurse logs at most `max_work` shifts over the horizon.
% Objective: minimize the spread of total shifts per nurse (max - min).

int: n_nurses;
int: n_days;
int: n_shifts;
int: max_work;
int: min_work;

set of int: NURSES = 1..n_nurses;
set of int: DAYS = 1..n_days;
set of int: SHIFTS = 1..n_shifts;

array[NURSES, DAYS, SHIFTS] of var 0..1: work;

% HC-1: each (day, shift) staffed by exactly one nurse.
constraint forall(d in DAYS, s in SHIFTS)(
  sum(n in NURSES)(work[n, d, s]) = 1
);

% HC-2: no nurse works two shifts on the same day.
constraint forall(n in NURSES, d in DAYS)(
  sum(s in SHIFTS)(work[n, d, s]) <= 1
);

% HC-3: workload bounds per nurse.
array[NURSES] of var 0..n_days * n_shifts: total;
constraint forall(n in NURSES)(
  total[n] = sum(d in DAYS, s in SHIFTS)(work[n, d, s])
);
constraint forall(n in NURSES)(
  total[n] <= max_work /\ total[n] >= min_work
);

var 0..n_days * n_shifts: spread = max(total) - min(total);
solve minimize spread;

output [
  "spread=", show(spread), "\n",
  "totals=", show(total), "\n",
  "work=", show(work)
];
