package com.kadaikutty.pos.feature.subscription

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kadaikutty.pos.core.license.LicenseEntity

@Composable
fun LicenseExpiredLockScreen(
    license: LicenseEntity?,
    shopName: String,
    onRefreshStatus: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    var showMasterPinDialog by remember { mutableStateOf(false) }
    var enteredPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }

    val masterContactPhone = "+919840000000" // Master Admin Support

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F17))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Icon Badge
            Surface(
                shape = CircleShape,
                color = Color(0xFFEF4444).copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFEF4444)),
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (license?.licenseStatus == "REVOKED") Icons.Default.Block else Icons.Default.LockClock,
                        contentDescription = null,
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            // Title
            Text(
                text = if (license?.licenseStatus == "REVOKED") "Access Suspended" else "License Expired",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )

            // Shop Name Card
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF162238),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = shopName.ifBlank { license?.businessName ?: "Your Store" },
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color(0xFF38BDF8)
                    )
                    Text(
                        text = when {
                            license?.licenseStatus == "REVOKED" -> "Your access has been suspended by Master Admin."
                            license?.licenseStatus == "TRIAL" -> "Your 2-Day Free Trial period has ended."
                            else -> "Your 1-Year KadaiKutty POS License has expired."
                        },
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Text(
                text = "To activate or renew your 1-Year Complete POS & Cloud License, please contact the Master Admin below:",
                fontSize = 13.sp,
                color = Color(0xFFCBD5E1),
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            // Call Master Admin Button
            Button(
                onClick = {
                    try {
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$masterContactPhone"))
                        context.startActivity(intent)
                    } catch (e: Exception) {}
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Phone, contentDescription = null, tint = Color.White)
                    Text("Call Master Admin to Renew", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            // WhatsApp Master Admin Button
            Button(
                onClick = {
                    try {
                        val shop = shopName.ifBlank { license?.businessName ?: "My Shop" }
                        val url = "https://api.whatsapp.com/send?phone=$masterContactPhone&text=Hello%20Master%20Admin,%20I%20want%20to%20renew%20the%20KadaiKutty%20POS%20License%20for%20my%20store:%20${Uri.encode(shop)}"
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    } catch (e: Exception) {}
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = Color.White)
                    Text("WhatsApp Master Admin", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            // Check Status Button (Re-sync)
            OutlinedButton(
                onClick = onRefreshStatus,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF475569))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Sync, contentDescription = null, tint = Color.White)
                    Text("Already Paid? Check Status Now", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }

            // Logout Button
            Button(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.ExitToApp, contentDescription = null, tint = Color.White)
                    Text("Switch User / Logout", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
