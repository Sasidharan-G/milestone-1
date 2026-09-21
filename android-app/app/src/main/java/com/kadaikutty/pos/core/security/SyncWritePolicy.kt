package com.kadaikutty.pos.core.security

import com.kadaikutty.pos.core.auth.Session

/**
 * Who may create or change which records. The server applies the same rule to every pushed write
 * (permissionDenial in server/src/providers/sync/syncRules.ts), so an edit this device allows is
 * never one the cloud refuses, and one it refuses cannot be forced through the API.
 *
 * The server is looser on creation than this: sync itself restores deleted records that are still
 * in use and creates placeholders from any device, so the server lets any staff member with a
 * writing permission create. Here, a person creating a record by hand needs the specific one.
 */
object SyncWritePolicy {
    private val WRITER = setOf(
        Permission.SALE_CREATE, Permission.PURCHASE_CREATE, Permission.CATEGORY_CREATE,
        Permission.CATEGORY_EDIT, Permission.PRODUCT_CREATE, Permission.PRODUCT_EDIT
    )

    private val CREATE = mapOf(
        "Category" to Permission.CATEGORY_CREATE, "Product" to Permission.PRODUCT_CREATE,
        "Customer" to Permission.SALE_CREATE, "Supplier" to Permission.PURCHASE_CREATE, "Expense" to Permission.PURCHASE_CREATE
    )

    /** Must match LIVE_EDIT_REQUIREMENT on the server. */
    private val EDIT = mapOf(
        "Category" to Permission.CATEGORY_EDIT, "Product" to Permission.PRODUCT_EDIT,
        "Customer" to Permission.SALE_CREATE, "Supplier" to Permission.PURCHASE_CREATE, "Expense" to Permission.PURCHASE_CREATE
    )

    fun canCreate(session: Session?, entityType: String): Boolean = allowed(session, CREATE[entityType])

    fun canEdit(session: Session?, entityType: String): Boolean = allowed(session, EDIT[entityType])

    private fun allowed(session: Session?, needed: Permission?): Boolean {
        if (session == null || Permission.ACCOUNT_INACTIVE in session.permissions) return false
        if (session.role == "ADMIN" || session.role == "SUPER_ADMIN") return true
        if (session.permissions.none { it in WRITER }) return false
        return needed == null || needed in session.permissions
    }

    fun requireCreate(session: Session?, entityType: String) {
        if (!canCreate(session, entityType)) throw SecurityException("You don't have permission to add a ${label(entityType)}")
    }

    fun requireEdit(session: Session?, entityType: String) {
        if (!canEdit(session, entityType)) throw SecurityException("You don't have permission to change or delete a ${label(entityType)}")
    }

    private fun label(entityType: String) = entityType.lowercase()
}
