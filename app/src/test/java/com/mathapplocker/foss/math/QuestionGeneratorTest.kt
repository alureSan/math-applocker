package com.mathapplocker.foss.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QuestionGeneratorTest {

    private val allOps = Operation.entries.toSet()

    @Test
    fun `generates requested number of questions`() {
        Difficulty.entries.forEach { difficulty ->
            val quiz = QuestionGenerator.generate(
                count = 7,
                difficulty = difficulty,
                operations = allOps,
                random = Random(1),
            )
            assertEquals(7, quiz.size)
            assertTrue(quiz.all { it.text.isNotBlank() })
        }
    }

    @Test
    fun `kid subtraction never goes negative`() {
        val kidLevels = Difficulty.entries.filterNot { it.allowNegative }
        repeat(200) {
            kidLevels.forEach { difficulty ->
                val q = QuestionGenerator.generateOne(difficulty, setOf(Operation.SUB), Random(it))
                assertTrue("Negative answer at ${difficulty.key}: ${q.text}", q.answer >= 0)
            }
        }
    }

    @Test
    fun `division always has an exact integer answer`() {
        repeat(200) {
            val q = QuestionGenerator.generateOne(Difficulty.ADULT, setOf(Operation.DIV), Random(it))
            val parts = q.text.split(" ÷ ")
            assertEquals(2, parts.size)
            val dividend = parts[0].toInt()
            val divisor = parts[1].toInt()
            assertEquals(divisor * q.answer, dividend)
        }
    }

    @Test
    fun `adult subtraction may be negative but result matches expression`() {
        var sawNegative = false
        repeat(500) {
            val q = QuestionGenerator.generateOne(Difficulty.ADULT, setOf(Operation.SUB), Random(it))
            val (a, b) = q.text.split(" − ").map(String::toInt)
            assertEquals(a - b, q.answer)
            if (q.answer < 0) sawNegative = true
        }
        assertTrue("Expected at least one negative adult subtraction", sawNegative)
    }

    @Test
    fun `custom questions are used first and mixed in`() {
        val custom = (1..5).map { QuizQuestion("custom $it", it, isCustom = true) }
        val quiz = QuestionGenerator.generate(
            count = 3,
            difficulty = Difficulty.AGE_8_10,
            operations = allOps,
            customQuestions = custom,
            random = Random(7),
        )
        assertEquals(3, quiz.size)
        assertTrue(quiz.all { it.isCustom })
        assertEquals(3, quiz.map { it.text }.toSet().size)

        val mixed = QuestionGenerator.generate(
            count = 5,
            difficulty = Difficulty.AGE_8_10,
            operations = allOps,
            customQuestions = custom,
            random = Random(7),
        )
        assertEquals(5, mixed.size)
        assertEquals(5, mixed.count { it.isCustom })
    }

    @Test
    fun `empty operations fall back to every operation`() {
        val quiz = QuestionGenerator.generate(
            count = 20,
            difficulty = Difficulty.AGE_5_7,
            operations = emptySet(),
            random = Random(3),
        )
        assertFalse(quiz.isEmpty())
        assertTrue(
            quiz.all { q ->
                q.text.contains(" + ") || q.text.contains(" − ") ||
                    q.text.contains(" × ") || q.text.contains(" ÷ ")
            },
        )
    }

    @Test
    fun `explicit digit count controls addition operands`() {
        for (digits in 1..4) {
            repeat(100) { seed ->
                val q = QuestionGenerator.generateOne(
                    Difficulty.AGE_5_7,
                    setOf(Operation.ADD),
                    Random(seed),
                    digits,
                )
                val (a, b) = q.text.split(" + ").map(String::toInt)
                assertEquals(a + b, q.answer)
                assertEquals("digits=$digits: ${q.text}", digits, a.toString().length)
                assertEquals("digits=$digits: ${q.text}", digits, b.toString().length)
            }
        }
    }

    @Test
    fun `explicit digit multiplication stays readable and exact`() {
        for (digits in 1..4) {
            repeat(200) { seed ->
                val q = QuestionGenerator.generateOne(
                    Difficulty.AGE_5_7,
                    setOf(Operation.MUL),
                    Random(seed),
                    digits,
                )
                val (a, b) = q.text.split(" × ").map(String::toInt)
                assertEquals(a * b, q.answer)
                assertTrue("Product too large: ${q.text} = ${q.answer}", q.answer in 4..99_999)
            }
        }
    }

    @Test
    fun `explicit digit division always has an exact answer`() {
        for (digits in 1..4) {
            repeat(200) { seed ->
                val q = QuestionGenerator.generateOne(
                    Difficulty.AGE_5_7,
                    setOf(Operation.DIV),
                    Random(seed),
                    digits,
                )
                val (dividend, divisor) = q.text.split(" ÷ ").map(String::toInt)
                assertEquals(0, dividend % divisor)
                assertEquals(dividend / divisor, q.answer)
            }
        }
    }

    @Test
    fun `selected multiplication tables drive multiplication questions`() {
        val tables = setOf(3, 7)
        repeat(200) { seed ->
            val q = QuestionGenerator.generateOne(
                Difficulty.AGE_5_7,
                setOf(Operation.MUL),
                Random(seed),
                timesTables = tables,
            )
            val (a, b) = q.text.split(" × ").map(String::toInt)
            assertTrue("Unexpected table: ${q.text}", a in tables)
            assertTrue("Factor out of range: ${q.text}", b in 1..10)
            assertEquals(a * b, q.answer)
        }
    }

    @Test
    fun `advanced challenge uses only custom content`() {
        val custom = listOf(
            QuizQuestion("Capital of France", 0, isCustom = true, textAnswer = "Paris"),
            QuizQuestion("5 + 5", 10, isCustom = true),
        )
        val quiz = QuestionGenerator.generateAdvanced(5, custom, setOf(3), Random(2))
        assertEquals(5, quiz.size)
        assertEquals(5, quiz.map { it.text }.toSet().size)
        assertTrue(
            quiz.all { q -> q.isCustom || q.text.startsWith("3 × ") },
        )
    }

    @Test
    fun `advanced challenge is empty without content`() {
        val quiz = QuestionGenerator.generateAdvanced(
            count = 3,
            customQuestions = emptyList(),
            timesTables = emptySet(),
            random = Random(1),
        )
        assertTrue(quiz.isEmpty())
    }

    @Test
    fun `advanced challenge caps at available content`() {
        val quiz = QuestionGenerator.generateAdvanced(
            count = 20,
            customQuestions = emptyList(),
            timesTables = setOf(7),
            random = Random(3),
        )
        assertEquals(10, quiz.size)
        assertTrue(quiz.all { it.text.startsWith("7 × ") })
    }
}
