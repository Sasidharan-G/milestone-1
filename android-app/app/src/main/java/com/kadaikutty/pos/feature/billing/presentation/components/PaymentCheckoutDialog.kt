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
import com.kadaikutty.pos.core.common.CheckoutMath

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
    var isProcessing by remember { mutableStateOf(false) }

    LaunchedEffect(showDialog) {
        if (showDialog) {
            isProcessing = false
        }
    }

    AlertDialog(
        onDismissRequest = { 
            if (!isProcessing) {
                onDismiss()
                isSplitMode = false
                cashInput = ""
                upiInput = ""
            }
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
                                onCheckedChange = onIncludePreviousDueChange,
                                enabled = !isProcessing
                            )
                        }
                    }
                }

                Text("Total Payable: $finalPayableTotal", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                
                if (!isSplitMode) {
                    Text("Select Quick Checkout:", fontSize = 14.sp)
                    Button(
                        enabled = !isProcessing,
                        onClick = {
                            if (isProcessing) return@Button
                            isProcessing = true
                            onDismiss()
                            onPerformSave("CASH", finalPayableTotal, Money.Zero, Money.Zero)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF059669))
                    ) { Text("Full Cash ($finalPayableTotal)") }
                    
                    Button(
                        enabled = !isProcessing,
                        onClick = {
                            if (isProcessing) return@Button
                            isProcessing = true
                            onDismiss()
                            onPerformSave("GPAY", Money.Zero, finalPayableTotal, Money.Zero)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF2563EB))
                    ) { Text("Full GPay / UPI ($finalPayableTotal)") }
                    
                    Button(
                        enabled = !isProcessing,
                        onClick = {
                            if (isProcessing) return@Button
                            if (includePreviousDueInCheckout && customerCreditDue > 0) {
                                onError("Previous dues must be collected by cash or UPI. Turn off previous dues for a credit-only bill.")
                            } else if (selectedCustomerId == null || selectedCustomerId == "online") {
                                onDismiss()
                                onError("Validation Error: Credit can only be given to a registered customer. Please select a customer.")
                            } else {
                                isProcessing = true
                                onDismiss()
                                onPerformSave("CREDIT", Money.Zero, Money.Zero, finalPayableTotal)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("Full Credit ($finalPayableTotal)") }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    OutlinedButton(
                        enabled = !isProcessing,
                        onClick = { isSplitMode = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Split / Partial Payment") }
                    
                } else {
                    val cInput = CheckoutMath.parseAmount(cashInput)
                    val uInput = CheckoutMath.parseAmount(upiInput)
                    val inputTotalMinor = (cInput ?: 0L) + (uInput ?: 0L)
                    val diff = finalPayableTotal.minorUnits - inputTotalMinor
                    
                    OutlinedTextField(
                        value = cashInput, 
                        onValueChange = { cashInput = com.kadaikutty.pos.core.common.InputRules.money(it) }, 
                        label = { Text("Cash Amount Received") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isProcessing
                    )
                    OutlinedTextField(
                        value = upiInput, 
                        onValueChange = { upiInput = com.kadaikutty.pos.core.common.InputRules.money(it) }, 
                        label = { Text("UPI/GPay Amount Received") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isProcessing
                    )
                    
                    if (cInput == null || uInput == null || uInput > finalPayableTotal.minorUnits) {
                        Text("Enter valid amounts with up to 2 decimal places. UPI cannot exceed the total.", color = MaterialTheme.colorScheme.error)
                    } else if (diff > 0) {
                        Text("Remaining Credit: ${Money(diff)}", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        if (selectedCustomerId == null || selectedCustomerId == "online") {
                            Text("Please select a customer first to assign credit.", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                        }
                    } else if (diff < 0) {
                        Text("Change to Return: ${Money(-diff)}", color = androidx.compose.ui.graphics.Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                    } else {
                        Text("Fully Paid", color = androidx.compose.ui.graphics.Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            if (isSplitMode) {
                val cInput = CheckoutMath.parseAmount(cashInput)
                val uInput = CheckoutMath.parseAmount(upiInput)
                val inputTotalMinor = (cInput ?: 0L) + (uInput ?: 0L)
                val diff = finalPayableTotal.minorUnits - inputTotalMinor
                val creditAmount = if (diff > 0) diff else 0L
                
                val canSubmit = cInput != null && uInput != null && uInput <= finalPayableTotal.minorUnits && (creditAmount == 0L || (selectedCustomerId != null && selectedCustomerId != "online")) && !isProcessing
                
                Button(
                    enabled = canSubmit,
                    onClick = {
                        if (isProcessing) return@Button
                        isProcessing = true
                        onDismiss()
                        isSplitMode = false
                        cashInput = ""
                        upiInput = ""
                        
                        val paymentModeStr = if (creditAmount > 0L) "PARTIAL" else "SPLIT"
                        val payment = CheckoutMath.payment(finalPayableTotal.minorUnits, cInput!!, uInput!!, selectedCustomerId != null && selectedCustomerId != "online")
                        onPerformSave(paymentModeStr, Money(payment.cash), Money(payment.upi), Money(payment.credit))
                    }
                ) {
                    Text("Confirm Split Payment")
                }
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isProcessing,
                onClick = { 
                    if (isSplitMode) {
                        isSplitMode = false
                    } else {
                        onDismiss() 
                    }
                }
            ) {
                Text(if (isSplitMode) "Back" else "Cancel")
            }
        }
    )
}
