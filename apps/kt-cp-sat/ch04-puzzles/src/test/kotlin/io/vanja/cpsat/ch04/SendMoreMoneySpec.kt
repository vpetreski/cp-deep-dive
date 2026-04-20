package io.vanja.cpsat.ch04

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class SendMoreMoneySpec : StringSpec({
    "SEND + MORE = MONEY has the unique classic solution" {
        val sol = solveSendMoreMoney()
        sol shouldNotBe null
        val d = sol!!.digits
        d['S'] shouldBe 9L
        d['E'] shouldBe 5L
        d['N'] shouldBe 6L
        d['D'] shouldBe 7L
        d['M'] shouldBe 1L
        d['O'] shouldBe 0L
        d['R'] shouldBe 8L
        d['Y'] shouldBe 2L

        sol.send shouldBe 9567L
        sol.more shouldBe 1085L
        sol.money shouldBe 10652L
        (sol.send + sol.more) shouldBe sol.money
    }

    "render contains the arithmetic" {
        val sol = solveSendMoreMoney()!!
        val out = renderSendMoreMoney(sol)
        (out.contains("9567") && out.contains("1085") && out.contains("10652")) shouldBe true
    }
})
