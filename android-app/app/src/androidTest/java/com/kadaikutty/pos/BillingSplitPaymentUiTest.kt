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
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.core.view.WindowCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kadaikutty.pos.core.common.Money
import com.kadaikutty.pos.core.ui.LocalLayoutMode
import com.kadaikutty.pos.core.ui.theme.BillingTheme
import com.kadaikutty.pos.feature.billing.presentation.BillingScreen
import com.kadaikutty.pos.feature.billing.presentation.BillingViewModel
import com.kadaikutty.pos.support.DeviceShell
import com.kadaikutty.pos.support.PosTestFixture
import com.kadaikutty.pos.support.realTap
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Billing screen against a throwaway in-memory shop, with the same root as MainActivity
 * (edge-to-edge + imePadding) so the real soft keyboard shrinks the screen exactly as on a phone.
 *
 * Reported by a client: in Split / Partial payment, tapping "Cash Amount Received" threw the dialog
 * back to the first "Quick Checkout" page. These tests pin that, and the wrong total that a
 * cancelled split left on the main screen.
 */
@RunWith(AndroidJUnit4::class)
class BillingSplitPaymentUiTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var fixture: PosTestFixture
    private lateinit var vm: BillingViewModel
    private val layoutMode = mutableStateOf("Auto")

    @Before
    fun setUp() {
        fixture = PosTestFixture()
        vm = fixture.billingViewModel()
        rule.activityRule.scenario.onActivity { WindowCompat.setDecorFitsSystemWindows(it.window, false) }
        rule.setContent {
            CompositionLocalProvider(LocalLayoutMode provides layoutMode.value) {
                BillingTheme {
                    Box(Modifier.fillMaxSize().imePadding()) { BillingScreen(vm) }
                }
            }
        }
        // Products and stock arrive from the database asynchronously; addLine checks stock.
        rule.waitUntil(10_000) { vm.uiState.value.products.size == 3 && vm.uiState.value.stockBalances.size == 3 }
        rule.runOnIdle {
            check(vm.addLine("turmeric", "turmeric powder", 2, Money(12000), "PIECE") == null)
            check(vm.addLine("milk", "dairy milk", 2, Money(5000), "PIECE") == null)
            check(vm.addLine("soap", "hamam soap", 2, Money(5000), "PIECE") == null)
        }
        rule.waitUntil(5_000) { vm.uiState.value.lines.size == 3 }
        rule.waitForIdle()
    }

    @After
    fun tearDown() {
        fixture.close()
    }

    private fun openSplitPaymentPage() {
        rule.onNodeWithText("Pay All").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Split / Partial Payment").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Split / Partial Payment").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Cash Amount Received").fetchSemanticsNodes().isNotEmpty() }
    }

    /** Lets the keyboard animate in and the layout settle, the way a person waits a moment after tapping. */
    private fun letKeyboardSettle(shot: String? = null) {
        rule.waitForIdle()
        Thread.sleep(2_000)
        rule.waitForIdle()
        shot?.let { DeviceShell.screenshot(it) }
    }

    @Test
    fun tappingCashFieldKeepsSplitPaymentPageOpen() {
        openSplitPaymentPage()

        rule.onNode(hasText("Cash Amount Received") and hasSetTextAction()).realTap()
        letKeyboardSettle("split-cash-tap")

        rule.onNodeWithText("Cash Amount Received").assertIsDisplayed()
        rule.onAllNodesWithText("Select Quick Checkout:").assertCountEquals(0)
        // The field is still the one being typed in: it was not torn down and rebuilt.
        rule.onNode(hasText("Cash Amount Received") and hasSetTextAction()).assertIsFocused()
    }

    @Test
    fun typingSplitAmountsWorksWithKeyboardOpen() {
        openSplitPaymentPage()

        rule.onNode(hasText("Cash Amount Received") and hasSetTextAction()).realTap()
        letKeyboardSettle()
        rule.onNode(hasText("Cash Amount Received") and hasSetTextAction()).performTextInput("300")
        letKeyboardSettle()

        // 440 payable, 300 cash: 140 is left as credit.
        rule.onNodeWithText("Remaining Credit: ₹140.00").assertIsDisplayed()
        rule.onAllNodesWithText("Select Quick Checkout:").assertCountEquals(0)
    }

    @Test
    fun openingKeyboardOnBillingScreenKeepsTheFieldFocused() {
        rule.onNodeWithText("Draft Invoice").assertIsDisplayed()

        rule.onNode(hasText("Discount(₹)") and hasSetTextAction()).realTap()
        letKeyboardSettle("discount-tap")

        // Before the fix the screen flipped to the tablet layout for a moment: the field was rebuilt
        // (losing focus), the keyboard closed again, and it flipped back - so only focus tells.
        rule.onNode(hasText("Discount(₹)") and hasSetTextAction()).assertIsFocused()
        rule.onNodeWithText("Draft Invoice").assertIsDisplayed()
        rule.onNodeWithText("Sales History").assertIsDisplayed()
    }

    @Test
    fun splitEntriesSurviveALayoutSwitch() {
        openSplitPaymentPage()
        rule.onNode(hasText("Cash Amount Received") and hasSetTextAction()).performTextInput("125")
        rule.waitForIdle()
        rule.onNodeWithText("Remaining Credit: ₹315.00").assertIsDisplayed()

        // Anything that re-lays the screen out (rotation, a fold, split-screen) must not reset the dialog.
        rule.runOnIdle { layoutMode.value = "Tablet" }
        rule.waitForIdle()
        rule.runOnIdle { layoutMode.value = "Mobile" }
        rule.waitForIdle()

        rule.onNodeWithText("Remaining Credit: ₹315.00").assertIsDisplayed()
        rule.onAllNodesWithText("Select Quick Checkout:").assertCountEquals(0)
    }

    @Test
    fun cancelledSplitLeavesWholeCartTotal() {
        rule.onNodeWithText("Total: ₹440.00").assertIsDisplayed()

        rule.onNodeWithText("Split").performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(isToggleable()).fetchSemanticsNodes().size == 3 }
        rule.onAllNodes(isToggleable())[1].performClick() // dairy milk
        rule.onNodeWithText("Proceed to Pay").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Payment for Split Cart").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Total Payable: ₹100.00").assertIsDisplayed()
        rule.onNodeWithText("Cancel").performClick()
        rule.waitForIdle()

        // The cart still holds all three lines, so the main screen must still say 440.
        rule.onNodeWithText("Total: ₹440.00").assertIsDisplayed()
    }

    @Test
    fun splitSelectionStartsEmptyEachTime() {
        rule.onNodeWithText("Split").performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(isToggleable()).fetchSemanticsNodes().size == 3 }
        rule.onAllNodes(isToggleable())[1].performClick()
        rule.onNodeWithText("Cancel").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Split").performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(isToggleable()).fetchSemanticsNodes().size == 3 }
        rule.onAllNodes(isToggleable()).assertCountEquals(3)
        rule.onNodeWithText("Proceed to Pay").assertIsNotEnabled()
    }

    @Test
    fun finishedSplitLeavesRemainingItemsTotal() {
        rule.onNodeWithText("Split").performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(isToggleable()).fetchSemanticsNodes().size == 3 }
        rule.onAllNodes(isToggleable())[1].performClick() // dairy milk, 100
        rule.onNodeWithText("Proceed to Pay").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Payment for Split Cart").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Full Cash (₹100.00)").performClick()
        rule.waitUntil(10_000) { vm.uiState.value.lines.size == 2 }
        rule.waitForIdle()

        // 440 - 100 left: turmeric 240 + soap 100.
        rule.onNodeWithText("Total: ₹340.00").assertIsDisplayed()
    }

    @Test
    fun splitWithDiscountAsksForTheSameAmountTheBillTotals() {
        // 440 cart with a 44 discount (10%): the dairy milk batch of 100 carries 10 of it.
        rule.onNode(hasText("Discount(₹)") and hasSetTextAction()).performTextInput("44")
        rule.waitForIdle()
        rule.onNodeWithText("Total: ₹396.00").assertIsDisplayed()

        rule.onNodeWithText("Split").performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(isToggleable()).fetchSemanticsNodes().size == 3 }
        rule.onAllNodes(isToggleable())[1].performClick() // dairy milk
        rule.onNodeWithText("Proceed to Pay").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Payment for Split Cart").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Total Payable: ₹90.00").assertIsDisplayed()
        rule.onNodeWithText("Full Cash (₹90.00)").performClick()
        rule.waitUntil(10_000) { vm.uiState.value.lines.size == 2 }
        rule.waitForIdle()

        val sale = runBlocking { fixture.db.saleDao().getSales(PosTestFixture.COMPANY).first() }.single()
        assertEquals("the bill total must equal what the dialog asked for", 9000L, sale.totalMinorUnits)
        // The other 34 of the discount stays on the 340 that is still in the cart: 340 - 34 = 306.
        rule.onNodeWithText("Total: ₹306.00").assertIsDisplayed()
    }
}
