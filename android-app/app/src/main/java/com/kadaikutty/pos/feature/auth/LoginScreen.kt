package com.kadaikutty.pos.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kadaikutty.pos.core.auth.LoginMode
import com.kadaikutty.pos.core.presentation.components.LoadingOverlay


@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    var message by remember { mutableStateOf("") }
    val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity

    LaunchedEffect(state.complete) {
        if (state.complete) {
            onLoginSuccess()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF2C3E50),
                        Color(0xFF1A2536),
                        Color(0xFF0F172A)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .padding(24.dp)
                .border(
                    width = 1.dp,
                    color = Color(0xFF334155),
                    shape = RoundedCornerShape(24.dp)
                ),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111C2E))
        ) {
            Column(
                modifier = Modifier
                    .padding(32.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Electric Blue Lock Badge
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .background(
                            color = Color(0xFF1976D2),
                            shape = RoundedCornerShape(20.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(34.dp),
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = stringResource(com.kadaikutty.pos.R.string.welcome_back),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = stringResource(com.kadaikutty.pos.R.string.sign_in_subtitle),
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8)
                )

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(
                    value = state.mobileNumber,
                    onValueChange = { viewModel.updateMobileNumber(it) },
                    label = { Text(stringResource(com.kadaikutty.pos.R.string.mobile_number), color = Color(0xFF94A3B8)) },
                    placeholder = { Text(stringResource(com.kadaikutty.pos.R.string.enter_10_digit_mobile), color = Color(0xFF64748B)) },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = Color(0xFF94A3B8)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF162238),
                        unfocusedContainerColor = Color(0xFF162238),
                        focusedBorderColor = Color(0xFF1E88E5),
                        unfocusedBorderColor = Color(0xFF334155),
                        cursorColor = Color(0xFF1E88E5)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = state.password,
                    onValueChange = { viewModel.updatePassword(it) },
                    label = { Text(stringResource(com.kadaikutty.pos.R.string.password), color = Color(0xFF94A3B8)) },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF94A3B8)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF162238),
                        unfocusedContainerColor = Color(0xFF162238),
                        focusedBorderColor = Color(0xFF1E88E5),
                        unfocusedBorderColor = Color(0xFF334155),
                        cursorColor = Color(0xFF1E88E5)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Offline", fontSize = 12.sp, color = Color(0xFF94A3B8))
                        Switch(
                            checked = state.mode == LoginMode.Online,
                            onCheckedChange = { online ->
                                viewModel.updateMode(if (online) LoginMode.Online else LoginMode.Offline)
                            },
                            modifier = Modifier.padding(horizontal = 8.dp),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF1E88E5),
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color(0xFF475569)
                            )
                        )
                        Text("Online", fontSize = 12.sp, color = Color(0xFF94A3B8))
                    }

                    Text(
                        text = "Forgot Password?",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF38BDF8),
                        modifier = Modifier.clickable {
                            val phone = state.mobileNumber.trim()
                            if (phone.isBlank()) {
                                message = "Please enter your mobile number first."
                            } else if (activity != null) {
                                viewModel.requestPasswordResetOtp(phone, activity, 
                                    onCodeSent = { _ -> message = "Live SMS OTP sent to your mobile!" },
                                    onError = { errMsg -> message = "Recovery failed: $errMsg" }
                                )
                            } else {
                                message = "Activity context is missing."
                            }
                        }
                    )
                }

                if (!state.error.isNullOrBlank()) {
                    Text(
                        text = state.error ?: "",
                        color = Color(0xFFFF6B6B),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Button(
                    onClick = { viewModel.login() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1E88E5),
                        contentColor = Color.White
                    ),
                    enabled = !state.loading
                ) {
                    if (state.loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White
                        )
                    } else {
                        Text("Sign In", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }

                Text(
                    text = "New business? Register here",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF38BDF8),
                    modifier = Modifier
                        .clickable { onNavigateToRegister() }
                        .padding(vertical = 4.dp)
                )

                if (message.isNotBlank()) {
                    Text(
                        text = message,
                        color = Color(0xFF69F0AE),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
        
        if (state.showResetOtpDialog) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { viewModel.dismissResetDialog() }
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF0B0F17),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Reset Password / PIN", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            IconButton(onClick = { viewModel.dismissResetDialog() }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                            }
                        }

                        HorizontalDivider(color = Color(0xFF1E293B))
                        Spacer(modifier = Modifier.height(8.dp))

                        com.kadaikutty.pos.core.ui.otp.OrbitOtpVerificationView(
                            otpLength = 6,
                            otpValue = state.resetOtp,
                            phoneNumber = state.mobileNumber,
                            onOtpChange = { viewModel.updateResetOtp(it) },
                            onVerifyTriggered = {
                                if (state.newPasswordString.isNotBlank()) {
                                    viewModel.verifyOtpAndResetPassword { success, errMsg ->
                                        if (success) {
                                            message = "Password / PIN updated successfully! Please Sign In."
                                        } else {
                                            message = "Failed to update password: $errMsg"
                                        }
                                    }
                                }
                            },
                            onResendClick = {
                                if (activity != null) {
                                    viewModel.requestPasswordResetOtp(state.mobileNumber, activity, onCodeSent = {
                                        message = "SMS OTP resent to your mobile!"
                                    }, onError = { err -> message = "Failed: $err" })
                                }
                            },
                            isLoading = state.loading,
                            errorMessage = state.error
                        )

                        // New Password Field inside the dialog
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = state.newPasswordString,
                                onValueChange = { viewModel.updateNewPassword(it) },
                                label = { Text("New Password / 4-6 Digit PIN", color = Color(0xFF94A3B8)) },
                                placeholder = { Text("Enter new password or PIN", color = Color(0xFF64748B)) },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedContainerColor = Color(0xFF162238),
                                    unfocusedContainerColor = Color(0xFF162238),
                                    focusedBorderColor = Color(0xFF2EE6A8),
                                    unfocusedBorderColor = Color(0xFF334155)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Button(
                                onClick = {
                                    viewModel.verifyOtpAndResetPassword { success, errMsg ->
                                        if (success) {
                                            message = "Password / PIN updated successfully! Please Sign In."
                                        } else {
                                            message = "Failed to update password: $errMsg"
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2EE6A8)),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !state.loading && state.resetOtp.length == 6 && state.newPasswordString.isNotBlank()
                            ) {
                                Text("Confirm & Reset Password", color = Color(0xFF0B0F17), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
