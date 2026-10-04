package com.kadaikutty.pos.support

import android.view.View
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.test.SemanticsNodeInteraction

/**
 * A tap that goes through Android's real input pipeline (like a finger), instead of Compose's test
 * injection. Only a real tap gives the window focus and makes the soft keyboard actually appear,
 * which is what shrinks the screen and triggered the client's bug.
 */
@OptIn(InternalComposeUiApi::class)
fun SemanticsNodeInteraction.realTap() {
    val node = fetchSemanticsNode()
    val root = node.root as View
    val origin = IntArray(2)
    root.getLocationOnScreen(origin)
    val centre = node.boundsInRoot.center
    DeviceShell.run("input tap ${origin[0] + centre.x.toInt()} ${origin[1] + centre.y.toInt()}")
}
