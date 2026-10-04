package com.kadaikutty.pos.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * One pane at a time with tabs (a phone held upright) or both panes side by side (a tablet or a
 * phone on its side).
 *
 * "Upright" is read from the window ([windowWidthDp] x [windowHeightDp] from the Configuration),
 * never from the height of the box the screen is laid out in. That box is shrunk by the soft
 * keyboard, so on a phone with a tall keyboard the screen used to look "landscape" the moment a
 * field was tapped: the whole screen flipped to the tablet layout, every open dialog and the
 * focused field were destroyed, the keyboard closed, and it flipped straight back.
 */
fun isSinglePane(layoutMode: String, contentWidth: Dp, windowWidthDp: Int, windowHeightDp: Int): Boolean =
    when (layoutMode) {
        "Mobile" -> true
        "Tablet" -> false
        else -> contentWidth < 700.dp && windowHeightDp > windowWidthDp
    }

/** [isSinglePane] for the window this composable is shown in. */
@Composable
fun shouldUseSinglePane(layoutMode: String, contentWidth: Dp): Boolean {
    val configuration = LocalConfiguration.current
    return isSinglePane(layoutMode, contentWidth, configuration.screenWidthDp, configuration.screenHeightDp)
}
