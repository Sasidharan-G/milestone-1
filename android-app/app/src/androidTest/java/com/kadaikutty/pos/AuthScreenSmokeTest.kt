package com.kadaikutty.pos

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pure rendering/navigation smoke test for the login screen - no network, no backend, runs on
 * every instrumented test pass (unlike [EndToEndPosUiTest], which needs a staging backend and an
 * explicit flag). This is the test that should have caught a real bug shipped in a release build:
 * a Column(Modifier.verticalScroll()) was nested inside another scrolling Column, which Compose
 * throws on at layout time ("measured with an infinity maximum height constraints") - invisible in
 * a design preview or a unit test, only visible once the screen actually renders on a device.
 */
@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AuthScreenSmokeTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun init() {
        hiltRule.inject()
    }

    @Test
    fun loginScreenRendersAllFields() {
        composeTestRule.waitUntilExactlyOneExists(hasText("Welcome Back"), timeoutMillis = 10000)
        composeTestRule.onNodeWithTag("login_mobile_field").assertIsDisplayed()
        for (i in 0 until 6) {
            // The boxes sit inside a clickable row that merges its children's semantics, so they only
            // exist in the unmerged tree (they are drawn for show; typing goes to login_pin_input).
            composeTestRule.onNodeWithTag("login_pin_digit_$i", useUnmergedTree = true).assertIsDisplayed()
        }
        composeTestRule.onNodeWithText("Sign In").assertIsDisplayed()
    }

    @Test
    fun flippingToRegisterAndBackDoesNotCrash() {
        composeTestRule.waitUntilExactlyOneExists(hasText("Welcome Back"), timeoutMillis = 10000)
        composeTestRule.onNodeWithText("Register your store").performClick()
        composeTestRule.waitUntilExactlyOneExists(hasText("Merchant Registration"), timeoutMillis = 5000)

        composeTestRule.onNodeWithText("Already have an account? Sign In").performClick()
        composeTestRule.waitUntilExactlyOneExists(hasText("Welcome Back"), timeoutMillis = 8000)
    }
}
