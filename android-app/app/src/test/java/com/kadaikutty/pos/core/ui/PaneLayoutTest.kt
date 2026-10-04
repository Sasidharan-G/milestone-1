package com.kadaikutty.pos.core.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaneLayoutTest {

    @Test
    fun upright_phone_gets_one_pane() {
        assertTrue(isSinglePane("Auto", 360.dp, windowWidthDp = 360, windowHeightDp = 800))
        assertTrue(isSinglePane("Auto", 411.dp, windowWidthDp = 411, windowHeightDp = 914))
        // the short budget phone the screens were already tuned for
        assertTrue(isSinglePane("Auto", 360.dp, windowWidthDp = 360, windowHeightDp = 592))
    }

    @Test
    fun phone_on_its_side_gets_two_panes() {
        assertFalse(isSinglePane("Auto", 640.dp, windowWidthDp = 800, windowHeightDp = 360))
    }

    @Test
    fun wide_window_gets_two_panes_even_when_tall() {
        assertFalse(isSinglePane("Auto", 800.dp, windowWidthDp = 800, windowHeightDp = 1280))
    }

    @Test
    fun explicit_layout_setting_wins() {
        assertTrue(isSinglePane("Mobile", 1000.dp, windowWidthDp = 1000, windowHeightDp = 600))
        assertFalse(isSinglePane("Tablet", 360.dp, windowWidthDp = 360, windowHeightDp = 800))
    }
}
