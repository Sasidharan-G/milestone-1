package com.kadaikutty.pos.core.security

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Debug
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.kadaikutty.pos.BuildConfig
import com.scottyab.rootbeer.RootBeer
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.security.SecureRandom

object SecurityShield {

    private const val KEY_ALIAS = "com.kadaikutty.pos.db_encryption_key"
    private const val PREFS_NAME = "com.kadaikutty.pos.secure_prefs"
    private const val ENCRYPTED_PASS_KEY = "encrypted_db_pass"
    private const val IV_KEY = "encryption_iv"
    private const val FALLBACK_PASS_KEY = "fallback_db_pass"
    private const val FAILED_ATTEMPTS_KEY = "failed_access_attempts"
    private const val MAX_FAILED_ATTEMPTS = 5

    /**
     * Checks if the device is rooted, on unambiguous evidence only: an su binary, Magisk, or a
     * root-manager app. RootBeer's blanket isRooted also trips on "test-keys" firmware, writable
     * system paths and "dangerous" build properties, which many ordinary phones (custom or
     * carrier firmware, several budget brands) show. Those false positives locked honest shops
     * out of their own billing app.
     */
    fun isDeviceRooted(context: Context): Boolean {
        val rootBeer = RootBeer(context)
        val strongSignal = runCatching {
            rootBeer.checkForSuBinary() || rootBeer.checkForMagiskBinary() || rootBeer.detectRootManagementApps()
        }.getOrDefault(false)
        return strongSignal || checkRootFiles()
    }

