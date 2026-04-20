package io.vanja.cpsat

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Coverage for global constraints that don't have their own dedicated spec.
 * Each test is a small instance with a known solution.
 */
class GlobalsSpec : StringSpec({

    "element: target = values[index] works as a lookup" {
        lateinit var idx: IntVar
        lateinit var target: IntVar
        val model = cpModel {
            idx = intVar("idx", 0..3)
            target = intVar("target", 0..100)
            element(idx, longArrayOf(10L, 20L, 30L, 40L), target)
            constraint {
                +(idx eq 2L)
            }
        }
        val res = model.solveBlocking { randomSeed = 42 }
        res.shouldBeInstanceOf<SolveResult.Optimal>()
        res.values[target] shouldBe 30L
    }

    "element over a list of variables" {
        lateinit var idx: IntVar
        lateinit var target: IntVar
        lateinit var slots: List<IntVar>
        val model = cpModel {
            slots = intVarList("s", 3, 0..100)
            idx = intVar("idx", 0..2)
            target = intVar("target", 0..100)
            element(idx, slots, target)
            constraint {
                +(slots[0] eq 7L)
                +(slots[1] eq 8L)
                +(slots[2] eq 9L)
                +(idx eq 1L)
            }
        }
        val res = model.solveBlocking { randomSeed = 42 }
        res.shouldBeInstanceOf<SolveResult.Optimal>()
        res.values[target] shouldBe 8L
    }

    "inverse: f[i] = j iff g[j] = i for a permutation of 4" {
        val n = 4
        lateinit var f: List<IntVar>
        lateinit var g: List<IntVar>
        val model = cpModel {
            f = intVarList("f", n, 0 until n)
            g = intVarList("g", n, 0 until n)
            inverse(f, g)
            // Force f to the reverse permutation 3,2,1,0. g must then also be 3,2,1,0.
            constraint {
                +(f[0] eq 3L)
                +(f[1] eq 2L)
                +(f[2] eq 1L)
                +(f[3] eq 0L)
            }
        }
        val res = model.solveBlocking { randomSeed = 42 }
        res.shouldBeInstanceOf<SolveResult.Optimal>()
        g.map { res.values[it] } shouldBe listOf(3L, 2L, 1L, 0L)
    }

    "table: allowed tuples restrict assignments" {
        lateinit var x: IntVar
        lateinit var y: IntVar
        val model = cpModel {
            x = intVar("x", 0..10)
            y = intVar("y", 0..10)
            table(
                listOf(x, y),
                listOf(
                    longArrayOf(1L, 5L),
                    longArrayOf(3L, 7L),
                    longArrayOf(9L, 2L),
                ),
            )
            // Force x to be 3; table forces y to be 7.
            constraint { +(x eq 3L) }
        }
        val res = model.solveBlocking { randomSeed = 42 }
        res.shouldBeInstanceOf<SolveResult.Optimal>()
        res.values[x] shouldBe 3L
        res.values[y] shouldBe 7L
    }

    "exactlyOne picks exactly one boolean" {
        lateinit var bs: List<BoolVar>
        val model = cpModel {
            bs = boolVarList("b", 4)
            exactlyOne(bs)
        }
        val res = model.solveBlocking { randomSeed = 42 }
        res.shouldBeInstanceOf<SolveResult.Optimal>()
        val trueCount = bs.count { res.values[it] }
        trueCount shouldBe 1
    }

    "atMostOne at most one true; allows zero" {
        lateinit var bs: List<BoolVar>
        val model = cpModel {
            bs = boolVarList("b", 3)
            atMostOne(bs)
            // Force one to zero — solver can pick 0 trues or 1 true.
            constraint { +(bs[0] eq 0L) }
        }
        val res = model.solveBlocking { randomSeed = 42 }
        res.shouldBeInstanceOf<SolveResult.Optimal>()
        val trueCount = bs.count { res.values[it] }
        (trueCount in 0..1) shouldBe true
    }

    "atLeastOne at least one true" {
        lateinit var bs: List<BoolVar>
        val model = cpModel {
            bs = boolVarList("b", 3)
            atLeastOne(bs)
        }
        val res = model.solveBlocking { randomSeed = 42 }
        res.shouldBeInstanceOf<SolveResult.Optimal>()
        val trueCount = bs.count { res.values[it] }
        (trueCount >= 1) shouldBe true
    }

    "circuit constraint enforces a Hamiltonian cycle" {
        // Tiny 3-node graph; all arcs are BoolVars; the circuit selects a cycle.
        // Let's model fully-connected and expect the solver to pick some valid cycle.
        // Graph nodes: 0, 1, 2.
        lateinit var arcs: Map<Pair<Int, Int>, BoolVar>
        val model = cpModel {
            val arcPairs = listOf(
                0 to 1, 1 to 2, 2 to 0,
                0 to 2, 2 to 1, 1 to 0,
            )
            arcs = arcPairs.associateWith { (a, b) -> boolVar("arc_${a}_$b") }
            circuit(arcs.map { (pair, b) -> Triple(pair.first, pair.second, b) })
        }
        val res = model.solveBlocking { randomSeed = 42 }
        res.shouldBeInstanceOf<SolveResult.Optimal>()
        // Exactly 3 arcs must be selected to form a 3-cycle.
        val selected = arcs.entries.filter { res.values[it.value] }.map { it.key }
        selected.size shouldBe 3
    }

    "automaton accepts a matching sequence" {
        // Simple DFA: alternates 0/1 starting with 0. Length 4 valid seq: 0,1,0,1.
        lateinit var seq: List<IntVar>
        val model = cpModel {
            seq = intVarList("s", 4, 0..1)
            automaton(
                seq,
                startState = 1L,
                transitions = listOf(
                    Triple(1, 0L, 2),   // from state 1, on 0, go to 2
                    Triple(2, 1L, 1),   // from state 2, on 1, go to 1
                ),
                finalStates = listOf(1),
            )
        }
        val res = model.solveBlocking { randomSeed = 42 }
        res.shouldBeInstanceOf<SolveResult.Optimal>()
        val vals = seq.map { res.values[it] }
        vals shouldBe listOf(0L, 1L, 0L, 1L)
    }

    "forbidden tuples exclude assignments" {
        lateinit var x: IntVar
        lateinit var y: IntVar
        val model = cpModel {
            x = intVar("x", 0..2)
            y = intVar("y", 0..2)
            forbidden(
                listOf(x, y),
                listOf(
                    longArrayOf(0L, 0L),
                    longArrayOf(1L, 1L),
                    longArrayOf(2L, 2L),
                ),
            )
            // Force x == y via an additional constraint — should be infeasible.
            constraint { +(x eq y) }
        }
        val res = model.solveBlocking { randomSeed = 42 }
        res shouldBe SolveResult.Infeasible
    }

    "allDifferent distinct values" {
        lateinit var xs: List<IntVar>
        val model = cpModel {
            xs = intVarList("x", 5, 0..4)
            allDifferent(xs)
        }
        val res = model.solveBlocking { randomSeed = 42 }
        res.shouldBeInstanceOf<SolveResult.Optimal>()
        val vals = xs.map { res.values[it] }
        vals.toSet() shouldContainExactlyInAnyOrder setOf(0L, 1L, 2L, 3L, 4L)
    }

    "channelEq ties boolean to an equality" {
        lateinit var x: IntVar
        lateinit var b: BoolVar
        val model = cpModel {
            x = intVar("x", 0..10)
            b = boolVar("b")
            channelEq(b, x, 5L)
            constraint { +(b eq 1L) }
        }
        val res = model.solveBlocking { randomSeed = 42 }
        res.shouldBeInstanceOf<SolveResult.Optimal>()
        res.values[x] shouldBe 5L
        res.values[b] shouldBe true
    }
})
