package io.vanja.cpsat

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * The classic SEND + MORE = MONEY cryptarithmetic puzzle. Each distinct letter
 * stands for a distinct digit (0..9), leading digits (S, M) are non-zero, and
 * the addition must hold.
 *
 * There's a unique solution:
 *   S=9, E=5, N=6, D=7, M=1, O=0, R=8, Y=2
 *   SEND  = 9567
 *   MORE  = 1085
 *   MONEY = 10652
 */
class SendMoreMoneySpec : StringSpec({

    "SEND + MORE = MONEY has the classic unique solution" {
        lateinit var s: IntVar
        lateinit var e: IntVar
        lateinit var n: IntVar
        lateinit var d: IntVar
        lateinit var m: IntVar
        lateinit var o: IntVar
        lateinit var r: IntVar
        lateinit var y: IntVar

        val model = cpModel {
            s = intVar("S", 1..9)   // leading digit non-zero
            e = intVar("E", 0..9)
            n = intVar("N", 0..9)
            d = intVar("D", 0..9)
            m = intVar("M", 1..9)   // leading digit non-zero
            o = intVar("O", 0..9)
            r = intVar("R", 0..9)
            y = intVar("Y", 0..9)

            allDifferent(listOf(s, e, n, d, m, o, r, y))

            // SEND = 1000*S + 100*E + 10*N + D
            // MORE = 1000*M + 100*O + 10*R + E
            // MONEY = 10000*M + 1000*O + 100*N + 10*E + Y
            // SEND + MORE = MONEY
            val send = 1000 * s + 100 * e + 10 * n + d
            val more = 1000 * m + 100 * o + 10 * r + e
            val money = 10000 * m + 1000 * o + 100 * n + 10 * e + y

            constraint {
                +(send + more eq money)
            }
        }

        val res = model.solveBlocking { randomSeed = 42 }
        res.shouldBeInstanceOf<SolveResult.Optimal>()

        res.values[s] shouldBe 9L
        res.values[e] shouldBe 5L
        res.values[n] shouldBe 6L
        res.values[d] shouldBe 7L
        res.values[m] shouldBe 1L
        res.values[o] shouldBe 0L
        res.values[r] shouldBe 8L
        res.values[y] shouldBe 2L

        // Sanity: SEND + MORE = MONEY.
        val sendVal = 9567L
        val moreVal = 1085L
        val moneyVal = 10652L
        (sendVal + moreVal) shouldBe moneyVal
    }
})
