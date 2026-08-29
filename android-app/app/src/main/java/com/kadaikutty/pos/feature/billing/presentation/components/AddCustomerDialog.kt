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
import androidx.compose.ui.platform.LocalContext

@Composable
fun AddCustomerDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    onSaveCustomer: (name: String, phone: String, address: String, openingDueMinorUnits: Long) -> Unit
) {
    if (!showDialog) return

    val context = LocalContext.current
    var newCustName by remember { mutableStateOf("") }
    var newCustPhone by remember { mutableStateOf("") }
    var newCustAddress by remember { mutableStateOf("") }
    var newCustOpeningDue by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Customer", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = newCustName,
                    onValueChange = { newCustName = it },
                    label = { Text("Customer Name *") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newCustPhone,
                    onValueChange = { newCustPhone = it },
                    label = { Text("Phone Number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newCustAddress,
                    onValueChange = { newCustAddress = it },
                    label = { Text("Address (Optional)") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newCustOpeningDue,
                    onValueChange = { newCustOpeningDue = it },
                    label = { Text("Previous / Opening Due (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (newCustName.isBlank()) {
                        android.widget.Toast.makeText(context, "Please enter customer name", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        val openingDueVal = ((newCustOpeningDue.toDoubleOrNull() ?: 0.0) * 100).toLong()
                        onSaveCustomer(newCustName, newCustPhone, newCustAddress, openingDueVal)
                        newCustName = ""
                        newCustPhone = ""
                        newCustAddress = ""
                        newCustOpeningDue = ""
                    }
                }
            ) {
                Text("Save & Select")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
