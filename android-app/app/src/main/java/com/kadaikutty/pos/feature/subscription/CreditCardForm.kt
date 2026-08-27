package com.kadaikutty.pos.feature.subscription

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditCardForm(
    modifier: Modifier = Modifier,
    onSubmit: (cardNumber: String, holder: String, month: String, year: String, cvv: String) -> Unit
) {
    var cardNumber by remember { mutableStateOf("") }
    var cardHolder by remember { mutableStateOf("") }
    var expiryMonth by remember { mutableStateOf("") }
    var expiryYear by remember { mutableStateOf("") }
    var cvv by remember { mutableStateOf("") }
    
    var isCvvFocused by remember { mutableStateOf(false) }

    val rotationY by animateFloatAsState(
        targetValue = if (isCvvFocused) 180f else 0f,
        animationSpec = tween(durationMillis = 600),
        label = "card_flip"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- Animated Card UI ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
                .padding(bottom = 24.dp)
                .graphicsLayer {
                    this.rotationY = rotationY
                    cameraDistance = 12f * density
                }
        ) {
            if (rotationY <= 90f) {
                // FRONT OF CARD
                CardFrontView(cardNumber, cardHolder, expiryMonth, expiryYear)
            } else {
                // BACK OF CARD (Mirrored rotation correction)
                CardBackView(cvv, rotationY)
            }
        }

        // --- Form Inputs ---
        CardFormInputs(
            cardNumber = cardNumber,
            onCardNumberChange = { if (it.length <= 16) cardNumber = it.filter { char -> char.isDigit() } },
            cardHolder = cardHolder,
            onCardHolderChange = { cardHolder = it.uppercase() },
            expiryMonth = expiryMonth,
            onExpiryMonthChange = { if (it.length <= 2) expiryMonth = it.filter { char -> char.isDigit() } },
            expiryYear = expiryYear,
            onExpiryYearChange = { if (it.length <= 4) expiryYear = it.filter { char -> char.isDigit() } },
            cvv = cvv,
            onCvvChange = { if (it.length <= 4) cvv = it.filter { char -> char.isDigit() } },
            onCvvFocusChange = { isCvvFocused = it },
            onSubmit = { onSubmit(cardNumber, cardHolder, expiryMonth, expiryYear, cvv) }
        )
    }
}

@Composable
fun CardFrontView(cardNumber: String, holder: String, month: String, year: String) {
    val formattedNumber = cardNumber.chunked(4).joinToString(" ").padEnd(19, '#')
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .shadow(16.dp, RoundedCornerShape(20.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF323941), Color(0xFF061018))
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(24.dp)
    ) {
        // Decorative rings
        Box(modifier = Modifier
            .size(300.dp)
            .offset(x = (-100).dp, y = (-100).dp)
            .border(16.dp, Color(0xFFFF6BE7).copy(alpha = 0.5f), RoundedCornerShape(150.dp))
        )
        
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("CreditCard", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                
                val context = LocalContext.current
                val imageLoader = remember(context) {
                    coil.ImageLoader.Builder(context)
                        .components {
                            add(coil.decode.SvgDecoder.Factory())
                        }
                        .build()
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    coil.compose.AsyncImage(
                        model = coil.request.ImageRequest.Builder(context)
                            .data(com.kadaikutty.pos.R.raw.visa)
                            .build(),
                        imageLoader = imageLoader,
                        contentDescription = "Visa",
                        modifier = Modifier.height(32.dp)
                    )
                    coil.compose.AsyncImage(
                        model = coil.request.ImageRequest.Builder(context)
                            .data(com.kadaikutty.pos.R.raw.mastercard)
                            .build(),
                        imageLoader = imageLoader,
                        contentDescription = "Mastercard",
                        modifier = Modifier.height(40.dp)
                    )
                }
            }
            
            Text(
                text = formattedNumber,
                color = Color.White,
                fontSize = 24.sp,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Medium
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("CARD HOLDER", color = Color.LightGray, fontSize = 10.sp)
                    Text(holder.ifEmpty { "JANE DOE" }, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("EXPIRES", color = Color.LightGray, fontSize = 10.sp)
                    Text("${month.ifEmpty { "MM" }}/${year.ifEmpty { "YY" }}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun CardBackView(cvv: String, rotationY: Float) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .shadow(16.dp, RoundedCornerShape(20.dp))
            .graphicsLayer {
                // Cancel out the Y rotation on the back content so it's not mirrored
                this.rotationY = 180f
            }
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF323941), Color(0xFF061018))
                ),
                shape = RoundedCornerShape(20.dp)
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.height(24.dp))
            Box(modifier = Modifier.fillMaxWidth().height(40.dp).background(Color.DarkGray))
            Spacer(modifier = Modifier.height(24.dp))
            Column(
                modifier = Modifier.padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text("CVV", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .background(Color.White, RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        text = "*".repeat(cvv.length),
                        color = Color.Black,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun CardFormInputs(
    cardNumber: String, onCardNumberChange: (String) -> Unit,
    cardHolder: String, onCardHolderChange: (String) -> Unit,
    expiryMonth: String, onExpiryMonthChange: (String) -> Unit,
    expiryYear: String, onExpiryYearChange: (String) -> Unit,
    cvv: String, onCvvChange: (String) -> Unit,
    onCvvFocusChange: (Boolean) -> Unit,
    onSubmit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(16.dp))
            .border(1.dp, Color.LightGray, RoundedCornerShape(16.dp))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val textFieldColors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFF2563EB),
            focusedLabelColor = Color(0xFF2563EB),
            unfocusedBorderColor = Color.LightGray
        )
        
        OutlinedTextField(
            value = cardNumber,
            onValueChange = onCardNumberChange,
            label = { Text("Card Number") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = textFieldColors
        )
        
        OutlinedTextField(
            value = cardHolder,
            onValueChange = onCardHolderChange,
            label = { Text("Card Holder full name") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = textFieldColors
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = expiryMonth,
                onValueChange = onExpiryMonthChange,
                label = { Text("MM") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = textFieldColors
            )
            OutlinedTextField(
                value = expiryYear,
                onValueChange = onExpiryYearChange,
                label = { Text("YY") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = textFieldColors
            )
            OutlinedTextField(
                value = cvv,
                onValueChange = { onCvvChange(it) },
                label = { Text("CVV") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { state ->
                        onCvvFocusChange(state.isFocused)
                    },
                shape = RoundedCornerShape(12.dp),
                colors = textFieldColors
            )
        }
        
        val isValid = cardNumber.length >= 13 && cardHolder.length >= 2 && expiryMonth.isNotEmpty() && expiryYear.isNotEmpty() && cvv.length >= 3
        Button(
            onClick = onSubmit,
            enabled = isValid,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
        ) {
            Text("Checkout", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}
