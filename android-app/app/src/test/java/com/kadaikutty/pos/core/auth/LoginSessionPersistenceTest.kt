package com.kadaikutty.pos.core.auth

import com.kadaikutty.pos.core.database.BillingDatabase
import com.kadaikutty.pos.core.license.LicenseDao
import com.kadaikutty.pos.core.license.LicenseEntity
import com.kadaikutty.pos.core.network.BackendApiClient
import com.kadaikutty.pos.core.security.Permission
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/** What an online sign-in writes down on the device. */
class LoginSessionPersistenceTest {
    private val validUntil = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000

    private fun user(role: String, permissions: List<String>) = JSONObject()
        .put("userId", "u1").put("companyId", "c1").put("phone", "9876543210")
        .put("displayName", "Staff").put("role", role).put("permissions", JSONArray(permissions))

    private fun license() = JSONObject()
        .put("companyId", "c1").put("status", "ACTIVE_PAID").put("licenseType", "YEARLY")
        .put("validUntilEpochMs", validUntil).put("businessName", "Shop").put("ownerName", "Owner")

    private class Harness(existingLicense: LicenseEntity?) {
        val licenses: LicenseDao = mock(LicenseDao::class.java)
        val backend: BackendApiClient = mock(BackendApiClient::class.java)
        val repository: DefaultAuthRepository

        init {
            val database = mock(BillingDatabase::class.java)
            `when`(database.licenseDao()).thenReturn(licenses)
            `when`(database.userDao()).thenReturn(mock(UserDao::class.java))
            val security = mock(SessionSecurityManager::class.java)
            runBlocking {
                `when`(licenses.getLicense("c1")).thenReturn(existingLicense)
                `when`(security.registerSession()).thenReturn("session-1")
            }
            repository = DefaultAuthRepository(mock(SessionStore::class.java), mock(OfflineCredentialStore::class.java), OfflineCredentialVerifier(), database, backend, security)
        }

        fun login(response: JSONObject): LoginResult = runBlocking {
            `when`(backend.login(anyString(), anyString())).thenReturn(response)
            repository.loginOnline("9876543210", "123456".toCharArray())
        }

        fun savedLicense(): LicenseEntity = runBlocking {
            val captor = ArgumentCaptor.forClass(LicenseEntity::class.java)
            verify(licenses).saveLicense(captor.capture() ?: LicenseEntity(companyId = "unused"))
            captor.value
        }
    }

    @Test
    fun `a cashier given no permissions gets none, not every permission`() {
        val result = Harness(null).login(JSONObject().put("user", user("CASHIER", emptyList())).put("tokens", JSONObject().put("accessToken", "a")))
        assertTrue(result is LoginResult.Success)
        assertEquals(emptySet<Permission>(), (result as LoginResult.Success).session.permissions)
    }

    @Test
    fun `a cashier keeps exactly the permissions the owner gave`() {
        val result = Harness(null).login(JSONObject().put("user", user("CASHIER", listOf("SALE_CREATE"))).put("tokens", JSONObject().put("accessToken", "a")))
        assertEquals(setOf(Permission.SALE_CREATE), (result as LoginResult.Success).session.permissions)
    }

    @Test
    fun `sign-in stores the license validity it was given, so the shop is not locked out`() {
        val harness = Harness(null)
        harness.login(JSONObject().put("user", user("ADMIN", emptyList())).put("tokens", JSONObject().put("accessToken", "a")).put("license", license()))
        val saved = harness.savedLicense()
        assertEquals(validUntil, saved.validUntilEpochMs)
        assertEquals("ACTIVE_PAID", saved.licenseStatus)
        assertFalse(saved.isExpired)
    }

    @Test
    fun `sign-in without a license in the response keeps the stored validity`() {
        val stored = LicenseEntity(companyId = "c1", businessName = "Old", licenseStatus = "ACTIVE_PAID", validUntilEpochMs = validUntil)
        val harness = Harness(stored)
        harness.login(JSONObject().put("user", user("ADMIN", emptyList()).put("businessName", "Shop")).put("tokens", JSONObject().put("accessToken", "a")))
        val saved = harness.savedLicense()
        assertEquals(validUntil, saved.validUntilEpochMs)
        assertEquals("Shop", saved.businessName)
        assertFalse(saved.isExpired)
    }
}
