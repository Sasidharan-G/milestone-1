package com.kadaikutty.pos.core.auth

import com.kadaikutty.pos.core.database.BillingDatabase
import com.kadaikutty.pos.core.license.LicenseDao
import com.kadaikutty.pos.core.license.LicenseEntity
import com.kadaikutty.pos.core.network.BackendApiClient
import com.kadaikutty.pos.core.security.Permission
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
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
        val sessions: SessionStore = mock(SessionStore::class.java)
        val credentials: OfflineCredentialStore = mock(OfflineCredentialStore::class.java)
        val security: SessionSecurityManager = mock(SessionSecurityManager::class.java)
        val repository: DefaultAuthRepository

        init {
            val database = mock(BillingDatabase::class.java)
            `when`(database.licenseDao()).thenReturn(licenses)
            `when`(database.userDao()).thenReturn(mock(UserDao::class.java))
            runBlocking {
                `when`(licenses.getLicense("c1")).thenReturn(existingLicense)
                `when`(security.registerSession(anyString())).thenReturn("session-1")
            }
            repository = DefaultAuthRepository(sessions, credentials, OfflineCredentialVerifier(), database, backend, security)
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

    private val signedIn get() = JSONObject().put("user", user("ADMIN", emptyList())).put("tokens", JSONObject().put("accessToken", "a"))

    // Mockito matchers return null, which Kotlin refuses for a non-null parameter; hand back a dummy instead.
    private fun anyCredential(): OfflineCredential =
        org.mockito.ArgumentMatchers.any(OfflineCredential::class.java) ?: OfflineCredential("", "", "", ByteArray(0), ByteArray(0), "")
    private fun anySession(): Session =
        org.mockito.ArgumentMatchers.any(Session::class.java) ?: Session(userId = "", displayName = "", permissions = emptySet(), companyId = "", role = "")

    @Test
    fun `the session becomes visible only after the offline credential and the device session exist`() = runBlocking {
        val harness = Harness(null)
        assertTrue(harness.login(signedIn) is LoginResult.Success)
        val order = inOrder(harness.credentials, harness.security, harness.sessions)
        order.verify(harness.credentials).save(anyCredential())
        order.verify(harness.security).registerSession(anyString())
        val saved = ArgumentCaptor.forClass(Session::class.java)
        order.verify(harness.sessions).save(saved.capture() ?: Session(userId = "", displayName = "", permissions = emptySet(), companyId = "", role = ""))
        assertEquals("session-1", saved.value.sessionToken)
    }

    @Test
    fun `sign-in still completes when its caller is cancelled right after the server said yes`() = runBlocking {
        // The sign-in screen leaves (and cancels its scope) as soon as a session appears in the store.
        // Cancelling here, before anything is written, must not cost the device its offline credential.
        val harness = Harness(null)
        lateinit var caller: kotlinx.coroutines.Deferred<LoginResult>
        `when`(harness.backend.login(anyString(), anyString())).thenAnswer { caller.cancel(); signedIn }
        caller = async { harness.repository.loginOnline("9876543210", "123456".toCharArray()) }
        runCatching { caller.await() }
        verify(harness.credentials).save(anyCredential())
        verify(harness.sessions).save(anySession())
    }

    @Test
    fun `a failed device session leaves no session behind but keeps the offline credential`() = runBlocking {
        val harness = Harness(null)
        `when`(harness.security.registerSession(anyString())).thenAnswer { throw java.io.IOException("offline") }
        val result = harness.login(signedIn)
        assertTrue(result is LoginResult.Failure && result.canTryOffline)
        verify(harness.sessions, never()).save(anySession())
        verify(harness.credentials).save(anyCredential())
    }
}
