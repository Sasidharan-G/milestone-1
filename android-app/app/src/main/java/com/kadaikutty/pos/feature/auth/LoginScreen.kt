package com.kadaikutty.pos.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


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
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    LaunchedEffect(state.complete) {
        if (state.complete) {
            if (state.isSuperMaster) {
                onOpenMasterControl()
            } else {
                onLoginSuccess()
            }
        }
    }

        val accent = Color(0xFF3F3AC9)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 28.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        text = stringResource(com.kadaikutty.pos.R.string.welcome_back),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF0E1733)
                    )
                    Text(
                        text = stringResource(com.kadaikutty.pos.R.string.sign_in_subtitle),
                        fontSize = 14.sp,
                        color = Color(0xFF525D78)
                    )
                }

                // Mobile number - +91 prefix with a divider, matching the approved design
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(com.kadaikutty.pos.R.string.mobile_number), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF2B3553))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .background(Color(0xFFF6F7FB), RoundedCornerShape(14.dp))
                            .border(1.5.dp, Color(0xFFD6DBE8), RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("+91", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0E1733))
                        Box(Modifier.width(1.dp).height(22.dp).background(Color(0xFFD0D5E2)))
                        Box(Modifier.weight(1f)) {
                            if (state.mobileNumber.isEmpty()) {
                                Text(stringResource(com.kadaikutty.pos.R.string.enter_10_digit_mobile), fontSize = 16.sp, color = Color(0xFF8A93A8))
                            }
                            BasicTextField(
                                value = state.mobileNumber,
                                onValueChange = { viewModel.updateMobileNumber(com.kadaikutty.pos.core.common.InputRules.phone(it)) },
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0E1733), letterSpacing = 0.6.sp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(accent),
                                modifier = Modifier.fillMaxWidth().testTag("login_mobile_field")
                            )
                        }
                    }
                }

                // 6-digit PIN grid
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("6-Digit PIN", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF2B3553))
                        Text(
                            text = "Forgot PIN?",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = accent,
                            modifier = Modifier.clickable(enabled = !state.loading) {
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
                    PinDigitsInput(
                        value = state.password,
                        onValueChange = { viewModel.updatePassword(it.take(64)) },
                        length = 6,
                        accentColor = accent
                    )
                }

                Button(
                    onClick = {
                        // The keyboard stayed up over the result (and the offline message) after signing in.
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        triggerAnimation { viewModel.login() }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = Color.White
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp, pressedElevation = 2.dp),
                    enabled = !state.loading
                ) {
                    if (state.loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Sign In", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = Color(0xFF1E8E5A),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Verified & Secured", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF4A5572))
                }

                val errorMsg = state.error
                if (!errorMsg.isNullOrBlank()) {
                    Text(
                        text = errorMsg,
                        color = Color(0xFFDC2626),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (message.isNotBlank()) {
                    Text(
                        text = message,
                        color = Color(0xFF16A34A),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Super Master Access - kept reachable, styled small and subtle to match the new card
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id = com.kadaikutty.pos.R.drawable.ic_master_logo),
                        contentDescription = "Master Control",
                        modifier = Modifier
                            .size(26.dp)
                            .graphicsLayer { alpha = 0.55f }
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null,
                                onClick = { triggerAnimation { showMasterPinDialog = true } }
                            )
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
                containerColor = Color(0xFF1E3A8A), // Brand navy background
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
                            placeholder = { Text("Enter 6-digit PIN", color = Color.White.copy(alpha = 0.5f)) },
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
                            contentColor = Color(0xFF1E3A8A),
                            disabledContainerColor = Color.White.copy(alpha = 0.5f),
                            disabledContentColor = Color(0xFF1E3A8A).copy(alpha = 0.5f)
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
                containerColor = Color(0xFF1E3A8A), // Brand navy background
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
                            label = { Text("New Master PIN (6 Digits)", color = Color.White.copy(alpha = 0.8f)) },
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
                                newPin = masterNewPin,
                                mobileNumber = masterResetTargetPhone
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
                            contentColor = Color(0xFF1E3A8A),
                            disabledContainerColor = Color.White.copy(alpha = 0.5f),
                            disabledContentColor = Color(0xFF1E3A8A).copy(alpha = 0.5f)
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
                    color = Color(0xFF1E3A8A),
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
                                onValueChange = { viewModel.updateNewPassword(it.filter { ch -> ch.isDigit() }.take(6)) },
                                label = { Text("New 6-Digit PIN", color = Color.White.copy(alpha = 0.7f)) },
                                placeholder = { Text("Enter a new 6-digit PIN", color = Color.White.copy(alpha = 0.4f)) },
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
                                    contentColor = Color(0xFF1E3A8A),
                                    disabledContainerColor = Color.White.copy(alpha = 0.25f),
                                    disabledContentColor = Color.White.copy(alpha = 0.4f)
                                ),
                                shape = RoundedCornerShape(percent = 50),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 2.dp),
                                enabled = !state.loading && state.resetOtp.length == 6 && state.newPasswordString.length == 6
                            ) {
                                if (state.loading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        color = Color(0xFF1E3A8A),
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

/**
 * Six single-digit boxes for the login PIN. [value] is read as plain digits with no gaps,
 * left-packed; it maps straight onto the same 6-digit password string the rest of the login flow
 * already validates, so no ViewModel changes were needed for this visual.
 *
 * Earlier versions used one BasicTextField per box with manual focus-chaining on backspace. That
 * relied on either onValueChange("") firing (some keyboards skip this on an already-empty field)
 * or a raw KeyEvent reaching onKeyEvent (most software number pads never dispatch one at all -
 * they call the IME's deleteSurroundingText directly) - so continuous backspacing across boxes was
 * unreliable across real devices. This instead uses a single invisible BasicTextField holding the
 * whole PIN string, with ordinary text-field backspace semantics (always reliable, same as every
 * other text field in the app), and renders the boxes purely as a visual overlay on top of it.
 */
@Composable
private fun PinDigitsInput(
    value: String,
    onValueChange: (String) -> Unit,
    length: Int,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }

    Box(modifier = modifier.fillMaxWidth()) {
        BasicTextField(
            value = value,
            onValueChange = { newVal -> onValueChange(newVal.filter { it.isDigit() }.take(length)) },
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(color = Color.Transparent, fontSize = 1.sp),
            visualTransformation = VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.Transparent),
            modifier = Modifier
                .matchParentSize()
                .testTag("login_pin_input")
                .focusRequester(focusRequester)
                .onFocusChanged { isFocused = it.isFocused }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(interactionSource = interactionSource, indication = null) { focusRequester.requestFocus() },
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (i in 0 until length) {
                val digit = value.getOrNull(i)?.toString().orEmpty()
                val isCursorHere = isFocused && i == value.length.coerceAtMost(length - 1)
                val borderColor = if (isCursorHere) accentColor else Color(0xFFD6DBE8)
                val bgColor = if (isCursorHere || digit.isNotEmpty()) Color.White else Color(0xFFF6F7FB)

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("login_pin_digit_$i")
                        .background(bgColor, RoundedCornerShape(12.dp))
                        .border(1.5.dp, borderColor, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (digit.isNotEmpty()) {
                        Text("•", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0E1733))
                    }
                }
            }
        }
    }
}
