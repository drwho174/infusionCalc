package com.drwho174.infusioncalc.tci

import java.math.BigDecimal
import java.math.MathContext

/**
 * BigDecimal-математика для TCI: экспонента и натуральный логарифм.
 * Без примитивов с плавающей точкой и экспоненты/округления из java.lang.Math
 * (D-TCI-10, ⛔-TCI-7).
 *
 * exp — рядом Тейлора с редукцией аргумента (x → x/2^n, затем возведение в квадрат);
 * ln  — рядом atanh: ln(x) = 2·atanh((x−1)/(x+1)).
 */
internal object TciMath {
    private val MC = MathContext.DECIMAL128
    private val TWO = BigDecimal(2)
    private val HALF = BigDecimal("0.5")

    fun exp(x: BigDecimal): BigDecimal {
        if (x.signum() == 0) return BigDecimal.ONE
        var n = 0
        var reduced = x
        while (reduced.abs() > HALF) {
            reduced = reduced.divide(TWO, MC)
            n++
        }
        var sum = reduced.add(BigDecimal.ONE, MC)
        var term = reduced
        var k = 2
        while (true) {
            term = term.multiply(reduced, MC).divide(BigDecimal(k), MC)
            val next = sum.add(term, MC)
            if (next.compareTo(sum) == 0) break
            sum = next
            k++
            if (k > 400) break
        }
        repeat(n) { sum = sum.multiply(sum, MC) }
        return sum
    }

    fun ln(x: BigDecimal): BigDecimal {
        require(x.signum() > 0) { "ln defined for positive arguments" }
        val one = BigDecimal.ONE
        val z = x.subtract(one, MC).divide(x.add(one, MC), MC)
        val z2 = z.multiply(z, MC)
        var sum = z
        var term = z
        var k = 1
        while (true) {
            term = term.multiply(z2, MC)
            val delta = term.divide(BigDecimal(2L * k + 1), MC)
            val next = sum.add(delta, MC)
            if (next.compareTo(sum) == 0) break
            sum = next
            k++
            if (k > 10000) break
        }
        return sum.multiply(TWO, MC)
    }
}
