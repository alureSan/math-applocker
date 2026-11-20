package com.mathapplocker.foss.math

import org.junit.Assert.assertEquals
import org.junit.Test

class ExpressionEvaluatorTest {

    private fun value(input: String): Int =
        (ExpressionEvaluator.evaluate(input) as ExpressionResult.Value).value

    @Test
    fun `evaluates precedence and parentheses`() {
        assertEquals(14, value("2 + 3 × 4"))
        assertEquals(20, value("(2 + 3) × 4"))
        assertEquals(5, value("20 ÷ (2 + 2)"))
    }

    @Test
    fun `accepts calculator aliases`() {
        assertEquals(12, value("3 x 4"))
        assertEquals(3, value("6 / 2"))
        assertEquals(5, value("7 − 2"))
        assertEquals(7, value("2 + 10 / 2"))
    }

    @Test
    fun `supports unary signs`() {
        assertEquals(3, value("-5 + 8"))
        assertEquals(-6, value("-(2 + 4)"))
        assertEquals(-3, value("-6 ÷ 2"))
        assertEquals(8, value("+8"))
    }

    @Test
    fun `rejects invalid syntax`() {
        listOf("", "2 +", "+", "hello", "2 3", "()", "2 + × 3", "(2 + 3").forEach { input ->
            assertEquals(
                "Expected syntax error for '$input'",
                ExpressionResult.SyntaxError,
                ExpressionEvaluator.evaluate(input),
            )
        }
    }

    @Test
    fun `rejects division by zero`() {
        assertEquals(
            ExpressionResult.DivisionByZero,
            ExpressionEvaluator.evaluate("5 ÷ 0"),
        )
        assertEquals(
            ExpressionResult.DivisionByZero,
            ExpressionEvaluator.evaluate("5 ÷ (2 - 2)"),
        )
    }

    @Test
    fun `rejects non integer division`() {
        assertEquals(
            ExpressionResult.NotAnInteger,
            ExpressionEvaluator.evaluate("5 ÷ 2"),
        )
        assertEquals(
            ExpressionResult.NotAnInteger,
            ExpressionEvaluator.evaluate("10 ÷ 4"),
        )
    }

    @Test
    fun `rejects results outside the integer answer range`() {
        assertEquals(
            ExpressionResult.Overflow,
            ExpressionEvaluator.evaluate("3000000000 + 3000000000"),
        )
        assertEquals(
            ExpressionResult.Overflow,
            ExpressionEvaluator.evaluate("99999999999999999999"),
        )
        assertEquals(
            ExpressionResult.Overflow,
            ExpressionEvaluator.evaluate("999999999999 × 999999999999"),
        )
    }

    @Test
    fun `sanitizes and normalizes typed input`() {
        assertEquals("3 × 4 ÷ 2", ExpressionEvaluator.normalize(" 3 x 4 / 2 "))
        assertEquals("12 + 8", ExpressionEvaluator.sanitize("12abc + 8!"))
        assertEquals("3 + 4", ExpressionEvaluator.sanitize("3e + 4"))
    }
}
