package com.kadaikutty.pos.feature.subscription

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.razorpay.Checkout
import org.json.JSONObject

@Composable
fun PaymentScreen(price: Int) {
    val context = LocalContext.current
    val activity = context as? Activity

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF9FAFB))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "Complete Payment",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF111827),
            modifier = Modifier.padding(bottom = 32.dp)
        )

        UpiPaymentMethods(
            onUpiClick = { appPackage ->
                if (activity != null) {
                    startRazorpayCheckout(activity, price, appPackage)
                } else {
                    Toast.makeText(context, "Cannot start payment: Activity context missing", Toast.LENGTH_SHORT).show()
                }
            }
        )

        Spacer(modifier = Modifier.height(24.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        ) {
            Box(modifier = Modifier.weight(1f).height(1.dp).background(Color.LightGray))
            Text(
                text = "or pay using credit card", 
                color = Color.Gray, 
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Box(modifier = Modifier.weight(1f).height(1.dp).background(Color.LightGray))
        }
        Spacer(modifier = Modifier.height(24.dp))

        CreditCardForm(
            onSubmit = { _, _, _, _, _ ->
                if (activity != null) {
                    startRazorpayCheckout(activity, price)
                } else {
                    Toast.makeText(context, "Cannot start payment: Activity context missing", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

private fun startRazorpayCheckout(activity: Activity, amountInRupees: Int, upiAppPackage: String? = null) {
    val checkout = Checkout()
    checkout.setKeyID("rzp_test_TUk2idGOKiyUvP") 
    
    try {
        val options = JSONObject()
        options.put("name", "Kadaikutty POS")
        options.put("description", "Subscription for Cloud Sync")
        options.put("theme.color", "#2563EB")
        options.put("currency", "INR")
        
        if (upiAppPackage != null) {
            if (upiAppPackage.contains(".")) {
                options.put("method", "upi")
                val upiOptions = JSONObject()
                upiOptions.put("flow", "intent")
                upiOptions.put("app", upiAppPackage)
                options.put("upi", upiOptions)
            }
        }
        
        // Convert to paisa (smallest unit)
        options.put("amount", (amountInRupees * 100).toString())
        
        val retryObj = JSONObject()
        retryObj.put("enabled", true)
        retryObj.put("max_count", 4)
        options.put("retry", retryObj)

        checkout.open(activity, options)
        
    } catch (e: Exception) {
        Toast.makeText(activity, "Error in payment: ${e.message}", Toast.LENGTH_SHORT).show()
        e.printStackTrace()
    }
}

@Composable
fun UpiPaymentMethods(onUpiClick: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        UpiButton(
            name = "PayPal",
            imageRes = com.kadaikutty.pos.R.raw.paypal,
            onClick = { onUpiClick("paypal") },
            modifier = Modifier.weight(1f)
        )
        UpiButton(
            name = "Apple Pay",
            imageRes = android.R.drawable.ic_dialog_info,
            onClick = { onUpiClick("applepay") },
            modifier = Modifier.weight(1f)
        )
        UpiButton(
            name = "GPay",
            imageRes = com.kadaikutty.pos.R.raw.googlepay,
            onClick = { onUpiClick("com.google.android.apps.nbu.paisa.user") },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun UpiButton(name: String, imageRes: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val imageLoader = remember(context) {
        coil.ImageLoader.Builder(context)
            .components {
                add(coil.decode.SvgDecoder.Factory())
            }
            .build()
    }
    
    Box(
        modifier = modifier
            .height(56.dp)
            .border(1.dp, Color.LightGray, RoundedCornerShape(12.dp))
            .background(Color.White, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        coil.compose.AsyncImage(
            model = coil.request.ImageRequest.Builder(context)
                .data(imageRes)
                .build(),
            imageLoader = imageLoader,
            contentDescription = name,
            modifier = Modifier.height(32.dp) // Adjusted sizing here as requested by user
        )
    }
}
