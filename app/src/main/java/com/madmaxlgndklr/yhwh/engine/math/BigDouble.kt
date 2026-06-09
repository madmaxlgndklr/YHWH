package com.madmaxlgndklr.yhwh.engine.math

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * Fixed-precision large number representation: mantissa × 10^exponent.
 * Mantissa is always normalized to [1.0, 10.0) except for ZERO.
 * Supports only non-negative values (resource amounts never go below zero).
 */
@Serializable
data class BigDouble(val mantissa: Double, val exponent: Int) : Comparable<BigDouble> {

    companion object {
        val ZERO = BigDouble(0.0, 0)
        val ONE = BigDouble(1.0, 0)

        fun of(value: Double): BigDouble {
            if (!value.isFinite() || value == 0.0) return ZERO
            val exp = floor(log10(abs(value))).toInt()
            val mant = value / 10.0.pow(exp)
            return normalize(BigDouble(mant, exp))
        }

        fun normalize(bd: BigDouble): BigDouble {
            if (bd.mantissa == 0.0 || !bd.mantissa.isFinite()) return ZERO
            var m = bd.mantissa
            var e = bd.exponent
            while (abs(m) >= 10.0) { m /= 10.0; e++ }
            while (abs(m) < 1.0) { m *= 10.0; e-- }
            return BigDouble(m, e)
        }
    }

    operator fun plus(other: BigDouble): BigDouble {
        if (this == ZERO) return other
        if (other == ZERO) return this
        val expDiff = exponent - other.exponent
        return when {
            expDiff > 15 -> this
            expDiff < -15 -> other
            else -> normalize(
                BigDouble(mantissa + other.mantissa * 10.0.pow(-expDiff), exponent)
            )
        }
    }

    operator fun minus(other: BigDouble): BigDouble {
        if (other == ZERO) return this
        if (other >= this) return ZERO
        val expDiff = exponent - other.exponent
        return if (expDiff > 15) this
        else normalize(BigDouble(mantissa - other.mantissa * 10.0.pow(-expDiff), exponent))
    }

    operator fun times(other: BigDouble): BigDouble {
        if (this == ZERO || other == ZERO) return ZERO
        return normalize(BigDouble(mantissa * other.mantissa, exponent + other.exponent))
    }

    operator fun div(other: BigDouble): BigDouble {
        require(other != ZERO) { "BigDouble division by zero" }
        return normalize(BigDouble(mantissa / other.mantissa, exponent - other.exponent))
    }

    override fun compareTo(other: BigDouble): Int {
        if (exponent != other.exponent) return exponent.compareTo(other.exponent)
        return mantissa.compareTo(other.mantissa)
    }

    fun toDouble(): Double = mantissa * 10.0.pow(exponent)

    fun toDisplayString(): String = when {
        exponent < 3 -> "%.2f".format(toDouble())
        exponent < 6 -> "%.2fK".format(mantissa * 10.0.pow(exponent - 3))
        exponent < 9 -> "%.2fM".format(mantissa * 10.0.pow(exponent - 6))
        exponent < 12 -> "%.2fB".format(mantissa * 10.0.pow(exponent - 9))
        exponent < 15 -> "%.2fT".format(mantissa * 10.0.pow(exponent - 12))
        else -> "%.2fe%d".format(mantissa, exponent)
    }
}