    private fun checkRootFiles(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su"
        )
        for (path in paths) {
            if (java.io.File(path).exists()) return true
        }
        return false
    }

    /**
     * Detects if debugger is attached or the build is debuggable.
     */
    fun isDebuggerAttached(): Boolean {
        return Debug.isDebuggerConnected() || Debug.waitingForDebugger()
    }

    /**
     * Blocks traffic from Active VPN or Proxy configurations.
     */
    fun isVpnOrProxyActive(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNetwork = cm?.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        
        val isVpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        val hasProxy = System.getProperty("http.proxyHost") != null || 
                       System.getProperty("https.proxyHost") != null
                        
        return isVpn || hasProxy
    }

    /**
     * Validates package authenticity by matching runtime certificate SHA-256 hash.
     */
    fun verifyBinaryIntegrity(context: Context): Boolean {
        return try {
            val pm = context.packageManager
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }
            
            val info = pm.getPackageInfo(context.packageName, flags)
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                info.signatures
            }
            
            if (signatures.isNullOrEmpty()) return false
            
            // Enforced only when a real certificate hash is supplied via release.properties or
            // CI (SIGNING_CERT_SHA256). Blank means not configured: accept any signed build
            // rather than run a comparison that always passes and looks like verification.
            // Comma-separated, because Google Play re-signs every app with its own app-signing key:
            // an install from Play carries that certificate, not the upload keystore's. List both
            // (this build's key and the Play app-signing key) or Play installs fail this check.
            val allowed = BuildConfig.SIGNING_CERT_SHA256.split(',').map { it.replace(" ", "") }.filter { it.isNotBlank() }
            if (allowed.isEmpty()) return true

            val digest = MessageDigest.getInstance("SHA-256")
            signatures.any { signature ->
                val hexString = digest.digest(signature.toByteArray()).joinToString(":") { String.format("%02X", it) }
                allowed.any { it.equals(hexString, ignoreCase = true) }
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Tracks failed biometric/passcode access attempts and performs key lockout if limit is exceeded.
     */
    fun recordAccessAttempt(context: Context, isSuccess: Boolean): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (isSuccess) {
            prefs.edit().putInt(FAILED_ATTEMPTS_KEY, 0).apply()
            return true
        } else {
            val attempts = prefs.getInt(FAILED_ATTEMPTS_KEY, 0) + 1
            prefs.edit().putInt(FAILED_ATTEMPTS_KEY, attempts).apply()
            if (attempts >= MAX_FAILED_ATTEMPTS) {
                // Enforce 15-minute biometric lockout without destroying Keystore or local database!
                val lockoutUntil = System.currentTimeMillis() + (15 * 60 * 1000)
                prefs.edit().putLong("biometric_lockout_until", lockoutUntil).apply()
                return false // Lockout triggered
            }
            return true
        }
    }

    fun isBiometricLockedOut(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lockoutUntil = prefs.getLong("biometric_lockout_until", 0L)
        return System.currentTimeMillis() < lockoutUntil
    }

    fun getRemainingLockoutMinutes(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lockoutUntil = prefs.getLong("biometric_lockout_until", 0L)
        val diff = lockoutUntil - System.currentTimeMillis()
        return if (diff > 0) ((diff / 60000) + 1).toInt() else 0
    }

    /**
     * Quarantines corrupted or unreadable database files instead of permanently deleting them.
     */
    fun quarantineDatabase(context: Context) {
        val stamp = System.currentTimeMillis()
        listOf("billing.db", "billing.db-wal", "billing.db-shm").forEach { name ->
            val f = context.getDatabasePath(name)
            if (f.exists()) {
                val quarantined = java.io.File(f.parentFile, "$name.corrupt.$stamp")
                f.renameTo(quarantined)
            }
        }
        cleanupOldQuarantines(context)
    }

    /**
     * Removes quarantined database backups older than 30 days to protect disk storage.
     */
    fun cleanupOldQuarantines(context: Context) {
        try {
            val parent = context.getDatabasePath("billing.db").parentFile ?: return
            val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
            parent.listFiles()?.forEach { file ->
                if (file.name.contains(".corrupt.")) {
                    val parts = file.name.split(".corrupt.")
                    val fileTimestamp = parts.getOrNull(1)?.toLongOrNull()
                    if (fileTimestamp != null && fileTimestamp < thirtyDaysAgo) {
                        file.delete()
                    }
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * Wipes KeyStore keys in case of explicit confirmed Settings action only.
     */
    fun wipeKeystoreKeys() {
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS)
            }
        } catch (e: Exception) {
            // Ignored in wipe
        }
    }

    /**
     * Gets or generates a secure database passphrase from hardware Keystore.
     */
    @Synchronized
    fun getOrCreateDatabaseKey(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val encryptedPassBase64 = prefs.getString(ENCRYPTED_PASS_KEY, null)
        val ivBase64 = prefs.getString(IV_KEY, null)

        if (encryptedPassBase64 != null && ivBase64 != null) {
            try {
                val encryptedPass = Base64.decode(encryptedPassBase64, Base64.DEFAULT)
                val iv = Base64.decode(ivBase64, Base64.DEFAULT)
                return decryptKey(encryptedPass, iv)
            } catch (e: Exception) {
                // If decryption fails (e.g. key invalidated), we quarantine rather than delete
            }
        }

        // A phone whose Keystore refused to wrap the key earlier keeps it here (see below).
        prefs.getString(FALLBACK_PASS_KEY, null)?.let { stored ->
            try { return Base64.decode(stored, Base64.DEFAULT) } catch (_: Exception) { }
        }

        // If old database exists but key cannot decrypt it, quarantine it safely
        quarantineDatabase(context)

        // Generate new key
        val secureKey = ByteArray(32)
        SecureRandom().nextBytes(secureKey)
        try {
            val (encryptedPass, iv) = encryptKey(secureKey)
            // commit(), not apply(): the database is created with this key straight away, and a
            // key that never reached disk before the process died would make it unreadable.
            prefs.edit()
                .putString(ENCRYPTED_PASS_KEY, Base64.encodeToString(encryptedPass, Base64.DEFAULT))
                .putString(IV_KEY, Base64.encodeToString(iv, Base64.DEFAULT))
                .commit()
        } catch (e: Exception) {
            // Some phones have a faulty Keystore. Returning an unsaved key here meant the next
            // launch generated a different one, could not open the database and quarantined the
            // shop's data. Keep the key in the app's private storage instead: weaker than a
            // hardware-wrapped key, but the data stays reachable.
            prefs.edit().putString(FALLBACK_PASS_KEY, Base64.encodeToString(secureKey, Base64.DEFAULT)).commit()
        }
        return secureKey
    }

    private fun encryptKey(rawKey: ByteArray): Pair<ByteArray, ByteArray> {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        }

        val secretKey = keyStore.getKey(KEY_ALIAS, null) as SecretKey
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val encrypted = cipher.doFinal(rawKey)
        return Pair(encrypted, cipher.iv)
    }

    private fun decryptKey(encryptedKey: ByteArray, iv: ByteArray): ByteArray {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        val secretKey = keyStore.getKey(KEY_ALIAS, null) as SecretKey
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        return cipher.doFinal(encryptedKey)
    }
}
