package com.kadaikutty.pos.core.common

import java.math.BigDecimal
import java.math.BigInteger

object CheckoutMath {
    const val MAX_AMOUNT = 100_000_000_000L
    const val MAX_QUANTITY = 1_000_000_000L
    fun parseAmount(input: String): Long? = try {
        val text = input.trim()
        if (text.isEmpty()) 0L else if (!Regex("[0-9]+(\\.[0-9]{0,2})?").matches(text)) null
        else BigDecimal(text).movePointRight(2).longValueExact().takeIf { it in 0..MAX_AMOUNT }
    } catch (_: Exception) { null }
    fun lineTotal(price: Long, quantity: Long, unit: String): Long {
        require(price in 0..MAX_AMOUNT && quantity in 1..MAX_QUANTITY) { "Price or quantity is outside the allowed range" }
        val result = BigInteger.valueOf(price).multiply(BigInteger.valueOf(quantity)).divide(BigInteger.valueOf(if (unit == "KG" || unit == "LITER") 1000 else 1))
        require(result <= BigInteger.valueOf(MAX_AMOUNT)) { "Line amount exceeds the supported limit" }
        return result.toLong()
    }
    data class Payment(val cash: Long, val upi: Long, val credit: Long, val change: Long)
    fun payment(total: Long, cash: Long, upi: Long, allowCredit: Boolean): Payment {
        require(total in 0..MAX_AMOUNT && cash in 0..MAX_AMOUNT && upi in 0..MAX_AMOUNT) { "Enter a valid non-negative payment amount" }
        require(upi <= total) { "UPI amount cannot exceed the payable total" }
        val change = maxOf(0, Math.addExact(cash, upi) - total)
        val netCash = cash - change
        val credit = total - netCash - upi
        require(allowCredit || credit == 0L) { "Select a registered customer for credit" }
        return Payment(netCash, upi, credit, change)
    }
    fun validate(total: Long, cash: Long, upi: Long, credit: Long) {
        require(listOf(total, cash, upi, credit).all { it in 0..MAX_AMOUNT }) { "Invalid payment amount" }
        require(Math.addExact(Math.addExact(cash, upi), credit) == total) { "Payment amounts must equal the payable total" }
    }
    /** Cumulative allocation keeps every paise, including zero-weight lines. */
    fun allocate(amount: Long, weights: List<Long>): List<Long> {
        require(amount >= 0 && weights.all { it >= 0 })
        val sum = weights.fold(0L, Math::addExact)
        require(sum > 0 || amount == 0L)
        if (sum == 0L) return weights.map { 0L }
        var cumulative = 0L
        var previous = 0L
        return weights.map {
            cumulative = Math.addExact(cumulative, it)
            val next = BigInteger.valueOf(amount).multiply(BigInteger.valueOf(cumulative)).divide(BigInteger.valueOf(sum)).longValueExact()
            (next - previous).also { previous = next }
        }
    }
}
