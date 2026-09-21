package com.kadaikutty.pos.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
 * - offline: bills are being saved on this phone and will sync later;
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
    AnimatedVisibility(visible = !isOnline || needsSignIn, modifier = modifier.statusBarsPadding()) {
        Surface(
            color = if (needsSignIn) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val contentColor = if (needsSignIn) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer
                Icon(
                    if (needsSignIn) Icons.Default.Login else Icons.Default.CloudOff,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    if (needsSignIn) "Internet is back. Sign in again to sync bills made offline."
                    else "Offline — bills are saved on this phone and will sync automatically.",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = contentColor,
                    modifier = Modifier.weight(1f)
                )
                if (needsSignIn) {
                    TextButton(onClick = onSignIn) { Text("Sign in", fontWeight = FontWeight.Bold, color = contentColor) }
                }
            }
        }
    }
}
