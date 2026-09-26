package com.kadaikutty.pos

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kadaikutty.pos.core.ui.BillingApp
import com.kadaikutty.pos.core.security.SecurityShield
import com.kadaikutty.pos.BuildConfig
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge layout; system bars appearance will be driven dynamically by theme
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF5C151A)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Kadaikutty POS",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
        lifecycleScope.launch {
            // The security checks (root, signature) and opening the encrypted database are both slow
            // on budget phones and independent of each other, so they run side by side off the main
            // thread. Nothing of the app is shown until the checks pass, so a blocked phone still
            // sees only this splash and the alert.
            val (securityProblem, failure) = withContext(Dispatchers.IO) {
                kotlinx.coroutines.coroutineScope {
                    val check = async { securityFailure() }
                    val database = async<Throwable?> {
                        try {
                            val db = dagger.hilt.android.EntryPointAccessors.fromApplication(applicationContext, DatabaseStartup::class.java).database()
                            db.openHelper.writableDatabase.query("SELECT 1").use { it.moveToFirst() }
                            null
                        } catch (e: Exception) { e }
                    }
                    check.await() to database.await()
                }
            }
            if (securityProblem != null) {
                showSecurityFailureAndExit(securityProblem.first, securityProblem.second)
                return@launch
            }
            // The app draws edge to edge, so the window does not shrink for the keyboard by itself.
            // Padding the whole app by the keyboard's height keeps the screen above it, and a
            // focused field inside a scrolling screen is then scrolled into view.
            // Phones set to "Largest" font (common with older shop owners) scale every sp by up to
            // 1.3x and push buttons and totals off small screens; cap the scale so layouts hold.
            if (failure == null) setContent {
                val density = androidx.compose.ui.platform.LocalDensity.current
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(
                        density = density.density,
                        fontScale = density.fontScale.coerceAtMost(1.15f)
                    )
                ) {
                    Box(modifier = Modifier.fillMaxSize().imePadding()) { BillingApp() }
                }
            }
            else {
                android.util.Log.e("DatabaseStartup", "Database opening failed; original files preserved", failure)
                android.app.AlertDialog.Builder(this@MainActivity)
                    .setTitle("Shop data could not be opened")
                    .setMessage("Your existing database has been preserved. Free storage and restart the app. If this continues, contact support with your backup. Do not clear app data or reinstall.")
                    .setCancelable(false)
                    .setPositiveButton("Close") { _, _ -> finish() }.show()
            }
        }
    }

    /** Title and message of the reason this phone must not run the app, or null when it may. Runs off the main thread. */
    private fun securityFailure(): Pair<String, String>? {
        // Allow emulators / debug builds during development phase
        if (!BuildConfig.DEBUG) {
            if (SecurityShield.isDeviceRooted(this)) {
                return "Security Alert" to "This application cannot execute on rooted devices."
            }
            if (SecurityShield.isDebuggerAttached()) {
                return "Security Alert" to "Active debugging tools detected. Session terminated."
            }
        }

        // Note: VPN/Proxy check is relaxed to allow legitimate shop network configurations (e.g. Cloudflare WARP, Google One VPN, AdGuard)

        if (!SecurityShield.verifyBinaryIntegrity(this)) {
            return "Integrity Failure" to "App binary verification failed. Reinstall from official source."
        }

        return null
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

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface DatabaseStartup {
    fun database(): com.kadaikutty.pos.core.database.BillingDatabase
}
