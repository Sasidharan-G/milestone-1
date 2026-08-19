package com.company.billing

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.company.billing.core.ui.BillingApp
import com.company.billing.core.security.SecurityShield
import com.company.billing.core.security.BiometricAuthenticator
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Physical Security: Prevent screenshots and screen recording app-wide
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        // Runtime Security Check
        if (runSecurityChecks()) {
            return
        }

        setContent {
            var isAuthenticated by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                if (BiometricAuthenticator.isBiometricAvailable(this@MainActivity)) {
                    BiometricAuthenticator.authenticate(
                        activity = this@MainActivity,
                        onSuccess = { isAuthenticated = true },
                        onError = {
                            // Close app on cancel or failure
                            finishAffinity()
                        }
                    )
                } else {
                    // Fallback to password or direct access if biometrics are not set up on device
                    isAuthenticated = true
                }
            }

            if (isAuthenticated) {
                BillingApp()
            } else {
                // Secure black canvas to prevent pre-auth data exposure
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                )
            }
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
