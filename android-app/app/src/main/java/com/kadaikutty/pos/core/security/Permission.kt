package com.kadaikutty.pos.core.security

enum class Permission {
    USER_MANAGE, CATEGORY_VIEW, CATEGORY_CREATE, CATEGORY_EDIT,
    PRODUCT_VIEW, PRODUCT_CREATE, PRODUCT_EDIT,
    SALE_CREATE, SALE_VIEW, PURCHASE_CREATE, PURCHASE_VIEW,
    REPORT_SALES, REPORT_STOCK, REPORT_PROFIT, BACKUP_CREATE,
    SETTINGS_VIEW, SETTINGS_EDIT, ACCOUNT_INACTIVE, REQUIRE_PASSWORD_CHANGE,
    PENDING_MASTER_APPROVAL;

    companion object {
        val ALL_ACTIVE: Set<Permission> = entries.filter { 
            it != ACCOUNT_INACTIVE && it != REQUIRE_PASSWORD_CHANGE && it != PENDING_MASTER_APPROVAL
        }.toSet()
    }
}

interface AuthorizationService { fun can(permission: Permission): Boolean }
class PermissionAuthorizationService(private val granted: Set<Permission>) : AuthorizationService {
    override fun can(permission: Permission): Boolean = permission in granted
}
