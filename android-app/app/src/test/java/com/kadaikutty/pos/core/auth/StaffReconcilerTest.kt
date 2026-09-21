package com.kadaikutty.pos.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StaffReconcilerTest {

    private fun user(id: String, username: String, displayName: String = "Name", permissions: String = "SALE_VIEW", companyId: String = "company_1") =
        UserEntity(
            id = id, username = username, displayName = displayName,
            permissions = permissions, companyId = companyId, role = "CASHIER",
            lastOnlineVerifiedAt = 0L
        )

    private fun server(userId: String, phone: String, displayName: String = "Name", permissions: String = "SALE_VIEW", status: String = "ACTIVE") =
        ServerStaffRecord(userId = userId, phone = phone, displayName = displayName, role = "CASHIER", permissions = permissions, status = status)

    @Test
    fun `matching local and server rows produce no actions`() {
        val local = listOf(user(id = "u1", username = "9876543210"))
        val server = listOf(server(userId = "u1", phone = "9876543210"))

        val actions = StaffReconciler.plan(local, server, adminUserId = "admin", companyId = "company_1")

        assertTrue(actions.isEmpty())
    }

    @Test
    fun `local row with a diverged id is rekeyed to the server id`() {
        val local = listOf(user(id = "local_uuid_123", username = "9876543210"))
        val server = listOf(server(userId = "server_canonical_1", phone = "9876543210"))

        val actions = StaffReconciler.plan(local, server, adminUserId = "admin", companyId = "company_1")

        assertEquals(1, actions.size)
        val action = actions.single() as StaffReconciliationAction.Rekey
        assertEquals("local_uuid_123", action.local.id)
        assertEquals("server_canonical_1", action.server.userId)
    }

    @Test
    fun `local row with no matching active server account is deleted as an orphan`() {
        val local = listOf(user(id = "u1", username = "9876543210"))
        val server = emptyList<ServerStaffRecord>()

        val actions = StaffReconciler.plan(local, server, adminUserId = "admin", companyId = "company_1")

        assertEquals(1, actions.size)
        assertEquals("u1", (actions.single() as StaffReconciliationAction.Delete).localId)
    }

    @Test
    fun `a deactivated server account is treated the same as no account, deleting the local row`() {
        val local = listOf(user(id = "u1", username = "9876543210"))
        val server = listOf(server(userId = "u1", phone = "9876543210", status = "REJECTED"))

        val actions = StaffReconciler.plan(local, server, adminUserId = "admin", companyId = "company_1")

        assertEquals(1, actions.size)
        assertTrue(actions.single() is StaffReconciliationAction.Delete)
    }

    @Test
    fun `server account with no local mirror is inserted`() {
        val local = emptyList<UserEntity>()
        val server = listOf(server(userId = "u1", phone = "9876543210", displayName = "New Cashier"))

        val actions = StaffReconciler.plan(local, server, adminUserId = "admin", companyId = "company_1")

        assertEquals(1, actions.size)
        val action = actions.single() as StaffReconciliationAction.InsertMissing
        assertEquals("u1", action.server.userId)
        assertEquals("company_1", action.companyId)
    }

    @Test
    fun `matching id but drifted display name or permissions updates fields only`() {
        val local = listOf(user(id = "u1", username = "9876543210", displayName = "Old Name", permissions = "SALE_VIEW"))
        val server = listOf(server(userId = "u1", phone = "9876543210", displayName = "New Name", permissions = "SALE_VIEW,PRODUCT_VIEW"))

        val actions = StaffReconciler.plan(local, server, adminUserId = "admin", companyId = "company_1")

        assertEquals(1, actions.size)
        val action = actions.single() as StaffReconciliationAction.UpdateFields
        assertEquals("New Name", action.server.displayName)
    }

    @Test
    fun `the calling admin's own row is never touched even if it would otherwise look orphaned`() {
        val local = listOf(user(id = "admin", username = "9999999999"))
        val server = emptyList<ServerStaffRecord>()

        val actions = StaffReconciler.plan(local, server, adminUserId = "admin", companyId = "company_1")

        assertTrue(actions.isEmpty())
    }

    @Test
    fun `phone numbers are normalized before matching, ignoring country code and formatting`() {
        val local = listOf(user(id = "u1", username = "+91 98765-43210"))
        val server = listOf(server(userId = "u1", phone = "919876543210"))

        val actions = StaffReconciler.plan(local, server, adminUserId = "admin", companyId = "company_1")

        assertTrue(actions.isEmpty())
    }
}
