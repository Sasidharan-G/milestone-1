package com.kadaikutty.pos.core.ui.otp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Modern High-Contrast Charcoal & Warm Gold Glassmorphic OTP Verification View.
 * Sleek, professional UI with fluid micro-animations:
 * - Clean single-header card layout with close action button
 * - Properly formatted masked phone number badge
 * - 6 distinct high-contrast interactive OTP entry slots
 * - Blinking pulsing cursor and error shake physics
 * - Smooth circular countdown timer & resend button
 * - Shimmering primary verify button
 */
@Composable
fun OrbitOtpVerificationView(
    otpLength: Int = 6,
    otpValue: String,
    phoneNumber: String,
    onOtpChange: (String) -> Unit,
    onVerifyTriggered: (String) -> Unit = {},
    onResendClick: () -> Unit = {},
    onCloseClick: (() -> Unit)? = null,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    showVerifyButton: Boolean = true,
    verifyButtonText: String = "Verify & Proceed"
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val isComplete = otpValue.length == otpLength

    // Auto-focus keyboard on load
    LaunchedEffect(Unit) {
        delay(250)
        try { focusRequester.requestFocus() } catch (_: Exception) {}
    }

    // Countdown timer for OTP Resend (30 seconds)
    var countdownSeconds by remember { mutableIntStateOf(30) }
    var canResend by remember { mutableStateOf(false) }

    LaunchedEffect(countdownSeconds) {
        if (countdownSeconds > 0) {
            delay(1000L)
            countdownSeconds--
        } else {
            canResend = true
        }
    }

    // 1. Hero Badge Pulsing Animation
    val infiniteTransition = rememberInfiniteTransition(label = "SecurityHaloTransition")
    
    val haloScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "haloScale"
    )

    val haloAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "haloAlpha"
    )

    val badgeRotation by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "badgeRotation"
    )

    // 2. Cursor Blink Animation for Active Slot
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursorAlpha"
    )

    // 3. Error Shake Physics
    val hasError = !errorMessage.isNullOrBlank()
    val shakeOffset = remember { Animatable(0f) }

    LaunchedEffect(errorMessage) {
        if (!errorMessage.isNullOrBlank()) {
            shakeOffset.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 400
                    -12f at 50
                    12f at 100
                    -8f at 180
                    8f at 260
                    -4f at 330
                    0f at 400
                }
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- TOP CLOSE BUTTON ROW ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onCloseClick != null) {
                IconButton(
                    onClick = onCloseClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // --- HERO SECURITY BADGE WITH GLOWING HALO ---
        Box(
            modifier = Modifier
                .size(76.dp)
                .padding(2.dp),
            contentAlignment = Alignment.Center
        ) {
            // Expanding Golden Halo Wave
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .scale(haloScale)
                    .clip(CircleShape)
                    .background(Color(0xFFF59E0B).copy(alpha = haloAlpha))
            )

            // Inner Glassmorphic Circle
            Surface(
                modifier = Modifier
                    .size(56.dp)
                    .graphicsLayer { rotationZ = badgeRotation },
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.18f),
                border = androidx.compose.foundation.BorderStroke(
                    1.5.dp,
                    Brush.sweepGradient(
                        listOf(
                            Color(0xFFFCD34D),
                            Color(0xFFF59E0B),
                            Color.White.copy(alpha = 0.9f),
                            Color(0xFFFCD34D)
                        )
                    )
                ),
                shadowElevation = 8.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isComplete) Icons.Default.CheckCircle else Icons.Default.Shield,
                        contentDescription = "Security Verification",
                        tint = if (isComplete) Color(0xFF10B981) else Color(0xFFFCD34D),
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Title & Subtitle
        Text(
            text = "Mobile Verification",
            fontSize = 21.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            letterSpacing = 0.3.sp
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Enter the 6-digit OTP code sent to",
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.8f)
        )

        // Clean masked phone number badge
        val cleanDigits = phoneNumber.filter { it.isDigit() }
        val cleanDisplayPhone = if (cleanDigits.length >= 10) {
            val last10 = cleanDigits.takeLast(10)
            "+91 ${last10.take(2)}•••• ••${last10.takeLast(4)}"
        } else if (phoneNumber.isNotBlank()) {
            phoneNumber
        } else {
            "your registered mobile"
        }

        Surface(
            modifier = Modifier.padding(top = 6.dp),
            shape = RoundedCornerShape(percent = 50),
            color = Color.White.copy(alpha = 0.15f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
        ) {
            Text(
                text = cleanDisplayPhone,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFCD34D),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- INTERACTIVE 6-DIGIT GLASSMORPHIC SLOTS GRID ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = shakeOffset.value }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    // Back closes the keyboard but leaves the hidden field focused, so asking for
                    // focus again does nothing; the keyboard has to be asked for directly.
                    focusRequester.requestFocus()
                    keyboardController?.show()
                },
            contentAlignment = Alignment.Center
        ) {
            // Hidden native TextField capturing input
            BasicTextField(
                value = otpValue,
                onValueChange = { input ->
                    val filtered = input.filter { it.isDigit() }.take(otpLength)
                    onOtpChange(filtered)
                    if (filtered.length == otpLength) {
                        onVerifyTriggered(filtered)
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (otpValue.length == otpLength) {
                            onVerifyTriggered(otpValue)
                        }
                    }
                ),
                modifier = Modifier
                    .size(1.dp)
                    .alpha(0f)
                    .focusRequester(focusRequester)
            )

            // 6 Distinct Luxury Digit Slots
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until otpLength) {
                    val char = otpValue.getOrNull(i)?.toString() ?: ""
                    val isCurrentSlot = (i == otpValue.length) && (otpValue.length < otpLength)
                    val isFilled = char.isNotEmpty()

                    val slotScale by animateFloatAsState(
                        targetValue = if (isFilled) 1.05f else if (isCurrentSlot) 1.02f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "slotScale_$i"
                    )

                    val slotBorderColor = when {
                        hasError -> Color(0xFFFF5252)
                        isCurrentSlot -> Color(0xFFF59E0B) // Glowing Amber active focus
                        isFilled -> Color(0xFFFCD34D)
                        else -> Color.White.copy(alpha = 0.25f)
                    }

                    val slotBgColor = when {
                        hasError -> Color(0xFFFF5252).copy(alpha = 0.20f)
                        isCurrentSlot -> Color.White.copy(alpha = 0.22f)
                        isFilled -> Color.White.copy(alpha = 0.28f)
                        else -> Color.White.copy(alpha = 0.12f)
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .scale(slotScale),
                        shape = RoundedCornerShape(14.dp),
                        color = slotBgColor,
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (isCurrentSlot || hasError || isFilled) 2.dp else 1.2.dp,
                            color = slotBorderColor
                        ),
                        shadowElevation = if (isCurrentSlot || isFilled) 6.dp else 1.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isFilled) {
                                Text(
                                    text = char,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color.White,
                                    textAlign = TextAlign.Center
                                )
                            } else if (isCurrentSlot) {
                                // Pulsing Amber Cursor
                                Box(
                                    modifier = Modifier
                                        .width(2.5.dp)
                                        .height(22.dp)
                                        .alpha(cursorAlpha)
                                        .background(Color(0xFFFCD34D), shape = RoundedCornerShape(1.dp))
                                )
                            } else {
                                // Subtle placeholder dot
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .alpha(0.35f)
                                        .background(Color.White, shape = CircleShape)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Error message row
        AnimatedVisibility(
            visible = hasError,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            Text(
                text = errorMessage ?: "",
                color = Color(0xFFFF6B6B),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp, start = 8.dp, end = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // --- CIRCULAR ANIMATED RESEND COUNTDOWN ---
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (canResend) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .clickable {
                            canResend = false
                            countdownSeconds = 30
                            onResendClick()
                        }
                        .background(Color.White.copy(alpha = 0.15f))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Resend OTP",
                        tint = Color(0xFFFCD34D),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Didn't receive code? Resend OTP",
                        color = Color(0xFFFCD34D),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                // Progress circle with countdown seconds
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(26.dp)
                ) {
                    val progress = countdownSeconds / 30f
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawArc(
                            color = Color.White.copy(alpha = 0.2f),
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = Color(0xFFF59E0B),
                            startAngle = -90f,
                            sweepAngle = 360f * progress,
                            useCenter = false,
                            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    Text(
                        text = "$countdownSeconds",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = "Resend OTP in 00:${if (countdownSeconds < 10) "0$countdownSeconds" else "$countdownSeconds"}",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        if (showVerifyButton) {
            Spacer(modifier = Modifier.height(22.dp))

            // --- PRIMARY VERIFY BUTTON ---
            Button(
                onClick = {
                    if (otpValue.length == otpLength) {
                        onVerifyTriggered(otpValue)
                    }
                },
                enabled = !isLoading && isComplete,
                shape = RoundedCornerShape(percent = 50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF0C1018),
                    disabledContainerColor = Color.White.copy(alpha = 0.3f),
                    disabledContentColor = Color.White.copy(alpha = 0.45f)
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = if (isComplete) 8.dp else 0.dp,
                    pressedElevation = 2.dp
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color(0xFF0C1018),
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = verifyButtonText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.4.sp
                        )
                    }
                }
            }
        }
    }
}
