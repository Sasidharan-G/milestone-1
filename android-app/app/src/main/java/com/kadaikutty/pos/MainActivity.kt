package com.kadaikutty.pos

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.kadaikutty.pos.core.ui.BillingApp
import com.kadaikutty.pos.core.security.SecurityShield
import com.kadaikutty.pos.core.security.BiometricAuthenticator
import com.kadaikutty.pos.BuildConfig
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject


@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            // Note: Supabase composeAuth is removed. Firebase Google SignIn to be implemented later.
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Runtime Security Check
        if (runSecurityChecks()) {
            return
        }

        setContent {
            BillingApp()
        }
    }

    private fun runSecurityChecks(): Boolean {
        // Allow emulators / debug builds during development phase
        if (!BuildConfig.DEBUG) {
            if (SecurityShield.isDeviceRooted(this)) {
                showSecurityFailureAndExit("Security Alert", "This application cannot execute on rooted devices.")
                return true
            }
            if (SecurityShield.isDebuggerAttached()) {
                showSecurityFailureAndExit("Security Alert", "Active debugging tools detected. Session terminated.")
                return true
            }
        }

        if (SecurityShield.isVpnOrProxyActive(this)) {
            showSecurityFailureAndExit("Access Denied", "Connections via Proxy or VPN are restricted.")
            return true
        }

        if (!SecurityShield.verifyBinaryIntegrity(this)) {
            showSecurityFailureAndExit("Integrity Failure", "App binary verification failed. Reinstall from official source.")
            return true
        }

        return false
    }

    private fun showSecurityFailureAndExit(title: String, message: String) {
        android.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("Close") { _, _ ->
                finishAffinity()
            }
            .show()
    }
}
