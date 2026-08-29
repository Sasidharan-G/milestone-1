package com.kadaikutty.pos.feature.subscription

data class SubscriptionStatus(
    val companyId: String,
    val planId: String, // e.g. "free_trial", "monthly", "yearly"
    val status: String, // "active", "expired"
    val expiresAt: Long // Epoch milliseconds
) {
    val isExpired: Boolean
        get() = System.currentTimeMillis() > expiresAt
}

val mockSubscriptionStatus = SubscriptionStatus(
    companyId = "mock-company",
    planId = "free_trial",
    status = "active",
    expiresAt = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000) // 30 days from now
)
