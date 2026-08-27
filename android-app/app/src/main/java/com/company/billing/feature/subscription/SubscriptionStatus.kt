package com.company.billing.feature.subscription

data class SubscriptionStatus(
    val companyId: String,
    val planName: String,
    val status: String, // "trialing", "active", "past_due", "expired"
    val daysLeft: Int
)

val mockSubscriptionStatus = SubscriptionStatus(
    companyId = "company-123",
    planName = "free_trial",
    status = "expired", // Set to expired to show the Paywall for testing
    daysLeft = 0
)
