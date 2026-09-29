package com.kadaikutty.pos.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kadaikutty.pos.R

/**
 * Quiet "made with" brand strip for the bottom of long screens: a thin divider, the small
 * app logo, the app name and version. Kept muted so it never competes with the shop's own content.
 */
@Composable
fun BrandFooter(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
    }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            HorizontalDivider(modifier = Modifier.width(48.dp), color = muted.copy(alpha = 0.25f))
            Spacer(Modifier.width(10.dp))
            Image(
                painter = painterResource(R.drawable.brand_logo),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(9.dp))
            )
            Spacer(Modifier.width(10.dp))
            HorizontalDivider(modifier = Modifier.width(48.dp), color = muted.copy(alpha = 0.25f))
        }
        Text(
            text = stringResource(R.string.app_name),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = muted,
            modifier = Modifier.padding(top = 6.dp)
        )
        Text(
            text = if (version.isNullOrBlank()) "Mobile Billing App" else "Mobile Billing App · v$version",
            fontSize = 10.sp,
            color = muted.copy(alpha = 0.7f)
        )
    }
}
