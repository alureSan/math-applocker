package com.mathapplocker.foss.math

sealed interface ExpressionResult {
    data class Value(val value: Int) : ExpressionResult
    data object SyntaxError : ExpressionResult
    data object DivisionByZero : ExpressionResult
    data object NotAnInteger : ExpressionResult
    data object Overflow : ExpressionResult
}

/**
 * Small calculator-style evaluator for parent-authored custom questions.
 *
 * Accepts whole numbers, parentheses, unary signs and the four operations
 * (typing `*`/`x`/`X` is normalised to `×`, `/` to `÷`). Division must give a
 * whole number because challenge answers are integers.
 */
object ExpressionEvaluator {

    private const val ALLOWED_CHARS = "0123456789+-−×÷*/()xX"

    /** Drops characters that can never be part of a math problem. */
    fun sanitize(input: String): String = buildString(input.length) {
        input.forEach { c ->
            when {
                c.isWhitespace() -> append(' ')
                c in ALLOWED_CHARS -> append(c)
            }
        }
    }

    /** Canonical form used for storage and display, e.g. `3 x 4 / 2` → `3 × 4 ÷ 2`. */
    fun normalize(input: String): String = sanitize(input)
        .replace('−', '-')
        .replace('x', '×')
        .replace('X', '×')
        .replace('*', '×')
        .replace('/', '÷')
        .trim()
        .replace(Regex(" +"), " ")

    fun evaluate(input: String): ExpressionResult {
        val expression = normalize(input)
        if (expression.isEmpty()) return ExpressionResult.SyntaxError
        return try {
            val value = Parser(expression).parse()
            if (value !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
                ExpressionResult.Overflow
            } else {
                ExpressionResult.Value(value.toInt())
            }
        } catch (_: SyntaxException) {
            ExpressionResult.SyntaxError
        } catch (_: DivisionByZeroException) {
            ExpressionResult.DivisionByZero
        } catch (_: NonIntegerDivisionException) {
            ExpressionResult.NotAnInteger
        } catch (_: OverflowException) {
            ExpressionResult.Overflow
        } catch (_: ArithmeticException) {
            ExpressionResult.Overflow
        }
    }

    private class Parser(private val text: String) {
        private var position = 0

        fun parse(): Long {
            val value = parseExpression()
            skipSpaces()
            if (position != text.length) throw SyntaxException()
            return value
        }

        private fun parseExpression(): Long {
            var value = parseTerm()
            while (true) {
                skipSpaces()
                val op = peek() ?: return value
                if (op != '+' && op != '-') return value
                position++
                val right = parseTerm()
                value = if (op == '+') {
                    Math.addExact(value, right)
                } else {
                    Math.subtractExact(value, right)
                }
            }
        }

        private fun parseTerm(): Long {
            var value = parseFactor()
            while (true) {
                skipSpaces()
                val op = peek() ?: return value
                if (op != '×' && op != '÷') return value
                position++
                val right = parseFactor()
                value = if (op == '×') {
                    Math.multiplyExact(value, right)
                } else {
                    if (right == 0L) throw DivisionByZeroException()
                    if (value % right != 0L) throw NonIntegerDivisionException()
                    value / right
                }
            }
        }

        private fun parseFactor(): Long {
            skipSpaces()
            return when (val c = peek()) {
                null -> throw SyntaxException()
                '+' -> {
                    position++
                    parseFactor()
                }

                '-' -> {
                    position++
                    try {
                        Math.negateExact(parseFactor())
                    } catch (_: ArithmeticException) {
                        throw OverflowException()
                    }
                }

                '(' -> {
                    position++
                    val value = parseExpression()
                    skipSpaces()
                    if (peek() != ')') throw SyntaxException()
                    position++
                    value
                }

                else -> if (c.isDigit()) parseNumber() else throw SyntaxException()
            }
        }

        private fun parseNumber(): Long {
            val start = position
            while (position < text.length && text[position].isDigit()) position++
            val digits = text.substring(start, position)
            return digits.toLongOrNull() ?: throw OverflowException()
        }

        private fun skipSpaces() {
            while (position < text.length && text[position] == ' ') position++
        }

        private fun peek(): Char? = if (position < text.length) text[position] else null
    }

    private class SyntaxException : RuntimeException()
    private class DivisionByZeroException : RuntimeException()
    private class NonIntegerDivisionException : RuntimeException()
    private class OverflowException : RuntimeException()
}
