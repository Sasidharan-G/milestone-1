package com.company.billing.core.security

enum class Permission {
    USER_MANAGE, CATEGORY_VIEW, CATEGORY_CREATE, CATEGORY_EDIT,
    PRODUCT_VIEW, PRODUCT_CREATE, PRODUCT_EDIT,
    SALE_CREATE, SALE_VIEW, PURCHASE_CREATE, PURCHASE_VIEW,
    REPORT_SALES, REPORT_STOCK, REPORT_PROFIT, BACKUP_CREATE,
    SETTINGS_VIEW, SETTINGS_EDIT,
}

interface AuthorizationService { fun can(permission: Permission): Boolean }
class PermissionAuthorizationService(private val granted: Set<Permission>) : AuthorizationService {
    override fun can(permission: Permission): Boolean = permission in granted
}
