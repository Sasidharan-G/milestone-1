package com.kadaikutty.pos.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kadaikutty.pos.core.sync.SyncNotificationState
import kotlinx.coroutines.delay

@Composable
fun SyncNotificationOverlay(
    state: SyncNotificationState,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Auto-dismiss Success after 3.5 seconds
    LaunchedEffect(state) {
        if (state is SyncNotificationState.Success) {
            delay(3500)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = state !is SyncNotificationState.Idle,
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it },
        modifier = modifier
    ) {
        when (state) {
            is SyncNotificationState.Idle -> {
                // No-op
            }

            is SyncNotificationState.InProgress -> {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF0F172A),
                    border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f)),
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .wrapContentWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color(0xFF38BDF8),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "Cloud sync in progress...",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            is SyncNotificationState.Success -> {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF064E3B),
                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.6f)),
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .wrapContentWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Success",
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = state.message,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            is SyncNotificationState.Failed -> {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF1F2)),
                    border = BorderStroke(1.5.dp, Color(0xFFFDA4AF)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .shadow(8.dp, RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = "Sync Failed",
                                tint = Color(0xFFE11D48),
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = "Sync Failed",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFFBE123C),
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color(0xFF9F1239),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Text(
                            text = state.reason,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF1F2937),
                            lineHeight = 16.sp
                        )

                        Text(
                            text = "Next steps: ${state.suggestedNextSteps}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color(0xFF4B5563),
                            lineHeight = 15.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = onDismiss,
                                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF6B7280)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Dismiss", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            Button(
                                onClick = onRetry,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Retry",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Retry", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}
