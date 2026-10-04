package com.kadaikutty.pos.feature.auth

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.*
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import kotlinx.coroutines.delay
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Close
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    loginViewModel: LoginViewModel,
    registerViewModel: RegisterViewModel,
    onLoginSuccess: () -> Unit,
    onOpenMasterControl: () -> Unit
) {
    var isRegisterFlipped by remember { mutableStateOf(false) }

    val rotation by animateFloatAsState(
        targetValue = if (isRegisterFlipped) 180f else 0f,
        animationSpec = tween(durationMillis = 600),
        label = "FlipAnimation"
    )

    val loginState by loginViewModel.state.collectAsState()
    val registerState by registerViewModel.state.collectAsState()

    // Global Animation State. The action starts at once and the animation plays over the wait;
    // it used to start only after the animation finished, which added a fixed 3 seconds to every
    // sign-in and registration before the request was even sent.
    var showLoadingOverlay by remember { mutableStateOf(false) }
    val triggerAnimation = { action: () -> Unit ->
        if (!showLoadingOverlay) {
            showLoadingOverlay = true
            action()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A1B45)), // Deep navy-indigo brand background
        contentAlignment = Alignment.TopCenter
    ) {
        // Soft glow blobs, echoing the approved artifact design
        Box(
            modifier = Modifier
                .size(340.dp)
                .align(Alignment.TopEnd)
                .offset(x = 90.dp, y = (-140).dp)
                .background(
                    Brush.radialGradient(listOf(Color(0xFF1A2F6B), Color.Transparent)),
                    shape = androidx.compose.foundation.shape.CircleShape
                )
        )
        Box(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-110).dp, y = 110.dp)
                .background(
                    Brush.radialGradient(listOf(Color(0xFF132860), Color.Transparent)),
                    shape = androidx.compose.foundation.shape.CircleShape
                )
        )
        // Faint retail-icon texture (same pattern used across the app), low alpha over the dark ground
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(id = com.kadaikutty.pos.R.drawable.shopping_pattern),
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 0.12f }
        )

        // While the keyboard is up the brand header and the footer step aside: the card holds the fields
        // being typed into, and the header used to keep its full height and squeeze the card to a sliver.
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        val keyboardOpen = androidx.compose.foundation.layout.WindowInsets.isImeVisible
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .navigationBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = if (keyboardOpen) 12.dp else 48.dp, bottom = if (keyboardOpen) 8.dp else 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!keyboardOpen) {
            // Fixed brand header - stays put while the card behind it flips between Sign in / Register.
            // Uses the real app logo (same as the launcher icon), not the placeholder hand-drawn badge.
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(id = com.kadaikutty.pos.R.drawable.brand_logo),
                contentDescription = null,
                modifier = Modifier
                    .size(64.dp)
                    .shadow(elevation = 14.dp, shape = RoundedCornerShape(20.dp), spotColor = Color.Black.copy(alpha = 0.35f))
                    .clip(RoundedCornerShape(20.dp))
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row {
                Text("Kadaikutty", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = (-0.2).sp)
                Text(" POS", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF5A524), letterSpacing = (-0.2).sp)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "BILLING · INVENTORY · REPORTS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.6.sp,
                color = Color(0xFFB9C4E4)
            )

            Spacer(modifier = Modifier.height(28.dp))
            }

            // Flip card: white "Welcome back" panel on the front, the existing navy Register panel on the back
            Card(
                modifier = Modifier
                    // The card takes what is left after the header and the link below it, and scrolls
                    // inside. Without this the taller Register card used up the whole screen and the
                    // "Already have an account? Sign In" link under it was pushed out of sight.
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .widthIn(max = 400.dp)
                    .graphicsLayer {
                        rotationY = rotation
                        cameraDistance = 12f * density
                    }
                    .animateContentSize(animationSpec = tween(durationMillis = 600)),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 14.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (rotation <= 90f) {
                        // Front side: Login
                        Box(modifier = Modifier.graphicsLayer { alpha = 1f - (rotation / 90f) }) {
                            LoginScreenContent(
                                viewModel = loginViewModel,
                                onLoginSuccess = onLoginSuccess,
                                onNavigateToRegister = { isRegisterFlipped = true },
                                onOpenMasterControl = onOpenMasterControl,
                                triggerAnimation = triggerAnimation
                            )
                        }
                    } else {
                        // Back side: Register - kept as its original navy panel (untouched logic/styling),
                        // just nested inside the now-white flip card so it still reads as one solid card.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1E3A8A), RoundedCornerShape(28.dp))
                                .graphicsLayer {
                                    rotationY = 180f
                                    alpha = (rotation - 90f) / 90f
                                }
                        ) {
                            RegisterScreenContent(
                                viewModel = registerViewModel,
                                onNavigateBackToLogin = { isRegisterFlipped = false },
                                onRegisterSuccess = onLoginSuccess,
                                triggerAnimation = triggerAnimation
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(if (keyboardOpen) 4.dp else 18.dp))

            if (rotation <= 90f) {
              if (!keyboardOpen) {
                Row {
                    Text("New business? ", fontSize = 14.sp, color = Color(0xFFC5CEEA))
                    Text(
                        "Register your store",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                        modifier = Modifier.clickable { isRegisterFlipped = true }
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "v${com.kadaikutty.pos.BuildConfig.VERSION_NAME} · Made in India",
                    fontSize = 11.sp,
                    letterSpacing = 1.2.sp,
                    color = Color(0xFF8E9BC4)
                )
              }
            } else {
                // Lives here (outside the flipping card) rather than inside RegisterScreenContent -
                // a link on the rotated back face of the 3D flip card didn't reliably receive touch
                // input in testing, even though the fields on that same face focused correctly.
                Text(
                    "Already have an account? Sign In",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                    modifier = Modifier.clickable { isRegisterFlipped = false }
                )
            }
        }
    }

    if (showLoadingOverlay) {
        VehicleLoadingOverlay(onComplete = { showLoadingOverlay = false })
    }

    // Top Error Toast Overlay
    val currentError = if (!isRegisterFlipped) loginState.error else registerState.error

    Box(modifier = Modifier.fillMaxSize()) {
        androidx.compose.animation.AnimatedVisibility(
            visible = !currentError.isNullOrBlank(),
            enter = androidx.compose.animation.slideInVertically(initialOffsetY = { -it }) + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { -it }) + androidx.compose.animation.fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            currentError?.let { errorMsg ->
                TopErrorToast(
                    message = errorMsg,
                    onDismiss = {
                        if (!isRegisterFlipped) {
                            loginViewModel.clearError()
                        } else {
                            registerViewModel.clearError()
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun TopErrorToast(message: String, onDismiss: () -> Unit) {
    // Long enough to read a two-line message; the X still closes it sooner.
    LaunchedEffect(message) {
        kotlinx.coroutines.delay(5000)
        onDismiss()
    }

    androidx.compose.material3.Surface(
        color = com.kadaikutty.pos.core.ui.theme.CoralError, // An error toast should read as an error, not just another brand-coloured card
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            androidx.compose.material3.Text(
                text = message,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            androidx.compose.material3.IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(24.dp)
            ) {
                androidx.compose.material3.Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun VehicleLoadingOverlay(onComplete: () -> Unit = {}) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    
    // Vehicle X offset
    val offsetX = remember { androidx.compose.animation.core.Animatable(-screenWidth.value) }
    
    // Wheel rotation
    val wheelRotation = remember { androidx.compose.animation.core.Animatable(0f) }
    
    // Wobble
    val wobbleZ = remember { androidx.compose.animation.core.Animatable(0f) }

    LaunchedEffect(Unit) {
        // Run wheel rotation infinitely
        launch {
            wheelRotation.animateTo(
                targetValue = 360f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    animation = androidx.compose.animation.core.tween(400, easing = androidx.compose.animation.core.LinearEasing),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Restart
                )
            )
        }
        
        // Wobble when moving
        launch {
            wobbleZ.animateTo(
                targetValue = 3f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    animation = androidx.compose.animation.core.tween(150, easing = androidx.compose.animation.core.LinearEasing),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                )
            )
        }

        // Phase 1: Slide to center (600ms)
        offsetX.animateTo(
            targetValue = 0f,
            animationSpec = androidx.compose.animation.core.tween(durationMillis = 600, easing = androidx.compose.animation.core.LinearOutSlowInEasing)
        )
        
        // Phase 2: Stay in center (400ms)
        kotlinx.coroutines.delay(400)
        
        // Phase 3: Slide out to right (600ms)
        offsetX.animateTo(
            targetValue = screenWidth.value + 100f,
            animationSpec = androidx.compose.animation.core.tween(durationMillis = 600, easing = androidx.compose.animation.core.FastOutLinearInEasing)
        )
        
        onComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            // Swallows taps so the form underneath cannot be pressed twice while the request runs.
            .pointerInput(Unit) {},
        contentAlignment = Alignment.Center
    ) {
        // Speed lines behind vehicle
        Box(
            modifier = Modifier
                .offset(x = offsetX.value.dp - 80.dp, y = 20.dp)
                .width(60.dp)
                .height(4.dp)
                .background(Color.White.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
        )
        Box(
            modifier = Modifier
                .offset(x = offsetX.value.dp - 120.dp, y = -10.dp)
                .width(40.dp)
                .height(3.dp)
                .background(Color.White.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
        )
        Box(
            modifier = Modifier
                .offset(x = offsetX.value.dp - 90.dp, y = 40.dp)
                .width(50.dp)
                .height(2.dp)
                .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(1.dp))
        )

        // Vehicle container
        Box(
            modifier = Modifier
                .offset(x = offsetX.value.dp)
                .graphicsLayer { rotationZ = wobbleZ.value }
        ) {
            Image(
                painter = painterResource(id = com.kadaikutty.pos.R.drawable.ic_loading_vehicle),
                contentDescription = "Loading Vehicle",
                modifier = Modifier.size(120.dp)
            )
        }
    }
}
