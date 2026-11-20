package com.mathapplocker.foss.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextAnswerMatcherTest {

    @Test
    fun `ignores case and surrounding whitespace`() {
        assertTrue(TextAnswerMatcher.matches("  pArIs  ", "Paris"))
    }

    @Test
    fun `ignores accents`() {
        assertTrue(TextAnswerMatcher.matches("Bogota", "Bogotá"))
        assertTrue(TextAnswerMatcher.matches("el nino", "El Niño"))
    }

    @Test
    fun `collapses repeated spaces`() {
        assertTrue(TextAnswerMatcher.matches("New   York", "New York"))
    }

    @Test
    fun `rejects a different answer`() {
        assertFalse(TextAnswerMatcher.matches("London", "Paris"))
    }

    @Test
    fun `normalizes consistently`() {
        assertEquals("el nino", TextAnswerMatcher.normalize("  El   Niño "))
    }
}
