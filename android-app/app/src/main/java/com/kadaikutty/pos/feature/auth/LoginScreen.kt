package com.kadaikutty.pos.feature.auth

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kadaikutty.pos.core.auth.LoginMode


@Composable
fun LoginScreenContent(
    viewModel: LoginViewModel,
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onOpenMasterControl: () -> Unit = {},
    triggerAnimation: (() -> Unit) -> Unit
) {
    val state by viewModel.state.collectAsState()
    var message by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = remember(context) {
        var ctx: android.content.Context? = context
        while (ctx is android.content.ContextWrapper) {
            if (ctx is android.app.Activity) return@remember ctx
            ctx = ctx.baseContext
        }
        null
    }
    var passwordVisible by remember { mutableStateOf(false) }
    var showMasterPinDialog by remember { mutableStateOf(false) }
    var enteredMasterPin by remember { mutableStateOf("") }
    var masterPinVisible by remember { mutableStateOf(false) }
    var masterPinError by remember { mutableStateOf(false) }
    var isCheckingMasterPin by remember { mutableStateOf(false) }

    var showMasterOtpResetDialog by remember { mutableStateOf(false) }
    var masterResetVerificationId by remember { mutableStateOf<String?>(null) }
    var masterResetOtp by remember { mutableStateOf("") }
    var masterNewPin by remember { mutableStateOf("") }
    var masterNewPinVisible by remember { mutableStateOf(false) }
    var masterResetTargetPhone by remember { mutableStateOf("") }
    var masterResetLoading by remember { mutableStateOf(false) }
    var forgotPasswordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(state.complete) {
        if (state.complete) {
            if (state.isSuperMaster) {
                onOpenMasterControl()
            } else {
                onLoginSuccess()
            }
        }
    }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
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
                    label = { Text(stringResource(com.kadaikutty.pos.R.string.mobile_number), color = Color.White.copy(alpha = 0.8f)) },
                    placeholder = { Text(stringResource(com.kadaikutty.pos.R.string.enter_10_digit_mobile), color = Color.White.copy(alpha = 0.5f)) },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = Color.White.copy(alpha = 0.8f)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    shape = RoundedCornerShape(percent = 50),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color.White.copy(alpha = 0.15f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.1f),
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = state.password,
                    onValueChange = { viewModel.updatePassword(it) },
                    label = { Text("Password (6-Digit PIN)", color = Color.White.copy(alpha = 0.8f)) },
                    placeholder = { Text("Enter 6-digit PIN or existing password", color = Color.White.copy(alpha = 0.5f)) },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White.copy(alpha = 0.8f)) },
                    trailingIcon = {
                        if (state.password.isNotEmpty()) {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                    tint = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    shape = RoundedCornerShape(percent = 50),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color.White.copy(alpha = 0.15f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.1f),
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = Color.White
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
                                checkedTrackColor = Color(0xFF8E2128), // Matches maroon theme
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color.White.copy(alpha = 0.3f)
                            )
                        )
                        Text("Online", fontSize = 12.sp, color = Color(0xFF94A3B8))
                    }

                    Text(
                        text = "Forgot Password?",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        modifier = Modifier.clickable {
                            val phone = state.mobileNumber.trim()
                            if (phone.isBlank()) {
                                message = "Please enter your mobile number first."
                            } else if (activity != null) {
                                triggerAnimation {
                                    viewModel.requestPasswordResetOtp(phone, activity, 
                                        onCodeSent = { _ -> message = "Live SMS OTP sent to your mobile!" },
                                        onError = { errMsg -> message = "Recovery failed: $errMsg" }
                                    )
                                }
                            } else {
                                message = "Activity context is missing."
                            }
                        }
                    )
                }


                Button(
                    onClick = { triggerAnimation { viewModel.login() } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(percent = 50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFFE40000)
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 0.dp),
                    enabled = !state.loading
                ) {
                    if (state.loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color(0xFFE40000)
                        )
                    } else {
                        Text("Sign In", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }

                Text(
                    text = "New business? Register here",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    modifier = Modifier
                        .clickable { onNavigateToRegister() }
                        .padding(vertical = 4.dp)
                )

                HorizontalDivider(color = Color.White.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))

                // Super Master Access Button (Logo)
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = com.kadaikutty.pos.R.drawable.ic_master_logo),
                    contentDescription = "Master Control",
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .size(36.dp)
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null,
                            onClick = { triggerAnimation { showMasterPinDialog = true } }
                        )
                )

                val errorMsg = state.error
                if (!errorMsg.isNullOrBlank()) {
                    Text(
                        text = errorMsg,
                        color = Color(0xFFFF6B6B),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                } else if (message.isNotBlank()) {
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

        // Master Secret PIN Dialog with Cloud Sync and Forgot PIN OTP
        if (showMasterPinDialog) {
            AlertDialog(
                onDismissRequest = {
                    showMasterPinDialog = false
                    enteredMasterPin = ""
                    masterPinError = false
                },
                containerColor = Color(0xFF5C151A), // Maroon Background
                titleContentColor = Color.White,
                textContentColor = Color.White.copy(alpha = 0.9f),
                title = {
                    Text("Super Master Authentication", fontWeight = FontWeight.Bold, color = Color.White)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Enter Super Master Secret PIN (Cloud Synced):", color = Color.White.copy(alpha = 0.8f))
                        OutlinedTextField(
                            value = enteredMasterPin,
                            onValueChange = {
                                enteredMasterPin = it.filter { ch -> ch.isDigit() }.take(6)
                                masterPinError = false
                            },
                            label = { Text("Master PIN", color = Color.White.copy(alpha = 0.8f)) },
                            placeholder = { Text("Enter 4-6 digit PIN", color = Color.White.copy(alpha = 0.5f)) },
                            isError = masterPinError,
                            supportingText = if (masterPinError) { { Text("Incorrect Master PIN.", color = Color(0xFFFF5252)) } } else null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            trailingIcon = {
                                IconButton(onClick = { masterPinVisible = !masterPinVisible }) {
                                    Icon(
                                        imageVector = if (masterPinVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = if (masterPinVisible) "Hide PIN" else "Show PIN",
                                        tint = Color.White.copy(alpha = 0.8f)
                                    )
                                }
                            },
                            visualTransformation = if (masterPinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(percent = 50),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = Color.White.copy(alpha = 0.15f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.1f),
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = Color.Transparent,
                                cursorColor = Color.White,
                                errorBorderColor = Color(0xFFFF5252),
                                errorLabelColor = Color(0xFFFF5252),
                                errorSupportingTextColor = Color(0xFFFF5252)
                            )
                        )

                        // Master Forgot PIN via backend SMS OTP
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = {
                                    if (activity != null) {
                                        masterResetLoading = true
                                        viewModel.requestMasterResetOtp(
                                            activity = activity,
                                            customMobile = state.mobileNumber.ifBlank { null },
                                            onCodeSent = { vId, targetPhone ->
                                                masterResetLoading = false
                                                masterResetVerificationId = vId
                                                masterResetTargetPhone = targetPhone
                                                showMasterPinDialog = false
                                                enteredMasterPin = ""
                                                showMasterOtpResetDialog = true
                                            },
                                            onError = { err ->
                                                masterResetLoading = false
                                                message = "Failed to send Master OTP: $err"
                                            }
                                        )
                                    }
                                },
                                enabled = !masterResetLoading
                            ) {
                                Text(
                                    text = if (masterResetLoading) "Sending SMS OTP..." else "Forgot Master PIN? (SMS OTP)",
                                    fontSize = 12.sp,
                                    color = Color(0xFF38BDF8),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            isCheckingMasterPin = true
                            viewModel.validateMasterPin(enteredMasterPin) { isValid ->
                                isCheckingMasterPin = false
                                if (isValid) {
                                    showMasterPinDialog = false
                                    enteredMasterPin = ""
                                    onOpenMasterControl()
                                } else {
                                    masterPinError = true
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color(0xFF5C151A),
                            disabledContainerColor = Color.White.copy(alpha = 0.5f),
                            disabledContentColor = Color(0xFF5C151A).copy(alpha = 0.5f)
                        ),
                        enabled = enteredMasterPin.length >= 6 && !isCheckingMasterPin
                    ) {
                        Text(if (isCheckingMasterPin) "Verifying..." else "Open Control Panel", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showMasterPinDialog = false
                        enteredMasterPin = ""
                    }) { Text("Cancel", color = Color.White.copy(alpha = 0.8f)) }
                }
            )
        }

        // Master SMS OTP Reset Dialog
        if (showMasterOtpResetDialog && masterResetVerificationId != null) {
            AlertDialog(
                onDismissRequest = {
                    showMasterOtpResetDialog = false
                    masterResetOtp = ""
                    masterNewPin = ""
                    masterResetVerificationId = null
                },
                containerColor = Color(0xFF5C151A), // Maroon Background
                titleContentColor = Color.White,
                textContentColor = Color.White.copy(alpha = 0.9f),
                title = { Text("Reset Master Secret PIN", fontWeight = FontWeight.Bold, color = Color.White) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("SMS OTP sent to Master mobile ($masterResetTargetPhone):", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                        
                        OutlinedTextField(
                            value = masterResetOtp,
                            onValueChange = { masterResetOtp = it.filter { ch -> ch.isDigit() }.take(6) },
                            label = { Text("6-Digit SMS OTP", color = Color.White.copy(alpha = 0.8f)) },
                            placeholder = { Text("Enter 6-digit OTP", color = Color.White.copy(alpha = 0.5f)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(percent = 50),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = Color.White.copy(alpha = 0.15f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.1f),
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = Color.Transparent,
                                cursorColor = Color.White
                            )
                        )

                        OutlinedTextField(
                            value = masterNewPin,
                            onValueChange = { masterNewPin = it.filter { ch -> ch.isDigit() }.take(6) },
                            label = { Text("New Master PIN (4-6 Digits)", color = Color.White.copy(alpha = 0.8f)) },
                            placeholder = { Text("Enter new secret PIN", color = Color.White.copy(alpha = 0.5f)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            trailingIcon = {
                                IconButton(onClick = { masterNewPinVisible = !masterNewPinVisible }) {
                                    Icon(
                                        imageVector = if (masterNewPinVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = if (masterNewPinVisible) "Hide PIN" else "Show PIN",
                                        tint = Color.White.copy(alpha = 0.8f)
                                    )
                                }
                            },
                            visualTransformation = if (masterNewPinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(percent = 50),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = Color.White.copy(alpha = 0.15f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.1f),
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = Color.Transparent,
                                cursorColor = Color.White
                            )
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            masterResetLoading = true
                            viewModel.verifyMasterOtpAndSetNewPin(
                                verificationId = masterResetVerificationId!!,
                                otp = masterResetOtp,
                                newPin = masterNewPin
                            ) { success, errMsg ->
                                masterResetLoading = false
                                if (success) {
                                    showMasterOtpResetDialog = false
                                    masterResetOtp = ""
                                    masterNewPin = ""
                                    masterResetVerificationId = null
                                    message = "Master PIN successfully updated! Opening panel..."
                                    onOpenMasterControl()
                                } else {
                                    message = "Master OTP verification failed: $errMsg"
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color(0xFF5C151A),
                            disabledContainerColor = Color.White.copy(alpha = 0.5f),
                            disabledContentColor = Color(0xFF5C151A).copy(alpha = 0.5f)
                        ),
                        enabled = masterResetOtp.length == 6 && masterNewPin.length >= 6 && !masterResetLoading
                    ) {
                        Text(if (masterResetLoading) "Updating..." else "Verify & Set PIN", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showMasterOtpResetDialog = false
                        masterResetOtp = ""
                        masterNewPin = ""
                        masterResetVerificationId = null
                    }) { Text("Cancel", color = Color.White.copy(alpha = 0.8f)) }
                }
            )
        }
        
        if (state.showResetOtpDialog) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { viewModel.dismissResetDialog() },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = Color(0xFF5C151A),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color.White.copy(alpha = 0.35f)),
                    shadowElevation = 24.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 440.dp)
                        .padding(horizontal = 16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        com.kadaikutty.pos.core.ui.otp.OrbitOtpVerificationView(
                            otpLength = 6,
                            otpValue = state.resetOtp,
                            phoneNumber = state.mobileNumber,
                            onOtpChange = { viewModel.updateResetOtp(it) },
                            onResendClick = {
                                if (activity != null) {
                                    viewModel.requestPasswordResetOtp(state.mobileNumber, activity, onCodeSent = {
                                        message = "SMS OTP resent to your mobile!"
                                    }, onError = { err -> message = "" })
                                }
                            },
                            onCloseClick = { viewModel.dismissResetDialog() },
                            isLoading = state.loading,
                            errorMessage = state.error,
                            showVerifyButton = false
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // New Password Field inside the dialog
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = state.newPasswordString,
                                onValueChange = { viewModel.updateNewPassword(it) },
                                label = { Text("New Password / 4-6 Digit PIN", color = Color.White.copy(alpha = 0.7f)) },
                                placeholder = { Text("Enter new password or PIN", color = Color.White.copy(alpha = 0.4f)) },
                                trailingIcon = {
                                    IconButton(onClick = { forgotPasswordVisible = !forgotPasswordVisible }) {
                                        Icon(
                                            imageVector = if (forgotPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = if (forgotPasswordVisible) "Hide password" else "Show password",
                                            tint = Color.White.copy(alpha = 0.8f)
                                        )
                                    }
                                },
                                visualTransformation = if (forgotPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                singleLine = true,
                                shape = RoundedCornerShape(percent = 50),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedContainerColor = Color.White.copy(alpha = 0.15f),
                                    unfocusedContainerColor = Color.White.copy(alpha = 0.1f),
                                    focusedBorderColor = Color.White,
                                    unfocusedBorderColor = Color.Transparent,
                                    cursorColor = Color.White
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Button(
                                onClick = {
                                    viewModel.verifyOtpAndResetPassword { success, errMsg ->
                                        if (success) {
                                            message = "Password / PIN updated successfully! Please Sign In."
                                        } else {
                                            message = ""
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color(0xFF5C151A),
                                    disabledContainerColor = Color.White.copy(alpha = 0.25f),
                                    disabledContentColor = Color.White.copy(alpha = 0.4f)
                                ),
                                shape = RoundedCornerShape(percent = 50),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 2.dp),
                                enabled = !state.loading && state.resetOtp.length == 6 && state.newPasswordString.isNotBlank()
                            ) {
                                if (state.loading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        color = Color(0xFF5C151A),
                                        strokeWidth = 2.5.dp
                                    )
                                } else {
                                    Text("Confirm & Reset Password", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                }
                            }
                        }
                    }
                }
        }
    }
}
