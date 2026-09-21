package com.kadaikutty.pos.core.common

/**
 * One set of rules for what every text field accepts, so a phone number, amount or GSTIN is
 * checked the same way on every screen.
 *
 * The filters run in onValueChange: they drop what can never be valid (a letter in a phone
 * number, a third decimal in a price) as it is typed. The checks run on save and return the
 * message to show, or null when the value is fine. An optional field that is left blank passes.
 */
object InputRules {
    const val PHONE_LENGTH = 10
    const val PIN_LENGTH = 6
    const val OTP_LENGTH = 6
    const val GSTIN_LENGTH = 15
    const val NAME_MAX = 60
    const val TEXT_MAX = 500
    const val EMAIL_MAX = 100
    const val BARCODE_MAX = 48
    private const val MONEY_WHOLE_DIGITS = 9
    private const val QUANTITY_WHOLE_DIGITS = 7

    private val PHONE = Regex("^[6-9][0-9]{9}$")
    // State code, PAN (5 letters, 4 digits, 1 letter), entity number, the fixed Z, check character.
    private val GSTIN = Regex("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$")
    private val EMAIL = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    // ---- Filters (onValueChange) ----

    fun phone(input: String): String = input.filter(Char::isDigit).take(PHONE_LENGTH)

    fun digits(input: String, maxLength: Int): String = input.filter(Char::isDigit).take(maxLength)

    fun gstin(input: String): String = input.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }.take(GSTIN_LENGTH)

    fun email(input: String): String = input.filterNot(Char::isWhitespace).take(EMAIL_MAX)

    /** A name or label: one line, bounded, no leading space. */
    fun name(input: String, maxLength: Int = NAME_MAX): String =
        input.replace('\n', ' ').trimStart().take(maxLength)

    /** Free text such as an address or a note: one line, bounded. */
    fun text(input: String, maxLength: Int = TEXT_MAX): String = input.replace('\n', ' ').take(maxLength)

    fun barcode(input: String): String = input.filter { it.isLetterOrDigit() || it == '-' }.take(BARCODE_MAX)

    /** Rupees: digits, one decimal point, at most two decimals. */
    fun money(input: String): String = decimal(input, MONEY_WHOLE_DIGITS, 2)

    /** A quantity: whole numbers for pieces, up to three decimals for KG and litre. */
    fun quantity(input: String, allowDecimals: Boolean): String =
        if (allowDecimals) decimal(input, QUANTITY_WHOLE_DIGITS, 3) else digits(input.substringBefore('.'), QUANTITY_WHOLE_DIGITS)

    private fun decimal(input: String, wholeDigits: Int, decimals: Int): String {
        val cleaned = input.filter { it.isDigit() || it == '.' }
        val point = cleaned.indexOf('.')
        if (point < 0) return cleaned.take(wholeDigits)
        val whole = cleaned.substring(0, point).take(wholeDigits)
        val fraction = cleaned.substring(point + 1).filter(Char::isDigit).take(decimals)
        return "$whole.$fraction"
    }

    // ---- Checks (on save) ----

    fun checkName(value: String, label: String = "Name"): String? {
        val trimmed = value.trim()
        return when {
            trimmed.isEmpty() -> "$label is required"
            trimmed.length < 2 -> "$label is too short"
            trimmed.none(Char::isLetter) -> "$label must contain letters"
            else -> null
        }
    }

    fun checkPhone(value: String, required: Boolean = false): String? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return if (required) "Mobile number is required" else null
        return if (PHONE.matches(trimmed)) null else "Enter a valid 10-digit mobile number starting with 6, 7, 8 or 9"
    }

    fun checkGstin(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.length != GSTIN_LENGTH) return "GST number must be exactly 15 characters"
        return if (GSTIN.matches(trimmed)) null else "GST number format is invalid (example: 33ABCDE1234F1Z5)"
    }

    fun checkEmail(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null
        return if (EMAIL.matches(trimmed)) null else "Enter a valid email address"
    }

    fun checkPin(value: String, length: Int = PIN_LENGTH, label: String = "PIN"): String? =
        if (value.length == length && value.all(Char::isDigit)) null else "$label must be exactly $length digits"

    /** An amount of rupees. [allowZero] for things like an opening balance that may be nothing. */
    fun checkMoney(value: String, label: String = "Amount", required: Boolean = true, allowZero: Boolean = false): String? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return if (required) "$label is required" else null
        val amount = trimmed.toBigDecimalOrNull() ?: return "$label is not a valid amount"
        return when {
            amount.signum() < 0 -> "$label cannot be negative"
            amount.signum() == 0 && !allowZero -> "$label must be more than zero"
            else -> null
        }
    }

    fun checkQuantity(value: String, label: String = "Quantity"): String? {
        val amount = value.trim().toBigDecimalOrNull() ?: return "$label is required"
        return if (amount.signum() > 0) null else "$label must be more than zero"
    }

    fun checkWholeNumber(value: String, label: String, min: Long, max: Long, required: Boolean = true): String? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return if (required) "$label is required" else null
        val number = trimmed.toLongOrNull() ?: return "$label must be a whole number"
        return if (number in min..max) null else "$label must be between $min and $max"
    }

    /** First failing message among [checks], or null when all pass. */
    fun firstError(vararg checks: String?): String? = checks.firstOrNull { it != null }
}
