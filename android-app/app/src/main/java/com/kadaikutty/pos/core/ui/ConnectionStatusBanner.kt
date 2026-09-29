package com.kadaikutty.pos.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Login
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Two states worth telling the cashier about:
 * - offline: bills are being saved on this phone and will sync later. Shown as a small round
 *   badge (not a full-width strip) so it never sits on top of the settings / cloud-sync buttons.
 * - back online, but this session was opened offline without server tokens (a different user
 *   than the last online one), so nothing can sync until they sign in once with internet.
 */
@Composable
fun ConnectionStatusBanner(
    isOnline: Boolean,
    needsSignIn: Boolean,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (needsSignIn) {
        // Full-width, so unlike the small badges below it needs real clearance: 56dp is the same
        // offset BillingApp's "Synced" pill uses to clear the header's icon row and shop-name
        // title on Home - this banner was sitting right on top of them, blocking the settings /
        // lock / logout buttons underneath.
        AnimatedVisibility(visible = true, modifier = modifier.statusBarsPadding().padding(top = 56.dp)) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val contentColor = MaterialTheme.colorScheme.onErrorContainer
                    Icon(
                        Icons.Default.Login,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        "Internet is back. Sign in again to sync bills made offline.",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = contentColor,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onSignIn) { Text("Sign in", fontWeight = FontWeight.Bold, color = contentColor) }
                }
            }
        }
    } else {
        AnimatedVisibility(visible = !isOnline, modifier = modifier.statusBarsPadding().padding(start = 12.dp, top = 4.dp)) {
            val contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.CloudOff,
                        contentDescription = "Offline",
                        tint = contentColor,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = CircleShape
                ) {
                    Text(
                        "Offline",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}
