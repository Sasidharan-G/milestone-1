package com.kadaikutty.pos.feature.billing.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kadaikutty.pos.feature.billing.domain.SaleLine
import java.util.Locale

@Composable
fun EditQuantityDialog(
    line: SaleLine,
    onDismiss: () -> Unit,
    onUpdateQuantity: (productId: String, newQty: Long) -> Unit,
    onRemoveLine: (productId: String) -> Unit
) {
    var inputQty by remember(line) {
        val isDec = line.unitType == "KG" || line.unitType == "LITER"
        mutableStateOf(if (isDec) String.format(Locale.US, "%.3f", line.quantity / 1000.0) else line.quantity.toString())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Quantity - ${line.productName}", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Unit Price: ${line.unitPrice}", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                OutlinedTextField(
                    value = inputQty,
                    onValueChange = { inputQty = it },
                    label = { Text(if (line.unitType == "KG" || line.unitType == "LITER") "Quantity (Kg/L)" else "Quantity (Pieces)") },
                    keyboardOptions = KeyboardOptions(keyboardType = if (line.unitType == "KG" || line.unitType == "LITER") KeyboardType.Decimal else KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Quick Increment Buttons
                if (line.unitType == "PIECE") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 2, 5, 10).forEach { addVal ->
                            FilterChip(
                                selected = false,
                                onClick = {
                                    val cur = inputQty.toLongOrNull() ?: 0L
                                    inputQty = (cur + addVal).toString()
                                },
                                label = { Text("+$addVal") }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val isDec = line.unitType == "KG" || line.unitType == "LITER"
                    val parsed = if (isDec) {
                        val d = inputQty.toDoubleOrNull()
                        if (d != null && d > 0) (d * 1000).toLong() else null
                    } else inputQty.toLongOrNull()

                    if (parsed != null && parsed > 0) {
                        onUpdateQuantity(line.productId, parsed)
                    } else if (parsed == 0L) {
                        onRemoveLine(line.productId)
                    }
                }
            ) {
                Text("Update Quantity")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
