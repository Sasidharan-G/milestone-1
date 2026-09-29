package com.kadaikutty.pos.core.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object BiometricAuthenticator {

    fun isBiometricAvailable(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)
        return biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun authenticate(
        activity: FragmentActivity,
        title: String = "Biometric Security Lock",
        subtitle: String = "Authenticate to access financial billing records",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        // SecurityShield records a 15-minute lockout after repeated failures; it was written but never
        // checked, so the prompt kept coming back. Refuse up front while it lasts.
        if (SecurityShield.isBiometricLockedOut(activity)) {
            onError("Too many failed attempts. Try again in ${SecurityShield.getRemainingLockoutMinutes(activity)} min.")
            return
        }
        val executor = ContextCompat.getMainExecutor(activity)
        var biometricPromptRef: BiometricPrompt? = null
        val biometricPrompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    // Only a real lockout counts against the user; closing the prompt or tapping
                    // Cancel is not a failed attempt.
                    if (errorCode == BiometricPrompt.ERROR_LOCKOUT || errorCode == BiometricPrompt.ERROR_LOCKOUT_PERMANENT) {
                        SecurityShield.recordAccessAttempt(activity, false)
                    }
                    onError(errString.toString())
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    SecurityShield.recordAccessAttempt(activity, true)
                    onSuccess()
                }

                override fun onAuthenticationFailed() {
                    // A finger that did not match; the prompt stays open for another try, so this
                    // only counts the attempt - onError here would report failure while it is still up.
                    super.onAuthenticationFailed()
                    if (!SecurityShield.recordAccessAttempt(activity, false)) {
                        biometricPromptRef?.cancelAuthentication()
                    }
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        biometricPromptRef = biometricPrompt
        biometricPrompt.authenticate(promptInfo)
    }
}
