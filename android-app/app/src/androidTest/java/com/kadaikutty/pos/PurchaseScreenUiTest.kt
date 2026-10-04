package com.kadaikutty.pos

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.core.view.WindowCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kadaikutty.pos.core.common.Money
import com.kadaikutty.pos.core.ui.LocalLayoutMode
import com.kadaikutty.pos.core.ui.theme.BillingTheme
import com.kadaikutty.pos.feature.purchase.presentation.PurchaseScreen
import com.kadaikutty.pos.feature.purchase.presentation.PurchaseViewModel
import com.kadaikutty.pos.support.DeviceShell
import com.kadaikutty.pos.support.PosTestFixture
import com.kadaikutty.pos.support.realTap
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Purchase screen against a throwaway in-memory shop, rooted like MainActivity (edge-to-edge +
 * imePadding). Covers what a client reported: "Add" seemed dead (the reason was shown out of sight),
 * and the supplier-payment dialog had the same keyboard flip as the billing one.
 */
@RunWith(AndroidJUnit4::class)
class PurchaseScreenUiTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var fixture: PosTestFixture
    private lateinit var vm: PurchaseViewModel
    private val layoutMode = mutableStateOf("Auto")

    @Before
    fun setUp() {
        fixture = PosTestFixture()
        vm = fixture.purchaseViewModel()
        rule.activityRule.scenario.onActivity { WindowCompat.setDecorFitsSystemWindows(it.window, false) }
        rule.setContent {
            CompositionLocalProvider(LocalLayoutMode provides layoutMode.value) {
                BillingTheme {
                    Box(Modifier.fillMaxSize().imePadding()) { PurchaseScreen(vm) }
                }
            }
        }
        rule.waitUntil(10_000) { vm.uiState.value.products.size == 3 && vm.uiState.value.suppliers.size == 1 }
        rule.waitForIdle()
    }

    @After
    fun tearDown() {
        fixture.close()
    }

    private fun pickProduct(name: String) {
        rule.onNodeWithText("Select Product").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText(name).onFirst().performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Select Product").fetchSemanticsNodes().isEmpty() }
        rule.waitForIdle()
    }

    private fun letKeyboardSettle(shot: String? = null) {
        rule.waitForIdle()
        Thread.sleep(2_000)
        rule.waitForIdle()
        shot?.let { DeviceShell.screenshot(it) }
    }

    /** A supplier and one line of 2 x 25.00 = 50.00 already in the cart, then the payment dialog open on its split page. */
    private fun openSplitPaymentPage() {
        rule.runOnIdle {
            vm.setSupplier("supplier")
            vm.addLine("milk", 2, Money(2500), "PIECE")
        }
        rule.waitUntil(5_000) { vm.uiState.value.lines.size == 1 }
        rule.onNodeWithText("Pay & Save Purchase").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Split Payment (Cash + UPI + Credit)").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Split Payment (Cash + UPI + Credit)").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Cash Paid (₹)").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun addWithoutSupplierSaysWhyRightUnderTheButton() {
        pickProduct("dairy milk")

        rule.onNodeWithText("Add").performClick()
        rule.waitForIdle()
        DeviceShell.screenshot("purchase-add-no-supplier")

        // Visible on screen, not merely somewhere in the layout below the fold.
        rule.onNode(hasText("supplier first", substring = true, ignoreCase = true)).assertIsDisplayed()
        assertEquals("nothing may be added without a supplier", 0, vm.uiState.value.lines.size)
    }

    @Test
    fun addWithSupplierAddsTheLineAndClearsTheReason() {
        rule.onNodeWithText("Select Supplier").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Test Supplier").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Test Supplier").performClick()
        pickProduct("dairy milk")

        rule.onNodeWithText("Add").performClick()
        rule.waitUntil(5_000) { vm.uiState.value.lines.size == 1 }

        assertEquals("milk", vm.uiState.value.lines.single().productId)
        rule.onAllNodesWithText("supplier first", substring = true, ignoreCase = true).assertCountEquals(0)
    }

    @Test
    fun reasonDisappearsOnceTheSupplierIsChosen() {
        pickProduct("dairy milk")
        rule.onNodeWithText("Add").performClick()
        rule.onNode(hasText("supplier first", substring = true, ignoreCase = true)).assertIsDisplayed()

        rule.onNodeWithText("Select Supplier").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Test Supplier").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Test Supplier").performClick()
        rule.waitForIdle()

        rule.onAllNodesWithText("supplier first", substring = true, ignoreCase = true).assertCountEquals(0)
    }

    @Test
    fun splitSupplierPaymentSurvivesALayoutSwitch() {
        openSplitPaymentPage()
        rule.onNode(hasText("Cash Paid (₹)") and hasSetTextAction()).performTextInput("10")
        rule.waitForIdle()
        rule.onNodeWithText("Total Paid: ₹10.00").assertIsDisplayed()

        rule.runOnIdle { layoutMode.value = "Tablet" }
        rule.waitForIdle()
        rule.runOnIdle { layoutMode.value = "Mobile" }
        rule.waitForIdle()

        rule.onNodeWithText("Total Paid: ₹10.00").assertIsDisplayed()
        rule.onAllNodesWithText("Select Payment Mode to Supplier").assertCountEquals(0)
    }

    @Test
    fun tappingCashPaidKeepsSupplierSplitPageOpen() {
        openSplitPaymentPage()

        rule.onNode(hasText("Cash Paid (₹)") and hasSetTextAction()).realTap()
        letKeyboardSettle("purchase-split-cash-tap")

        rule.onNodeWithText("Cash Paid (₹)").assertIsDisplayed()
        rule.onAllNodesWithText("Select Payment Mode to Supplier").assertCountEquals(0)
        rule.onNode(hasText("Cash Paid (₹)") and hasSetTextAction()).assertIsFocused()
    }
}
