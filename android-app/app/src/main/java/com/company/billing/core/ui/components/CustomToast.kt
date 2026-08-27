package com.company.billing.core.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

enum class ToastType {
    MESSAGE, SUCCESS, WARNING, ERROR
}

data class ToastMessage(
    val id: Long,
    val text: String,
    val type: ToastType
)

object ToastManager {
    var toast by mutableStateOf<ToastMessage?>(null)
        private set
    
    private var toastIdCounter = 0L

    fun showMessage(text: String) {
        toast = ToastMessage(++toastIdCounter, text, ToastType.MESSAGE)
    }

    fun showSuccess(text: String) {
        toast = ToastMessage(++toastIdCounter, text, ToastType.SUCCESS)
    }

    fun showWarning(text: String) {
        toast = ToastMessage(++toastIdCounter, text, ToastType.WARNING)
    }

    fun showError(text: String) {
        toast = ToastMessage(++toastIdCounter, text, ToastType.ERROR)
    }

    fun dismiss() {
        toast = null
    }
}

@Composable
fun CustomToastOverlay(modifier: Modifier = Modifier) {
    val toast = ToastManager.toast

    LaunchedEffect(toast) {
        if (toast != null) {
            delay(3000)
            if (ToastManager.toast?.id == toast.id) {
                ToastManager.dismiss()
            }
        }
    }

    Box(modifier = modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = toast != null,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(350, easing = androidx.compose.animation.core.FastOutSlowInEasing)
            ) + fadeIn(),
            exit = fadeOut(animationSpec = tween(250))
        ) {
            toast?.let { currentToast ->
                val (backgroundColor, textColor) = when (currentToast.type) {
                    ToastType.MESSAGE -> Color(0xFFF3F4F6) to Color(0xFF111827) // Geist background
                    ToastType.SUCCESS -> Color(0xFF1D4ED8) to Color.White // Blue 700
                    ToastType.WARNING -> Color(0xFF92400E) to Color.White // Amber 800
                    ToastType.ERROR -> Color(0xFF991B1B) to Color.White // Red 800
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(elevation = 12.dp, shape = RoundedCornerShape(12.dp))
                        .background(color = backgroundColor, shape = RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = currentToast.text,
                        color = textColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Toast",
                        tint = textColor.copy(alpha = 0.8f),
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { ToastManager.dismiss() }
                    )
                }
            }
        }
    }
}
