package com.kadaikutty.pos.support

import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry

/** Shell access from an instrumented test: lets a test look at what is really on the phone's screen. */
object DeviceShell {
    fun run(command: String): String {
        val fd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.bufferedReader().readText() }
    }

    /** True while the soft keyboard is on screen (not just requested). */
    fun keyboardShown(): Boolean = run("dumpsys input_method").lines().any { it.contains("mInputShown=true") || it.contains("isInputViewShown=true") }

    /** Saved to /sdcard/Download so the picture outlives the test APK being uninstalled. */
    fun screenshot(name: String) {
        run("screencap -p /sdcard/Download/$name.png")
    }
}
