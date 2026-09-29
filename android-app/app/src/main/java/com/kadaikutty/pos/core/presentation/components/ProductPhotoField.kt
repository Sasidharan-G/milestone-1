package com.kadaikutty.pos.core.presentation.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import java.io.File

/**
 * The "add a photo" field shared by Create Product and Edit Product. [imageModel] is whatever
 * Coil should render right now - a local [File] (fastest, works offline), a remote URL [String]
 * (another device's photo, not yet downloaded here), or null for "no photo yet". Deciding which of
 * those to pass is the caller's job, because only the caller knows the product's id.
 *
 * A big labelled square reads as "tap here to add a picture" without needing any text at all,
 * which is the point for a shop owner who may not read English labels comfortably.
 */
@Composable
fun ProductPhotoField(
    imageModel: Any?,
    onPickedFromGallery: (Uri) -> Unit,
    onCapturedFromCamera: (Uri) -> Unit,
    onRemove: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showChooser by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPickedFromGallery(uri)
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) onCapturedFromCamera(uri)
    }

    if (showChooser) {
        AlertDialog(
            onDismissRequest = { showChooser = false },
            title = { Text("Add Product Photo", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ChooserRow(icon = Icons.Default.CameraAlt, label = "Take a Photo") {
                        showChooser = false
                        val file = com.kadaikutty.pos.core.common.ProductImageStore.newTempFile(context)
                        val uri = FileProvider.getUriForFile(context, "com.kadaikutty.pos.fileprovider", file)
                        pendingCameraUri = uri
                        cameraLauncher.launch(uri)
                    }
                    ChooserRow(icon = Icons.Default.Photo, label = "Choose from Gallery") {
                        showChooser = false
                        galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showChooser = false }) { Text("Cancel") }
            }
        )
    }

    Box(modifier = modifier.size(96.dp)) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { showChooser = true },
            contentAlignment = Alignment.Center
        ) {
            if (imageModel != null) {
                AsyncImage(
                    model = imageModel,
                    contentDescription = "Product photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(96.dp).clip(RoundedCornerShape(16.dp))
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Icon(
                        imageVector = Icons.Default.AddAPhoto,
                        contentDescription = "Add product photo",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Text("Add Photo", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                }
            }
        }

        if (imageModel != null && onRemove != null) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.TopEnd)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error)
                    .clickable { onRemove() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, contentDescription = "Remove photo", tint = MaterialTheme.colorScheme.onError, modifier = Modifier.size(14.dp))
            }
        }
    }
}

/** Read-only thumbnail for a product list row. Local photo first (instant, works offline), then
 *  the cloud URL for a product photographed on another device, then a plain placeholder icon. */
@Composable
fun ProductThumbnail(imageModel: Any?, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 44.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (imageModel != null) {
            AsyncImage(
                model = imageModel,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(RoundedCornerShape(10.dp))
            )
        } else {
            Icon(
                imageVector = Icons.Default.Photo,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(size / 2)
            )
        }
    }
}

@Composable
private fun ChooserRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp)
    ) {
        Column {
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Text(label, fontSize = 15.sp)
            }
        }
    }
}
