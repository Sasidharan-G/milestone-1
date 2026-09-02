package com.kadaikutty.pos

import android.view.KeyEvent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.waitUntilDoesNotExist
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class EndToEndPosUiTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun init() {
        hiltRule.inject()
    }

    @Test
    fun completeE2EFlowTest() {
        // Random suffix to prevent unique constraint errors during multiple test runs
        val randomSuffix = System.currentTimeMillis().toString().takeLast(4)

        // 1. LOGIN FLOW
        composeTestRule.waitUntilExactlyOneExists(hasText("Mobile Number"), timeoutMillis = 10000)
        composeTestRule.onNodeWithText("Mobile Number").performTextInput("9789418144")
        composeTestRule.onNodeWithText("Password").performTextInput("123456")
        composeTestRule.onNodeWithText("Sign In").performClick()

        // Wait for Dashboard to load (look for "Point of Sale (POS)")
        composeTestRule.waitUntilExactlyOneExists(hasText("Point of Sale (POS)"), timeoutMillis = 15000)

        // 2. NAVIGATE TO MASTERS
        composeTestRule.onNodeWithText("Master Catalog").performClick()

        // 3. ADD CATEGORY
        composeTestRule.waitUntilExactlyOneExists(hasText("Categories"), timeoutMillis = 5000)
        composeTestRule.onNodeWithText("Categories").performClick()
        composeTestRule.waitUntilExactlyOneExists(hasText("Category Name"), timeoutMillis = 5000)
        composeTestRule.onNodeWithText("Category Name").performTextInput("Test Category $randomSuffix")
        composeTestRule.onNodeWithText("Add Category").performClick()

        // 4. ADD PRODUCT
        composeTestRule.onNodeWithText("Products").performClick()
        composeTestRule.waitUntilExactlyOneExists(hasText("Product Name"), timeoutMillis = 5000)
        composeTestRule.onNodeWithText("Product Name").performTextInput("Test Product $randomSuffix")
        composeTestRule.onNode(hasText("Sale Price", substring = true)).performTextInput("150")
        composeTestRule.onNodeWithText("Add Product").performClick()

        // 5. ADD CUSTOMER
        composeTestRule.onNodeWithText("Customers").performClick()
        composeTestRule.waitUntilExactlyOneExists(hasText("Customer Name"), timeoutMillis = 5000)
        composeTestRule.onNodeWithText("Customer Name").performTextInput("Test Customer $randomSuffix")
        composeTestRule.onNodeWithText("Phone Number").performTextInput("9876543210")
        composeTestRule.onNodeWithText("Add Customer").performClick()

        // 6. ADD SUPPLIER
        composeTestRule.onNodeWithText("Suppliers").performClick()
        composeTestRule.waitUntilExactlyOneExists(hasText("Supplier Name"), timeoutMillis = 5000)
        composeTestRule.onNodeWithText("Supplier Name").performTextInput("Test Supplier $randomSuffix")
        composeTestRule.onNodeWithText("Phone Number").performTextInput("9123456780")
        composeTestRule.onNodeWithText("Add Supplier").performClick()

        // Safely dismiss keyboard and navigate back to Dashboard
        Espresso.closeSoftKeyboard()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        composeTestRule.waitUntilExactlyOneExists(hasText("Point of Sale (POS)"), timeoutMillis = 5000)

        // 7. NAVIGATE TO BILLING & MAKE A SALE
        composeTestRule.onNodeWithText("Point of Sale (POS)").performClick()
        composeTestRule.waitUntilExactlyOneExists(hasText("Search & Select Product..."), timeoutMillis = 5000)
        composeTestRule.onNodeWithText("Search & Select Product...").performClick()

        // Search for product in selector dialog
        composeTestRule.waitUntilExactlyOneExists(hasText("Search product name, barcode..."), timeoutMillis = 5000)
        composeTestRule.onNodeWithText("Search product name, barcode...").performTextInput("Test Product $randomSuffix")

        // Unambiguously pick the first matching product node
        composeTestRule.onAllNodes(
            hasText("Test Product $randomSuffix", substring = true) and !hasSetTextAction()
        ).onFirst().performClick()

        composeTestRule.waitForIdle()

        // ADD the product to the cart
        composeTestRule.onNodeWithText("ADD").performClick()
        composeTestRule.waitForIdle()

        // Proceed to Checkout
        composeTestRule.onNodeWithText("Checkout All").performClick()
        composeTestRule.waitUntilExactlyOneExists(hasText("Full Cash", substring = true), timeoutMillis = 5000)

        // Select Cash Payment
        composeTestRule.onNode(hasText("Full Cash", substring = true)).performClick()
        composeTestRule.waitForIdle()

        // Verify sale completed and payment dialog dismissed
        composeTestRule.waitUntilDoesNotExist(hasText("Full Cash", substring = true), timeoutMillis = 5000)
    }
}

