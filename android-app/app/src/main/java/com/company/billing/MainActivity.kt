package com.company.billing

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.compose.runtime.*
import com.company.billing.core.preferences.AppPreferences
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.company.billing.core.ui.BillingApp
import com.company.billing.core.security.SecurityShield
import com.company.billing.core.security.BiometricAuthenticator
import com.company.billing.core.ui.components.CustomToastOverlay
import com.company.billing.core.ui.components.ToastManager
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import com.razorpay.PaymentResultWithDataListener
import com.razorpay.PaymentData
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity(), PaymentResultWithDataListener {

    @Inject lateinit var appPreferences: AppPreferences

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

        val isRecoveryFlow = intent?.dataString?.contains("type=recovery") == true

        setContent {
            Box(modifier = Modifier.fillMaxSize()) {
                BillingApp(isRecoveryFlow = isRecoveryFlow)
                CustomToastOverlay(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp))
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

    override fun onPaymentSuccess(razorpayPaymentID: String?, paymentData: PaymentData?) {
        ToastManager.showSuccess("Payment Successful! ID: $razorpayPaymentID")
    }

    override fun onPaymentError(code: Int, response: String?, paymentData: PaymentData?) {
        ToastManager.showError("Payment Failed: $response")
    }
}