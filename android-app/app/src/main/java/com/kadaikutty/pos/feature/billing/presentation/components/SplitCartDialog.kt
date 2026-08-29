package com.kadaikutty.pos.feature.billing.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kadaikutty.pos.feature.billing.domain.SaleLine

@Composable
fun SplitCartDialog(
    showDialog: Boolean,
    lines: List<SaleLine>,
    selectedItems: Set<String>,
    onDismiss: () -> Unit,
    onSelectionChange: (productId: String, isChecked: Boolean) -> Unit,
    onProceedToPay: () -> Unit
) {
    if (!showDialog) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Items for Split Bill", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(lines) { line ->
                    val isChecked = selectedItems.contains(line.productId)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Checkbox(
                            checked = isChecked, 
                            onCheckedChange = { onSelectionChange(line.productId, it) }
                        )
                        Text(line.productName, modifier = Modifier.weight(1f), fontSize = 14.sp)
                        Text(line.lineTotal.toString(), fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = selectedItems.isNotEmpty(),
                onClick = onProceedToPay
            ) { Text("Proceed to Pay") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
