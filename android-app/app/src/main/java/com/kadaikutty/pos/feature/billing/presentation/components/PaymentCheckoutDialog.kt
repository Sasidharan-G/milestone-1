package com.kadaikutty.pos.feature.billing.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kadaikutty.pos.core.common.Money

@Composable
fun PaymentCheckoutDialog(
    showDialog: Boolean,
    checkoutMode: String,
    finalPayableTotal: Money,
    customerCreditDue: Long,
    selectedCustomerId: String?,
    includePreviousDueInCheckout: Boolean,
    onIncludePreviousDueChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onPerformSave: (paymentMode: String, paidCash: Money, paidUpi: Money, creditApplied: Money) -> Unit,
    onError: (String) -> Unit
) {
    if (!showDialog) return

    var isSplitMode by remember { mutableStateOf(false) }
    var cashInput by remember { mutableStateOf("") }
    var upiInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { 
            onDismiss()
            isSplitMode = false
            cashInput = ""
            upiInput = ""
        },
        title = { Text(if (checkoutMode == "SPLIT_CART") "Payment for Split Cart" else "Payment & Checkout", fontWeight = FontWeight.Bold) },
        text = { 
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (customerCreditDue > 0L && selectedCustomerId != null && selectedCustomerId != "online") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Previous Due: ${Money(customerCreditDue)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                                Text(if (includePreviousDueInCheckout) "Added to Total Bill" else "Skipped (Current Bill Only)", fontSize = 11.sp)
                            }
                            Switch(
                                checked = includePreviousDueInCheckout,
                                onCheckedChange = onIncludePreviousDueChange
                            )
                        }
                    }
                }

                Text("Total Payable: $finalPayableTotal", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                
                if (!isSplitMode) {
                    Text("Select Quick Checkout:", fontSize = 14.sp)
                    Button(
                        onClick = {
                            onDismiss()
                            onPerformSave("CASH", finalPayableTotal, Money.Zero, Money.Zero)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF059669))
                    ) { Text("Full Cash ($finalPayableTotal)") }
                    
                    Button(
                        onClick = {
                            onDismiss()
                            onPerformSave("GPAY", Money.Zero, finalPayableTotal, Money.Zero)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF2563EB))
                    ) { Text("Full GPay / UPI ($finalPayableTotal)") }
                    
                    Button(
                        onClick = {
                            if (selectedCustomerId == null || selectedCustomerId == "online") {
                                onDismiss()
                                onError("Validation Error: Credit can only be given to a registered customer. Please select a customer.")
                            } else {
                                onDismiss()
                                onPerformSave("CREDIT", Money.Zero, Money.Zero, finalPayableTotal)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("Full Credit ($finalPayableTotal)") }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    OutlinedButton(
                        onClick = { isSplitMode = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Split / Partial Payment") }
                    
                } else {
                    val cInput = cashInput.toDoubleOrNull() ?: 0.0
                    val uInput = upiInput.toDoubleOrNull() ?: 0.0
                    val inputTotalMinor = ((cInput + uInput) * 100).toLong()
                    val diff = finalPayableTotal.minorUnits - inputTotalMinor
                    
                    OutlinedTextField(
                        value = cashInput, 
                        onValueChange = { cashInput = it }, 
                        label = { Text("Cash Amount Received") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = upiInput, 
                        onValueChange = { upiInput = it }, 
                        label = { Text("UPI/GPay Amount Received") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    if (diff > 0) {
                        Text("Remaining Credit: ${Money(diff)}", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        if (selectedCustomerId == null || selectedCustomerId == "online") {
                            Text("⚠️ Please select a customer first to assign credit.", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                        }
                    } else if (diff < 0) {
                        Text("Change to Return: ${Money(-diff)}", color = androidx.compose.ui.graphics.Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                    } else {
                        Text("Fully Paid! \uD83C\uDF89", color = androidx.compose.ui.graphics.Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            if (isSplitMode) {
                val cInput = cashInput.toDoubleOrNull() ?: 0.0
                val uInput = upiInput.toDoubleOrNull() ?: 0.0
                val inputTotalMinor = ((cInput + uInput) * 100).toLong()
                val diff = finalPayableTotal.minorUnits - inputTotalMinor
                val creditAmount = if (diff > 0) diff else 0L
                
                val canSubmit = creditAmount == 0L || (selectedCustomerId != null && selectedCustomerId != "online")
                
                Button(
                    enabled = canSubmit,
                    onClick = {
                        onDismiss()
                        isSplitMode = false
                        cashInput = ""
                        upiInput = ""
                        
                        val paymentModeStr = if (creditAmount > 0L) "PARTIAL" else "SPLIT"
                        onPerformSave(paymentModeStr, Money((cInput * 100).toLong()), Money((uInput * 100).toLong()), Money(creditAmount))
                    }
                ) {
                    Text("Confirm Split Payment")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { 
                if (isSplitMode) {
                    isSplitMode = false
                } else {
                    onDismiss() 
                }
            }) {
                Text(if (isSplitMode) "Back" else "Cancel")
            }
        }
    )
}
