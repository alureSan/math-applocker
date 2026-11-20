package com.mathapplocker.foss.math

import java.text.Normalizer
import java.util.Locale

/**
 * Text answers are compared leniently: trimmed, case-insensitive, accent-insensitive
 * and with repeated whitespace collapsed.
 */
object TextAnswerMatcher {

    fun matches(input: String, expected: String): Boolean =
        normalize(input) == normalize(expected)

    fun normalize(input: String): String {
        val decomposed = Normalizer.normalize(input.trim(), Normalizer.Form.NFD)
        return decomposed
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
    }
}
