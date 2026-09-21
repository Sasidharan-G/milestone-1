package com.kadaikutty.pos.core.security

import com.kadaikutty.pos.core.auth.Session
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncWritePolicyTest {
    private fun session(role: String, vararg permissions: Permission) =
        Session(userId = "u", displayName = "U", permissions = permissions.toSet(), companyId = "c", role = role)

    @Test
    fun `a cashier who can bill cannot change products or categories`() {
        val cashier = session("CASHIER", Permission.SALE_CREATE, Permission.PRODUCT_VIEW, Permission.CATEGORY_VIEW)
        assertFalse(SyncWritePolicy.canEdit(cashier, "Product"))
        assertFalse(SyncWritePolicy.canEdit(cashier, "Category"))
        assertFalse(SyncWritePolicy.canCreate(cashier, "Product"))
        assertTrue(SyncWritePolicy.canCreate(cashier, "Customer"))
        assertTrue(SyncWritePolicy.canEdit(cashier, "Customer"))
        assertFalse(SyncWritePolicy.canEdit(cashier, "Supplier"))
    }

    @Test
    fun `edit permission allows editing and an administrator may do everything`() {
        assertTrue(SyncWritePolicy.canEdit(session("CASHIER", Permission.PRODUCT_EDIT), "Product"))
        assertTrue(SyncWritePolicy.canEdit(session("ADMIN"), "Expense"))
    }

    @Test
    fun `a view-only or deactivated account writes nothing`() {
        assertFalse(SyncWritePolicy.canCreate(session("CASHIER", Permission.SALE_VIEW, Permission.REPORT_SALES), "Customer"))
        assertFalse(SyncWritePolicy.canEdit(session("ADMIN", Permission.ACCOUNT_INACTIVE), "Product"))
        assertFalse(SyncWritePolicy.canEdit(null, "Product"))
    }
}
