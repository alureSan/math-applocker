package com.mathapplocker.foss.math

/**
 * Age presets control operand ranges. Multiplication/division use their own,
 * smaller ranges so that results stay reasonable for the selected age.
 */
enum class Difficulty(
    val key: String,
    val addMax: Int,
    val subMax: Int,
    val mulMax: Int,
    val divMax: Int,
    val allowNegative: Boolean,
) {
    AGE_3_4("age_3_4", addMax = 10, subMax = 10, mulMax = 5, divMax = 5, allowNegative = false),
    AGE_5_7("age_5_7", addMax = 20, subMax = 20, mulMax = 5, divMax = 5, allowNegative = false),
    AGE_8_10("age_8_10", addMax = 50, subMax = 50, mulMax = 10, divMax = 10, allowNegative = false),
    AGE_11_13("age_11_13", addMax = 100, subMax = 100, mulMax = 12, divMax = 12, allowNegative = false),
    AGE_14_17("age_14_17", addMax = 200, subMax = 200, mulMax = 15, divMax = 15, allowNegative = false),
    ADULT("adult", addMax = 500, subMax = 500, mulMax = 25, divMax = 25, allowNegative = true);

    companion object {
        fun fromKey(key: String?): Difficulty =
            entries.firstOrNull { it.key == key } ?: AGE_5_7
    }
}

enum class Operation(val key: String, val symbol: String) {
    ADD("add", "+"),
    SUB("sub", "−"),
    MUL("mul", "×"),
    DIV("div", "÷");

    companion object {
        fun fromKey(key: String?): Operation? = entries.firstOrNull { it.key == key }
    }
}

data class QuizQuestion(
    val text: String,
    val answer: Int,
    val isCustom: Boolean = false,
)

object QuestionGenerator {

    private const val MAX_ATTEMPTS = 12

    /**
     * Builds one challenge: up to [count] custom questions (randomly chosen), and
     * normal generated questions for the remaining slots. The final list is shuffled.
     */
    fun generate(
        count: Int,
        difficulty: Difficulty,
        operations: Set<Operation>,
        customQuestions: List<QuizQuestion> = emptyList(),
        random: kotlin.random.Random = kotlin.random.Random.Default,
    ): List<QuizQuestion> {
        val wanted = count.coerceAtLeast(1)
        val ops = operations.ifEmpty { Operation.entries.toSet() }

        val pickedCustom = customQuestions
            .shuffled(random)
            .take(wanted)

        val generated = ArrayList<QuizQuestion>(wanted - pickedCustom.size)
        val seen = pickedCustom.mapTo(HashSet()) { it.text }
        var guard = 0
        while (generated.size < wanted - pickedCustom.size && guard < (wanted * MAX_ATTEMPTS)) {
            guard++
            val question = generateOne(difficulty, ops, random)
            if (seen.add(question.text)) {
                generated += question
            }
        }
        // Extremely unlikely fallback if dedupe kept us short.
        while (generated.size < wanted - pickedCustom.size) {
            generated += generateOne(difficulty, ops, random)
        }

        return (pickedCustom + generated).shuffled(random)
    }

    fun generateOne(
        difficulty: Difficulty,
        operations: Set<Operation>,
        random: kotlin.random.Random = kotlin.random.Random.Default,
    ): QuizQuestion {
        val op = operations.randomOrNull(random) ?: Operation.ADD
        return when (op) {
            Operation.ADD -> {
                val a = random.nextInt(1, difficulty.addMax + 1)
                val b = random.nextInt(1, difficulty.addMax + 1)
                QuizQuestion("$a + $b", a + b)
            }

            Operation.SUB -> {
                var a = random.nextInt(1, difficulty.subMax + 1)
                var b = random.nextInt(1, difficulty.subMax + 1)
                if (!difficulty.allowNegative && b > a) {
                    val tmp = a; a = b; b = tmp
                }
                QuizQuestion("$a − $b", a - b)
            }

            Operation.MUL -> {
                val a = random.nextInt(2, difficulty.mulMax + 1)
                val b = random.nextInt(2, difficulty.mulMax + 1)
                QuizQuestion("$a × $b", a * b)
            }

            Operation.DIV -> {
                val divisor = random.nextInt(2, difficulty.divMax + 1)
                val result = random.nextInt(2, difficulty.divMax + 1)
                QuizQuestion("${divisor * result} ÷ $divisor", result)
            }
        }
    }
}
