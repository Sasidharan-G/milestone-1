package com.kadaikutty.pos.core.common

import java.math.BigDecimal
import java.math.BigInteger

object CheckoutMath {
    const val MAX_AMOUNT = 100_000_000_000L
    const val MAX_QUANTITY = 1_000_000_000L
    fun parseAmount(input: String): Long? = try {
        val raw = input.trim()
        val text = if (raw.startsWith(".")) "0$raw" else raw
        if (text.isEmpty()) 0L else if (!Regex("[0-9]+(\\.[0-9]{0,2})?").matches(text)) null
        else BigDecimal(text).movePointRight(2).longValueExact().takeIf { it in 0..MAX_AMOUNT }
    } catch (_: Exception) { null }
    /** (rupees * 100).toLong() truncates: the nearest Double to a value like 19.99 is
     *  slightly below it, so multiplying by 100 and truncating silently drops a paisa. Round instead. */
    fun rupeesToMinorUnits(rupees: Double): Long = Math.round(rupees * 100)
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
            val share = BigInteger.valueOf(amount).multiply(BigInteger.valueOf(cumulative)).divide(BigInteger.valueOf(sum))
            // Not longValueExact(): that is API 31 and minSdk is 26, so on Android 8-11 it would
            // throw NoSuchMethodError at checkout. bitLength() gives the same overflow guarantee
            // for a non-negative value on every release we support.
            require(share.bitLength() < 64) { "Allocation overflowed" }
            val next = share.toLong()
            (next - previous).also { previous = next }
        }
    }
}
