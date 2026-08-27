package com.kadaikutty.pos

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.compose.runtime.*
import com.kadaikutty.pos.core.preferences.AppPreferences
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.kadaikutty.pos.core.ui.BillingApp
import com.kadaikutty.pos.core.security.SecurityShield
import com.kadaikutty.pos.core.security.BiometricAuthenticator
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.razorpay.PaymentResultWithDataListener

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
            BillingApp(isRecoveryFlow = isRecoveryFlow)
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

    override fun onPaymentSuccess(razorpayPaymentID: String?, paymentData: com.razorpay.PaymentData?) {
        android.widget.Toast.makeText(this, "Payment Successful! ID: $razorpayPaymentID", android.widget.Toast.LENGTH_SHORT).show()
        
        // Save to Firebase Firestore directly since we're using offline-first architecture
        val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
        val companyId = auth.currentUser?.phoneNumber ?: return
        
        val paymentRecord = hashMapOf(
            "paymentId" to razorpayPaymentID,
            "status" to "success",
            "timestamp" to System.currentTimeMillis(),
            "data" to (paymentData?.data?.toString() ?: "")
        )
        
        firestore.collection("users").document(companyId)
            .collection("payments").document(razorpayPaymentID ?: System.currentTimeMillis().toString())
            .set(paymentRecord)
            .addOnSuccessListener {
                android.widget.Toast.makeText(this, "Subscription updated successfully on Cloud!", android.widget.Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                android.widget.Toast.makeText(this, "Failed to sync subscription: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
    }

    override fun onPaymentError(code: Int, response: String?, paymentData: com.razorpay.PaymentData?) {
        android.widget.Toast.makeText(this, "Payment Failed: $response", android.widget.Toast.LENGTH_SHORT).show()
    }
}
