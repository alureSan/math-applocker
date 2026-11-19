package com.mathapplocker.foss.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {

    @Test
    fun `verifies the correct pin`() {
        val stored = PinHasher.create("1234", iterations = 1_000)
        assertTrue(
            PinHasher.verify(
                pin = "1234",
                saltBase64 = stored.saltBase64,
                hashBase64 = stored.hashBase64,
                iterations = stored.iterations,
            ),
        )
    }

    @Test
    fun `rejects a wrong pin`() {
        val stored = PinHasher.create("1234", iterations = 1_000)
        assertFalse(
            PinHasher.verify(
                pin = "4321",
                saltBase64 = stored.saltBase64,
                hashBase64 = stored.hashBase64,
                iterations = stored.iterations,
            ),
        )
    }

    @Test
    fun `every pin gets a fresh salt`() {
        val first = PinHasher.create("0000", iterations = 1_000)
        val second = PinHasher.create("0000", iterations = 1_000)
        assertTrue(first.saltBase64 != second.saltBase64)
        assertTrue(first.hashBase64 != second.hashBase64)
    }

    @Test
    fun `missing hash is rejected`() {
        assertFalse(PinHasher.verify("1234", saltBase64 = null, hashBase64 = null, iterations = 1_000))
    }
}
