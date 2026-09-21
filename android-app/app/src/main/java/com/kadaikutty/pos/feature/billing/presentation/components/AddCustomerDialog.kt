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
import com.kadaikutty.pos.core.common.CheckoutMath

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
    var isSubmitting by remember { mutableStateOf(false) }

    LaunchedEffect(showDialog) {
        if (showDialog) {
            isSubmitting = false
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (!isSubmitting) onDismiss()
        },
        title = { Text("Add New Customer", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = newCustName,
                    onValueChange = { newCustName = com.kadaikutty.pos.core.common.InputRules.name(it) },
                    label = { Text("Customer Name *") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newCustPhone,
                    onValueChange = { newCustPhone = com.kadaikutty.pos.core.common.InputRules.phone(it) },
                    label = { Text("Phone Number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newCustAddress,
                    onValueChange = { newCustAddress = com.kadaikutty.pos.core.common.InputRules.text(it) },
                    label = { Text("Address (Optional)") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newCustOpeningDue,
                    onValueChange = { newCustOpeningDue = com.kadaikutty.pos.core.common.InputRules.money(it) },
                    label = { Text("Previous / Opening Due (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !isSubmitting,
                onClick = {
                    if (isSubmitting) return@Button
                    val problem = com.kadaikutty.pos.core.common.InputRules.firstError(
                        com.kadaikutty.pos.core.common.InputRules.checkName(newCustName, "Customer name"),
                        com.kadaikutty.pos.core.common.InputRules.checkPhone(newCustPhone),
                        com.kadaikutty.pos.core.common.InputRules.checkMoney(newCustOpeningDue, "Opening due", required = false, allowZero = true)
                    )
                    if (problem != null) {
                        android.widget.Toast.makeText(context, problem, android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        isSubmitting = true
                        val openingDueVal = CheckoutMath.rupeesToMinorUnits(newCustOpeningDue.toDoubleOrNull() ?: 0.0)
                        onSaveCustomer(newCustName, newCustPhone, newCustAddress, openingDueVal)
                        newCustName = ""
                        newCustPhone = ""
                        newCustAddress = ""
                        newCustOpeningDue = ""
                    }
                }
            ) {
                Text(if (isSubmitting) "Saving..." else "Save & Select")
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isSubmitting,
                onClick = onDismiss
            ) {
                Text("Cancel")
            }
        }
    )
}
