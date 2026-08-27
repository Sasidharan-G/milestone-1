package com.kadaikutty.pos.core.auth

object EmailValidator {
    // List of common disposable/fake email domains to block
    private val blockedDomains = setOf(
        "mailinator.com", "10minutemail.com", "yopmail.com", "guerrillamail.com",
        "temp-mail.org", "fakeinbox.com", "throwawaymail.com", "tempmail.com",
        "dropmail.me", "nada.email", "getnada.com", "dispostable.com",
        "sharklasers.com", "maildrop.cc", "trashmail.com", "anonaddy.com",
        "maildim.com", "mohmal.com", "tempmail.ninja"
    )

    fun isValidEmail(email: String): Boolean {
        val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$".toRegex()
        if (!emailRegex.matches(email)) {
            return false
        }
        
        val domain = email.substringAfterLast("@").lowercase()
        return !blockedDomains.contains(domain)
    }
}
