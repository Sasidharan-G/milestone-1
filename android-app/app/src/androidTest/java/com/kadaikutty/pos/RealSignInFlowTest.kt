package com.kadaikutty.pos

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kadaikutty.pos.core.auth.AuthRepository
import com.kadaikutty.pos.core.auth.LoginResult
import com.kadaikutty.pos.core.auth.OfflineCredentialStore
import com.kadaikutty.pos.core.auth.SessionStore
import com.kadaikutty.pos.core.network.BackendApiClient
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * The app's whole sign-in, with the app's real dependency graph (encrypted tenant database, encrypted
 * session store, credential store, device-session registration) against a real copy of the server on
 * this PC. Covers what a shop does: register, sign in, delete the shop, register the same number again
 * and sign in again on the phone that still holds the old shop's leftovers.
 *
 * Skipped unless started with `-e localServer true` and the build points at 10.0.2.2, so it can never
 * touch the real backend. The local server must run with LOCAL_DEV_OTP_CODE=123456.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class RealSignInFlowTest {
    @get:Rule val hilt = HiltAndroidRule(this)

    @Inject lateinit var auth: AuthRepository
    @Inject lateinit var backend: BackendApiClient
    @Inject lateinit var sessions: SessionStore
    @Inject lateinit var credentials: OfflineCredentialStore

    private val pin = "123456"

    @Before fun setUp() {
        assumeTrue("start the local server and pass -e localServer true", InstrumentationRegistry.getArguments().getString("localServer") == "true")
        assumeTrue("only ever against a server on the emulator's host", BuildConfig.BACKEND_BASE_URL.startsWith("http://10.0.2.2"))
        hilt.inject()
    }

    /** The number of this test run: new each time so an earlier run's shop never interferes. */
    private val phone = "9" + (100_000_000L + (System.nanoTime() / 1000) % 899_999_999L).toString()

    private suspend fun register(): String {
        val requestId = backend.sendOtp(phone).getString("requestId")
        val proof = backend.verifyOtp(phone, "123456", requestId).getString("resetToken")
        return backend.registerMerchant(phone, "Test Owner", "Test Stores", pin, proof).getString("companyId")
    }

    private suspend fun signIn(): LoginResult = auth.loginOnline(phone, pin.toCharArray())

    @Test fun aNewShopRegistersAndSignsIn() = runBlocking {
        val companyId = register()

        val result = signIn()

        assertTrue("sign-in failed: ${(result as? LoginResult.Failure)?.message}", result is LoginResult.Success)
        val session = sessions.activeSession.first()
        assertNotNull(session)
        assertEquals(companyId, session!!.companyId)
        assertTrue("a device session was registered", !session.sessionToken.isNullOrBlank())
        assertNotNull("the offline credential was saved", credentials.getCredential(phone).first())
    }

    @Test fun theSameNumberCanRegisterAndSignInAgainAfterItsShopWasDeleted() = runBlocking {
        val firstCompany = register()
        val first = signIn()
        assertTrue("first sign-in failed: ${(first as? LoginResult.Failure)?.message}", first is LoginResult.Success)

        // The shop is deleted from the server (as Master Control or the owner's phone does). This phone only
        // finds out later; what it keeps is the old shop's database, its credential and its session.
        val token = sessions.activeSession.first()!!.accessToken!!
        backend.deleteAccount(token, pin)
        sessions.clear()
        Thread.sleep(21_000) // the server allows one verification code every 20 seconds per number

        val secondCompany = register()
        assertTrue("a new shop, not the old one", secondCompany != firstCompany)
        val second = signIn()

        assertTrue("sign-in after re-registering failed: ${(second as? LoginResult.Failure)?.message}", second is LoginResult.Success)
        assertEquals(secondCompany, sessions.activeSession.first()!!.companyId)
        assertTrue(!sessions.activeSession.first()!!.sessionToken.isNullOrBlank())
    }
}
