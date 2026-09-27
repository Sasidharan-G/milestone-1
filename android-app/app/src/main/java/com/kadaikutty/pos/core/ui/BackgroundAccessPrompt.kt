package com.kadaikutty.pos.core.ui

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Phone makers (Xiaomi, Oppo, Realme, Vivo, Samsung and others) freeze apps that are not on screen
 * to save battery, which stops the background sync that pushes offline bills to the cloud. The only
 * fix is a setting the owner has to switch on, so this asks for it and opens the exact screen.
 */
object BackgroundAccess {
    private const val PREFS = "background_access"
    private const val SNOOZE_UNTIL = "snooze_until"
    private const val AUTOSTART_OPENED = "autostart_opened"
    private const val BATTERY_REQUESTED = "battery_requested"
    private const val BATTERY_CONFIRMED = "battery_confirmed"
    private const val SNOOZE_MS = 24L * 60 * 60 * 1000

    fun isUnrestricted(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return runCatching { pm.isIgnoringBatteryOptimizations(context.packageName) }.getOrDefault(true)
    }

    /** Auto-start has no readable state, so it is asked for once: after the owner has been taken to that screen it is not asked again. */
    fun autoStartPending(context: Context): Boolean =
        hasAutoStartScreen() && !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(AUTOSTART_OPENED, false)

    /**
     * The battery setting is only readable on stock Android. Vivo, Oppo, Xiaomi and others keep their
     * own "unrestricted" switch that [isUnrestricted] never sees, so an owner who has set it correctly
     * would be asked forever. After going to the settings screen the owner can say it is done, and that
     * answer is trusted from then on.
     */
    fun batteryConfirmed(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(BATTERY_CONFIRMED, false)

    fun batteryRequested(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(BATTERY_REQUESTED, false)

    fun confirmBattery(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(BATTERY_CONFIRMED, true).apply()
    }

    fun batteryNeeded(context: Context): Boolean = !(isUnrestricted(context) || batteryConfirmed(context))

    fun shouldAsk(context: Context): Boolean = shouldAskFor(
        batteryNeeded = batteryNeeded(context),
        autoStartPending = autoStartPending(context),
        snoozeUntil = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(SNOOZE_UNTIL, 0L),
        now = System.currentTimeMillis()
    )

    internal fun shouldAskFor(batteryNeeded: Boolean, autoStartPending: Boolean, snoozeUntil: Long, now: Long): Boolean {
        if (!batteryNeeded && !autoStartPending) return false
        return now >= snoozeUntil
    }

    fun snooze(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(SNOOZE_UNTIL, System.currentTimeMillis() + SNOOZE_MS).apply()
    }

    /** The manufacturer's own auto-start / background screen, when it has one we know how to open. */
    private fun autoStartIntents(): List<Intent> {
        val make = Build.MANUFACTURER.lowercase()
        fun component(pkg: String, cls: String) = Intent().setComponent(ComponentName(pkg, cls))
        return when {
            make.contains("xiaomi") || make.contains("redmi") || make.contains("poco") -> listOf(
                component("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"))
            make.contains("oppo") || make.contains("realme") || make.contains("oneplus") -> listOf(
                component("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                component("com.oplus.battery", "com.oplus.startupapp.view.StartupAppListActivity"),
                component("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"))
            make.contains("vivo") || make.contains("iqoo") -> listOf(
                component("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                component("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"))
            make.contains("huawei") || make.contains("honor") -> listOf(
                component("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"))
            make.contains("asus") -> listOf(
                component("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity"))
            else -> emptyList()
        }
    }

    fun hasAutoStartScreen(): Boolean = autoStartIntents().isNotEmpty()

    /** Opens the phone's own auto-start screen, or the app's settings page when it has none. */
    fun openAutoStart(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(AUTOSTART_OPENED, true).apply()
        for (intent in autoStartIntents()) if (launch(context, intent)) return
        openAppSettings(context)
    }

    /**
     * Opens the phone's battery-optimisation list, where the owner sets this app to "Unrestricted" /
     * "Don't optimise". The one-tap system prompt (ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) is
     * not used: it needs a permission Google Play only allows for a few kinds of app and rejects
     * the rest, and this app has to be publishable there. Falls back to this app's settings page.
     */
    fun requestUnrestricted(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(BATTERY_REQUESTED, true).apply()
        if (launch(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))) return
        openAppSettings(context)
    }

    private fun openAppSettings(context: Context) {
        launch(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
    }

    private fun launch(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}

/** Shown while the app is not allowed to run in the background; disappears once the owner allows it. */
@Composable
fun BackgroundAccessPrompt() {
    val context = LocalContext.current
    var visible by remember { mutableStateOf(BackgroundAccess.shouldAsk(context)) }

    // Coming back from the settings screen: re-read the state instead of trusting the tap.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) visible = BackgroundAccess.shouldAsk(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!visible) return

    val needsBattery = BackgroundAccess.batteryNeeded(context)
    // Already sent to the settings screen and still showing: this phone does not report its own
    // battery setting to apps, so ask the owner instead of asking again forever.
    val alreadyOpened = needsBattery && BackgroundAccess.batteryRequested(context)
    AlertDialog(
        onDismissRequest = { BackgroundAccess.snooze(context); visible = false },
        title = { Text("Allow background sync", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                if (alreadyOpened) {
                    "Did you set KadaiKutty to \"Unrestricted\" / \"Don't optimise\"? Some phones (Vivo, Oppo, Xiaomi) " +
                        "do not tell apps their own battery setting, so KadaiKutty cannot see it. If you have set it, tap " +
                        "\"Yes, it's set\" and this message will not appear again."
                } else if (needsBattery) {
                    "This phone may stop KadaiKutty in the background, so bills made offline would not reach " +
                        "the cloud until you open the app. Tap Allow, find KadaiKutty in the list and choose \"Don't optimise\" / \"Unrestricted\"."
                } else {
                    "One more step: this phone also has an Auto-start list. Tap Open, then switch KadaiKutty on " +
                        "so background sync keeps running."
                }
            )
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    alreadyOpened -> { BackgroundAccess.confirmBattery(context); visible = BackgroundAccess.shouldAsk(context) }
                    needsBattery -> BackgroundAccess.requestUnrestricted(context)
                    else -> BackgroundAccess.openAutoStart(context)
                }
            }) { Text(if (alreadyOpened) "Yes, it's set" else if (needsBattery) "Allow" else "Open", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = {
                if (alreadyOpened) BackgroundAccess.requestUnrestricted(context) else { BackgroundAccess.snooze(context); visible = false }
            }) { Text(if (alreadyOpened) "Open settings again" else "Later") }
        }
    )
}
