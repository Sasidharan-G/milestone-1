package com.kadaikutty.pos.feature.subscription

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kadaikutty.pos.BuildConfig

// Deliberately simple, non-technical messaging (per product decision): a shop owner should only
// ever see "connect to the internet or contact your admin", never which internal rule tripped it.
@Composable
fun CloudAccessLockScreen(
    shopName: String,
    onRefreshStatus: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val masterContactPhone = BuildConfig.MASTER_SUPPORT_PHONE.trim()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F17))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0xFFF59E0B).copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFF59E0B)),
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.CloudOff, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(40.dp))
                }
            }

            Text("Access Expired", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)

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
                    Text(shopName.ifBlank { "Your Store" }, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF38BDF8))
                    Text(
                        "Please connect to the internet, or contact your admin to continue.",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Button(
                onClick = {
                    try {
                        if (masterContactPhone.isBlank()) {
                            android.widget.Toast.makeText(context, "Admin contact is not configured.", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$masterContactPhone")))
                        }
                    } catch (e: Exception) {}
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Phone, contentDescription = null, tint = Color.White)
                    Text("Call Admin", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            Button(
                onClick = {
                    try {
                        if (masterContactPhone.isBlank()) {
                            android.widget.Toast.makeText(context, "Admin contact is not configured.", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            val url = "https://api.whatsapp.com/send?phone=$masterContactPhone&text=Hello,%20my%20access%20has%20expired%20for%20${Uri.encode(shopName.ifBlank { "my store" })}"
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }
                    } catch (e: Exception) {}
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = Color.White)
                    Text("WhatsApp Admin", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            OutlinedButton(
                onClick = onRefreshStatus,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF475569))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Sync, contentDescription = null, tint = Color.White)
                    Text("I'm Connected — Check Now", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }

            Button(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = Color.White)
                    Text("Switch User / Logout", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
