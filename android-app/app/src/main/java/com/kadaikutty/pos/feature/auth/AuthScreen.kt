package com.kadaikutty.pos.feature.auth

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
            .background(Color(0xFF5C151A)), // Deep Maroon
        contentAlignment = Alignment.Center
    ) {
        // Pattern background
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(id = com.kadaikutty.pos.R.drawable.shopping_pattern),
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 0.3f }
        )

        // Glassmorphism Card
        Card(
            modifier = Modifier
                .statusBarsPadding()
                .navigationBarsPadding()
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .padding(16.dp)
                .graphicsLayer {
                    rotationY = rotation
                    cameraDistance = 12f * density
                }
                .animateContentSize(animationSpec = tween(durationMillis = 600))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(24.dp)
                ),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF5C151A) // Solid maroon to block pattern inside the card
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
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
                    // Back side: Register
                    Box(modifier = Modifier.graphicsLayer {
                        rotationY = 180f
                        alpha = (rotation - 90f) / 90f
                    }) {
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
        color = Color(0xFF8E2128), // Matches maroon theme, slightly lighter than background
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
