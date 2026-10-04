package com.kadaikutty.pos

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kadaikutty.pos.core.presentation.components.ProductPhotoField
import com.kadaikutty.pos.support.DeviceShell
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * Reported by a client: tapping "Take a Photo" while adding a product threw the person out of the
 * app. The manifest declares CAMERA (the barcode scanner needs it), and Android crashes an app that
 * declares it, has not been granted it, and starts the camera app anyway. So the photo field now asks
 * first, and says what to do when the answer is no.
 *
 * Runs in name order because the first test needs the permission not granted yet and the second
 * grants it. A fresh install (what Gradle's connected run does) starts without it.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ProductPhotoCameraTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun cameraGranted() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun showPhotoField(): MutableList<android.net.Uri> {
        val captured = mutableListOf<android.net.Uri>()
        composeRule.setContent {
            MaterialTheme {
                ProductPhotoField(imageModel = null, onPickedFromGallery = {}, onCapturedFromCamera = { captured += it }, onRemove = null)
            }
        }
        return captured
    }

    private fun tapTakeAPhoto() {
        composeRule.onNodeWithContentDescription("Add product photo").performClick()
        composeRule.onNodeWithText("Take a Photo").performClick()
    }

    /** What is in front of the person right now, e.g. the camera app or the permission box. */
    private fun focusedWindow(): String =
        DeviceShell.run("dumpsys window").lines().firstOrNull { it.contains("mCurrentFocus") }.orEmpty()

    private fun waitForFocus(timeoutMs: Long = 15_000, matches: (String) -> Boolean): String {
        val end = System.currentTimeMillis() + timeoutMs
        var focus = focusedWindow()
        while (!matches(focus) && System.currentTimeMillis() < end) {
            Thread.sleep(250)
            focus = focusedWindow()
        }
        return focus
    }

    private fun appIsStillRunning(): Boolean = composeRule.activity.let { !it.isFinishing && !it.isDestroyed }

    @Test fun a_withoutCameraPermissionTheAppAsksInsteadOfCrashing() {
        assumeTrue("needs a fresh install: the camera permission was already granted", !cameraGranted())
        showPhotoField()

        tapTakeAPhoto()

        val focus = waitForFocus { it.contains("ermission") }
        assertTrue("the system asks for the camera permission, front window was: $focus", focus.contains("ermission"))
        // Say no (back closes the box). The app is still there and nothing was captured.
        DeviceShell.run("input keyevent KEYCODE_BACK")
        val after = waitForFocus { it.contains(context.packageName) }
        assertTrue("the app is still in front after saying no: $after", after.contains(context.packageName))
        assertTrue(appIsStillRunning())
    }

    @Test fun b_withCameraPermissionTheCameraOpensAndComesBack() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.CAMERA)
        assertTrue(cameraGranted())
        showPhotoField()

        tapTakeAPhoto()

        val focus = waitForFocus { it.isNotBlank() && !it.contains(context.packageName) && !it.contains("null") }
        assertTrue("a camera app is in front, front window was: $focus", focus.isNotBlank() && !focus.contains(context.packageName))
        // Leave the camera without taking a picture: the app must be back and alive.
        DeviceShell.run("input keyevent KEYCODE_BACK")
        val after = waitForFocus { it.contains(context.packageName) }
        assertTrue("back in the app: $after", after.contains(context.packageName))
        assertTrue(appIsStillRunning())
    }
}
