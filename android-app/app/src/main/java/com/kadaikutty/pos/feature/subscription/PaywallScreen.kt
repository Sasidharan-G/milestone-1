package com.kadaikutty.pos.feature.subscription

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PaywallScreen(onSimulatePayment: (String) -> Unit) {
    val context = LocalContext.current
    val imageLoader = remember<ImageLoader>(context) {
        ImageLoader.Builder(context)
            .components {
                add(SvgDecoder.Factory())
            }
            .build()
    }

    var isYearly by remember { mutableStateOf(false) }

    val targetPrice = if (isYearly) 2999 else 299
    val originalPrice = if (isYearly) 4349 else 433

    val animatedPrice = remember { androidx.compose.animation.core.Animatable(433f) }

    LaunchedEffect(isYearly) {
        animatedPrice.snapTo(originalPrice.toFloat())
        animatedPrice.animateTo(
            targetValue = targetPrice.toFloat(),
            animationSpec = tween(durationMillis = 500, easing = androidx.compose.animation.core.FastOutSlowInEasing)
        )
    }

    val features = listOf(
        Pair("Unlimited Sales & Bills", com.kadaikutty.pos.R.raw.basket),
        Pair("Cloud Backup & Sync", com.kadaikutty.pos.R.raw.upload),
        Pair("Multiple Device Support", com.kadaikutty.pos.R.raw.phone),
        Pair("Bluetooth & USB Printing", com.kadaikutty.pos.R.raw.archive_lock),
        Pair("Advanced Analytics & Reports", com.kadaikutty.pos.R.raw.stat)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFFF9FAFB), Color(0xFFE8EBFF)),
                    radius = 1500f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp)
                .shadow(16.dp, RoundedCornerShape(32.dp)),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2563EB))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FlashOn,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Time to connect",
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Title
                Text(
                    text = "Let's get started",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Subtitle
                Text(
                    text = "Get KadaiKutty Subscription to save time, money and profit",
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 32.dp)
                )

                // Features
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp)
                ) {
                    Text(
                        text = "More access",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    features.forEach { (featureText, featureIcon) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            Box(
                                modifier = Modifier.size(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(featureIcon)
                                        .build(),
                                    imageLoader = imageLoader,
                                    contentDescription = null,
                                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.White),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = featureText,
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 15.sp
                            )
                        }
                    }
                }

                // Pricing
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Custom Pricing Switch
                    PricingSwitch(
                        isYearly = isYearly,
                        onSwitch = { isYearly = it }
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Price Display
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp)
                    ) {
                        Text(
                            text = "₹",
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "${animatedPrice.value.toInt()}",
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = "₹$originalPrice",
                            fontSize = 18.sp,
                            color = Color.White.copy(alpha = 0.6f),
                            textDecoration = TextDecoration.LineThrough
                        )
                    }

                    Button(
                        onClick = { onSimulatePayment(if (isYearly) "yearly" else "monthly") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .shadow(4.dp, RoundedCornerShape(28.dp)),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White
                        ),
                        shape = RoundedCornerShape(28.dp)
                    ) {
                        Text(
                            text = "Simulate Payment",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF2563EB)
                        )
                    }
                }
            }
        }
    }
    }

@Composable
fun PricingSwitch(
    isYearly: Boolean,
    onSwitch: (Boolean) -> Unit
) {
    val indicatorOffset by animateDpAsState(
        targetValue = if (isYearly) 150.dp else 0.dp,
        animationSpec = spring(stiffness = 500f, dampingRatio = 0.8f),
        label = "switch_offset"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(28.dp))
            .padding(4.dp)
    ) {
        // Animated Background for selection
        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .fillMaxWidth(0.5f)
                .fillMaxHeight()
                .shadow(2.dp, RoundedCornerShape(24.dp))
                .background(Color.White, RoundedCornerShape(24.dp))
        )

        Row(
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(24.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSwitch(false) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Monthly",
                    color = if (!isYearly) Color(0xFF2563EB) else Color.White,
                    fontWeight = FontWeight.Medium
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(24.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSwitch(true) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Yearly",
                    color = if (isYearly) Color(0xFF2563EB) else Color.White,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
