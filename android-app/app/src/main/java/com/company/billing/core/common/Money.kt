package com.company.billing.core.common

@JvmInline
value class Money(val minorUnits: Long) {
    operator fun plus(other: Money) = Money(Math.addExact(minorUnits, other.minorUnits))
    operator fun minus(other: Money) = Money(Math.subtractExact(minorUnits, other.minorUnits))
    operator fun times(quantity: Long) = Money(Math.multiplyExact(minorUnits, quantity))
    override fun toString(): String = "₹" + java.lang.String.format(java.util.Locale.US, "%.2f", minorUnits / 100.0)
    companion object { val Zero = Money(0) }
}

