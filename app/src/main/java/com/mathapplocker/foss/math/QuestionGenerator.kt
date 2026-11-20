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
    val answer: Int = 0,
    val isCustom: Boolean = false,
    /** Non-null makes this a free-text question. */
    val textAnswer: String? = null,
) {
    val isText: Boolean get() = textAnswer != null
}

object QuestionGenerator {

    private const val MAX_ATTEMPTS = 12

    /** Answers are typed on a keypad with a five-digit limit, so products stay below it. */
    private const val MAX_PRODUCT = 99_999

    /**
     * Digit counts supported by the "digits per number" setting. `0` means
     * "auto": the ranges from the age preset in [Difficulty] are used.
     */
    const val AUTO_DIGITS = 0

    /**
     * Builds one challenge: up to [count] custom questions (randomly chosen), and
     * normal generated questions for the remaining slots. The final list is shuffled.
     *
     * @param digits operand digit count (1–4), or [AUTO_DIGITS] to follow [difficulty].
     */
    fun generate(
        count: Int,
        difficulty: Difficulty,
        operations: Set<Operation>,
        customQuestions: List<QuizQuestion> = emptyList(),
        random: kotlin.random.Random = kotlin.random.Random.Default,
        digits: Int = AUTO_DIGITS,
        timesTables: Set<Int> = emptySet(),
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
            val question = generateOne(difficulty, ops, random, digits, timesTables)
            if (seen.add(question.text)) {
                generated += question
            }
        }
        // Extremely unlikely fallback if dedupe kept us short.
        while (generated.size < wanted - pickedCustom.size) {
            generated += generateOne(difficulty, ops, random, digits, timesTables)
        }

        return (pickedCustom + generated).shuffled(random)
    }

    /**
     * Advanced mode: a challenge made only from enabled custom questions and the
     * selected multiplication tables. Returns an empty list when nothing is
     * enabled, so the caller can fall back to generated questions.
     */
    fun generateAdvanced(
        count: Int,
        customQuestions: List<QuizQuestion>,
        timesTables: Set<Int>,
        random: kotlin.random.Random = kotlin.random.Random.Default,
    ): List<QuizQuestion> {
        val pool = ArrayList<QuizQuestion>(customQuestions.size + timesTables.size * 10)
        pool += customQuestions
        timesTables.filter { it in 1..9 }.forEach { table ->
            (1..10).forEach { factor ->
                pool += QuizQuestion("$table × $factor", table * factor)
            }
        }
        if (pool.isEmpty()) return emptyList()
        return pool
            .shuffled(random)
            .distinctBy { it.text }
            .take(count.coerceAtLeast(1))
            .shuffled(random)
    }

    fun generateOne(
        difficulty: Difficulty,
        operations: Set<Operation>,
        random: kotlin.random.Random = kotlin.random.Random.Default,
        digits: Int = AUTO_DIGITS,
        timesTables: Set<Int> = emptySet(),
    ): QuizQuestion {
        val op = operations.randomOrNull(random) ?: Operation.ADD
        val explicitDigits = digits in 1..4
        return when (op) {
            Operation.ADD -> {
                val range = addSubRange(difficulty.addMax, digits)
                val a = range.random(random)
                val b = range.random(random)
                QuizQuestion("$a + $b", a + b)
            }

            Operation.SUB -> {
                val range = addSubRange(difficulty.subMax, digits)
                var a = range.random(random)
                var b = range.random(random)
                if (!difficulty.allowNegative && b > a) {
                    val tmp = a; a = b; b = tmp
                }
                QuizQuestion("$a − $b", a - b)
            }

            Operation.MUL -> {
                val tables = timesTables.filter { it in 1..9 }
                if (tables.isNotEmpty()) {
                    // Selected tables drive multiplication: 3×, 5×, …
                    val table = tables.random(random)
                    val factor = random.nextInt(1, 11)
                    QuizQuestion("$table × $factor", table * factor)
                } else {
                    val factors = if (explicitDigits) {
                        val range = digitRange(digits)
                        val second = 2..readableFactorMax(range.last)
                        range.first.coerceAtLeast(2)..range.last to second
                    } else {
                        (2..difficulty.mulMax) to (2..difficulty.mulMax)
                    }
                    val a = factors.first.random(random)
                    val b = factors.second.random(random)
                    QuizQuestion("$a × $b", a * b)
                }
            }

            Operation.DIV -> {
                val divisorRange = if (explicitDigits) digitRange(digits) else 2..difficulty.divMax
                val quotientMax = if (explicitDigits) 9 else difficulty.divMax
                val divisor = divisorRange.random(random).coerceAtLeast(2)
                val result = (2..quotientMax).random(random)
                QuizQuestion("${divisor * result} ÷ $divisor", result)
            }
        }
    }

    /**
     * Addition/subtraction operands. With an explicit digit count both operands have
     * exactly that many digits; otherwise the age preset ranges are kept.
     */
    private fun addSubRange(presetMax: Int, digits: Int): IntRange =
        if (digits in 1..4) digitRange(digits) else 1..presetMax

    /** 1 → 1..9, 2 → 10..99, 3 → 100..999, 4 → 1000..9999. */
    private fun digitRange(digits: Int): IntRange {
        if (digits <= 1) return 1..9
        var power = 1
        repeat(digits - 1) { power *= 10 }
        return power..(power * 10 - 1)
    }

    /**
     * Multiplication's second factor is capped so the product stays readable and
     * fits the answer field, e.g. 4-digit operands multiply by at most 10.
     */
    private fun readableFactorMax(firstFactorMax: Int): Int =
        minOf(firstFactorMax, 99, MAX_PRODUCT / firstFactorMax).coerceAtLeast(2)

    private fun IntRange.random(random: kotlin.random.Random): Int {
        if (first == last) return first
        return random.nextInt(first, last + 1)
    }
}
