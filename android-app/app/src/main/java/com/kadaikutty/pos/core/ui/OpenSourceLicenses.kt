package com.kadaikutty.pos.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class Notice(val name: String, val license: String)

/** Third-party code and artwork shipped in the APK, with the licence each one is used under. */
private val NOTICES = listOf(
    Notice("Microsoft Fluent Emoji (Settings icons)", "MIT License, Copyright (c) Microsoft Corporation"),
    Notice("Android Jetpack: Compose, Room, WorkManager, CameraX, Paging, DataStore, Navigation, Biometric", "Apache License 2.0"),
    Notice("Kotlin, kotlinx.coroutines, kotlinx.serialization", "Apache License 2.0"),
    Notice("Dagger / Hilt", "Apache License 2.0"),
    Notice("Coil", "Apache License 2.0"),
    Notice("Guava", "Apache License 2.0"),
    Notice("ZXing Android Embedded", "Apache License 2.0"),
    Notice("RootBeer", "Apache License 2.0"),
    Notice("OkHttp, Okio", "Apache License 2.0"),
    Notice("Socket.IO Java client", "MIT License"),
    Notice("Sentry for Android", "MIT License"),
    Notice("SQLCipher for Android (Zetetic)", "BSD-style License"),
    Notice("Google ML Kit Barcode Scanning", "ML Kit Terms of Service"),
)

private const val MIT_TEXT = "Permission is hereby granted, free of charge, to any person obtaining a copy of this " +
    "software and associated documentation files (the \"Software\"), to deal in the Software without " +
    "restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, " +
    "sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished " +
    "to do so, subject to the following conditions: The above copyright notice and this permission notice " +
    "shall be included in all copies or substantial portions of the Software. THE SOFTWARE IS PROVIDED " +
    "\"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED."

private const val APACHE_TEXT = "Licensed under the Apache License, Version 2.0. You may obtain a copy of the " +
    "License at https://www.apache.org/licenses/LICENSE-2.0. Distributed on an \"AS IS\" BASIS, WITHOUT " +
    "WARRANTIES OR CONDITIONS OF ANY KIND."

@Composable
fun OpenSourceLicensesDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Open-source licences", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Kadaikutty POS is built with these open-source components:", fontSize = 13.sp)
                NOTICES.forEach { notice ->
                    Column {
                        Text(notice.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(notice.license, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider()
                Text("MIT License", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(MIT_TEXT, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Apache License 2.0", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(APACHE_TEXT, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
