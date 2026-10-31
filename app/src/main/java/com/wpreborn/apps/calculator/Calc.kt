package com.wpreborn.apps.calculator

import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.PI
import kotlin.math.E
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Expression editing and evaluation for the calculator. Expressions use the display
 * operators (+ − × ÷ ^ % !), "." as the decimal point, π, e and the functions sin, cos, tan,
 * ln, log and √. Multiplication may be implicit: 2π, 3(4+1), 2sin(30).
 */
object Calc {
    private val functions = listOf("sin(", "cos(", "tan(", "ln(", "log(", "√(")
    private const val BINARY = "+−×÷^"

    fun append(expr: String, token: String): String {
        val last = expr.lastOrNull()
        val l = last ?: ' '
        return when {
            token == "." -> {
                val number = expr.takeLastWhile { it.isDigit() || it == '.' }
                when {
                    number.contains('.') -> expr
                    number.isEmpty() -> "${expr}0."
                    else -> expr + token
                }
            }
            token.length == 1 && token[0] in BINARY -> when {
                // A leading minus is a sign; other operators start from 0.
                last == null -> if (token == "−") token else "0$token"
                token == "−" && l in "×÷^(" -> expr + token
                l in BINARY -> if (expr.length == 1) expr else expr.dropLast(1) + token
                l == '.' -> expr + "0" + token
                else -> expr + token
            }
            token.startsWith("^") && (last == null || l in BINARY || l == '(') -> expr
            token == "%" || token == "!" -> if (last == null || l in BINARY || l == '(') expr else expr + token
            token == ")" -> {
                val open = expr.count { it == '(' } - expr.count { it == ')' }
                if (open <= 0 || l == '(' || l in BINARY) expr else expr + token
            }
            // Avoid "007": a lone leading zero is replaced by the next digit.
            token.length == 1 && token[0].isDigit() && expr.takeLastWhile { it.isDigit() || it == '.' } == "0" -> expr.dropLast(1) + token
            else -> expr + token
        }
    }

    fun backspace(expr: String): String {
        functions.firstOrNull { expr.endsWith(it) }?.let { return expr.dropLast(it.length) }
        return expr.dropLast(1)
    }

    /** Toggles the sign of the last number. */
    fun negate(expr: String): String {
        if (expr.isEmpty()) return "−"
        val numberStart = expr.length - expr.takeLastWhile { it.isDigit() || it == '.' || it == 'π' || it == 'e' }.length
        val before = expr.getOrNull(numberStart - 1)
        val beforeSign = expr.getOrNull(numberStart - 2)
        return if (before == '−' && (beforeSign == null || beforeSign in BINARY || beforeSign == '(')) {
            expr.removeRange(numberStart - 1, numberStart)
        } else {
            expr.substring(0, numberStart) + "−" + expr.substring(numberStart)
        }
    }

    /** Evaluates [expr]; null when it is incomplete or undefined (e.g. 1÷0). */
    fun eval(expr: String, degrees: Boolean): Double? {
        if (expr.isBlank()) return null
        return try {
            val p = Parser(expr, degrees)
            val v = p.parseExpression()
            if (!p.atEnd()) null else v.takeIf { it.isFinite() }
        } catch (_: Exception) {
            null
        }
    }

    /** Up to 12 significant digits, no trailing zeros, scientific notation when huge or tiny. */
    fun format(v: Double): String {
        if (v == 0.0) return "0"
        val bd = BigDecimal(v).round(MathContext(12)).stripTrailingZeros()
        val a = abs(v)
        val s = if (a >= 1e15 || a < 1e-9) bd.toString() else bd.toPlainString()
        return s.replace("-", "−")
    }

    /** Result text back to an editable expression. */
    fun toExpr(result: String): String = result

    /** What the user sees: the locale decimal separator. */
    fun display(expr: String, sep: Char): String = expr.replace('.', sep)

    private class Parser(val s: String, val degrees: Boolean) {
        var i = 0

        fun atEnd() = i >= s.length

        private fun peek(): Char? = s.getOrNull(i)

        fun parseExpression(): Double {
            var v = parseTerm()
            while (true) {
                when (peek()) {
                    '+' -> { i++; v += parseTerm() }
                    '−' -> { i++; v -= parseTerm() }
                    else -> return v
                }
            }
        }

        private fun parseTerm(): Double {
            var v = parseSigned()
            while (true) {
                val c = peek() ?: return v
                when {
                    c == '×' -> { i++; v *= parseSigned() }
                    c == '÷' -> {
                        i++
                        val d = parseSigned()
                        if (d == 0.0) throw ArithmeticException()
                        v /= d
                    }
                    // Implicit multiplication: 2π, 3(1+1), 2sin(30)
                    c == '(' || c == 'π' || c == 'e' || c == '√' || c.isLetter() || c.isDigit() -> v *= parseSigned()
                    else -> return v
                }
            }
        }

        private fun parseSigned(): Double = when (peek()) {
            '−' -> { i++; -parseSigned() }
            '+' -> { i++; parseSigned() }
            else -> parsePower()
        }

        private fun parsePower(): Double {
            val base = parsePostfix()
            if (peek() == '^') {
                i++
                return base.pow(parseSigned())
            }
            return base
        }

        private fun parsePostfix(): Double {
            var v = parsePrimary()
            while (true) {
                when (peek()) {
                    '%' -> { i++; v /= 100 }
                    '!' -> { i++; v = factorial(v) }
                    else -> return v
                }
            }
        }

        private fun parsePrimary(): Double {
            val c = peek() ?: throw IllegalStateException()
            if (c.isDigit() || c == '.') {
                val start = i
                while (peek()?.let { it.isDigit() || it == '.' } == true) i++
                // Exponent from formatted results: 1.5E+20, 2E−12
                if (peek() == 'E') {
                    i++
                    if (peek() == '+' || peek() == '−' || peek() == '-') i++
                    while (peek()?.isDigit() == true) i++
                }
                return s.substring(start, i).replace('−', '-').toDouble()
            }
            if (c == 'π') { i++; return PI }
            if (c == 'e' && !s.startsWith("e(", i)) { i++; return E }
            if (c == '(') {
                i++
                val v = parseExpression()
                if (peek() == ')') i++ else if (!atEnd()) throw IllegalStateException()
                return v
            }
            for (f in functions) {
                if (s.startsWith(f, i)) {
                    i += f.length
                    val arg = parseExpression()
                    if (peek() == ')') i++ else if (!atEnd()) throw IllegalStateException()
                    val rad = if (degrees) Math.toRadians(arg) else arg
                    return when (f) {
                        "sin(" -> clean(sin(rad))
                        "cos(" -> clean(cos(rad))
                        "tan(" -> {
                            if (degrees && abs(arg % 180.0) == 90.0) throw ArithmeticException()
                            clean(tan(rad))
                        }
                        "ln(" -> ln(arg)
                        "log(" -> log10(arg)
                        else -> sqrt(arg)
                    }
                }
            }
            throw IllegalStateException()
        }

        /** sin(180°) should be 0, not 1.2e-16. */
        private fun clean(v: Double) = if (abs(v) < 1e-12) 0.0 else v

        private fun factorial(v: Double): Double {
            if (v < 0 || v != Math.floor(v) || v > 170) throw ArithmeticException()
            var r = 1.0
            for (k in 2..v.toInt()) r *= k
            return r
        }
    }
}
