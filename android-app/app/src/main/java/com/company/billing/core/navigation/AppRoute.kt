package com.company.billing.core.navigation

sealed class AppRoute(val path: String) {
    data object Login : AppRoute("login")
    data object Register : AppRoute("register")
    data object Home : AppRoute("home")
    data object Masters : AppRoute("masters")
    data object Billing : AppRoute("billing")
    data object Purchases : AppRoute("purchases")
    data object Reports : AppRoute("reports")
    data object Settings : AppRoute("settings")
    data object SetNewPassword : AppRoute("set_new_password")
}

