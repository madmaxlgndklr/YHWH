package com.madmaxlgndklr.yhwh.engine.math

import org.junit.Assert.*
import org.junit.Test

class BigDoubleTest {

    @Test fun `of constructs from double`() {
        val bd = BigDouble.of(1500.0)
        assertEquals(1.5, bd.mantissa, 1e-9)
        assertEquals(3, bd.exponent)
    }

    @Test fun `of constructs zero`() {
        assertEquals(BigDouble.ZERO, BigDouble.of(0.0))
    }

    @Test fun `addition same exponent`() {
        val a = BigDouble.of(1.5e10)
        val b = BigDouble.of(2.5e10)
        val result = a + b
        assertEquals(BigDouble.of(4.0e10).mantissa, result.mantissa, 1e-6)
        assertEquals(10, result.exponent)
    }

    @Test fun `addition different exponent`() {
        val a = BigDouble.of(1.0e10)
        val b = BigDouble.of(1.0e3)
        val result = a + b
        // 1.0e10 dominates; 1e3 is negligible at this scale
        assertEquals(10, result.exponent)
    }

    @Test fun `subtraction result clamps to zero`() {
        val a = BigDouble.of(5.0)
        val b = BigDouble.of(10.0)
        assertEquals(BigDouble.ZERO, a - b)
    }

    @Test fun `subtraction normal case`() {
        val a = BigDouble.of(10.0)
        val b = BigDouble.of(3.0)
        val result = a - b
        assertEquals(7.0, result.toDouble(), 1e-6)
    }

    @Test fun `multiplication`() {
        val a = BigDouble.of(2.0e5)
        val b = BigDouble.of(3.0e5)
        val result = a * b
        assertEquals(6.0, result.mantissa, 1e-9)
        assertEquals(10, result.exponent)
    }

    @Test fun `division`() {
        val a = BigDouble.of(6.0e10)
        val b = BigDouble.of(2.0e5)
        val result = a / b
        assertEquals(3.0, result.mantissa, 1e-9)
        assertEquals(5, result.exponent)
    }

    @Test fun `compareTo larger exponent wins`() {
        val a = BigDouble.of(1.0e10)
        val b = BigDouble.of(9.99e9)
        assertTrue(a > b)
    }

    @Test fun `compareTo same exponent uses mantissa`() {
        val a = BigDouble.of(2.5e5)
        val b = BigDouble.of(1.5e5)
        assertTrue(a > b)
    }

    @Test fun `toDisplayString under 1000`() {
        assertEquals("500.00", BigDouble.of(500.0).toDisplayString())
    }

    @Test fun `toDisplayString thousands`() {
        assertEquals("1.50K", BigDouble.of(1500.0).toDisplayString())
    }

    @Test fun `toDisplayString millions`() {
        assertEquals("2.50M", BigDouble.of(2_500_000.0).toDisplayString())
    }

    @Test fun `toDisplayString scientific`() {
        val bd = BigDouble.of(1.23e45)
        assertEquals("1.23e45", bd.toDisplayString())
    }
}
