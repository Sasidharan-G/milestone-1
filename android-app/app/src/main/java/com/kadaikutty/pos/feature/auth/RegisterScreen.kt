package com.kadaikutty.pos.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
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
import androidx.compose.ui.res.stringResource
import com.kadaikutty.pos.core.presentation.components.LoadingOverlay
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kadaikutty.pos.core.auth.AuthRepository
import com.kadaikutty.pos.core.auth.RegisterResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RegisterUiState(
    val mobileNumber: String = "",
    val passwordString: String = "",
    val confirmPasswordString: String = "",
    val ownerName: String = "",
    val businessName: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val complete: Boolean = false,
    val showOtpDialog: Boolean = false,
    val otp: String = "",
    val verificationId: String? = null
)

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {
    private val _state = MutableStateFlow(RegisterUiState())
    val state = _state.asStateFlow()

    fun updateMobileNumber(value: String) = _state.update { it.copy(mobileNumber = value, error = null) }
    fun updateOwnerName(value: String) = _state.update { it.copy(ownerName = value, error = null) }
    fun updateBusinessName(value: String) = _state.update { it.copy(businessName = value, error = null) }
    fun updatePassword(value: String) = _state.update { it.copy(passwordString = value, error = null) }
    fun updateConfirmPassword(value: String) = _state.update { it.copy(confirmPasswordString = value, error = null) }
    fun updateOtp(value: String) = _state.update { it.copy(otp = value, error = null) }
    fun dismissOtpDialog() = _state.update { it.copy(showOtpDialog = false, otp = "", verificationId = null) }

    fun register(activity: android.app.Activity) {
        val current = state.value
        if (current.mobileNumber.isBlank() || current.ownerName.isBlank() || current.businessName.isBlank() || current.passwordString.isBlank() || current.confirmPasswordString.isBlank()) {
            _state.update { it.copy(error = "All fields are required") }
            return
        }
        if (current.passwordString != current.confirmPasswordString) {
            _state.update { it.copy(error = "Passwords do not match") }
            return
        }
        if (current.passwordString.length < 6) {
            _state.update { it.copy(error = "Password must be at least 6 characters") }
            return
        }
        val cleanPhone = current.mobileNumber.trim().replace(" ", "").replace("-", "")
        if (cleanPhone.length < 10 || !cleanPhone.all { it.isDigit() || it == '+' }) {
            _state.update { it.copy(error = "Please provide a valid 10-digit mobile number") }
            return
        }
        
        val phoneWithCode = if (cleanPhone.startsWith("+")) cleanPhone else "+91$cleanPhone"

        _state.update { it.copy(loading = true, error = null) }
        
        authRepository.sendRegistrationOtp(
            mobileNumber = phoneWithCode,
            activity = activity,
            onCodeSent = { verificationId ->
                _state.update { it.copy(loading = false, showOtpDialog = true, verificationId = verificationId) }
            },
            onVerificationFailed = { error ->
                _state.update { it.copy(loading = false, error = error) }
            }
        )
    }

    fun verifyOtpAndCompleteRegistration(onSuccess: (String) -> Unit) {
        val current = state.value
        val verificationId = current.verificationId
        if (verificationId == null || current.otp.isBlank()) {
            _state.update { it.copy(error = "Please enter the OTP") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val passChars = current.passwordString.toCharArray()
            
            val cleanPhone = current.mobileNumber.trim().replace(" ", "").replace("-", "")
            val phoneWithCode = if (cleanPhone.startsWith("+")) cleanPhone else "+91$cleanPhone"

            val result = authRepository.verifyRegistrationOtpAndRegister(
                verificationId = verificationId,
                otp = current.otp,
                mobileNumber = phoneWithCode,
                password = passChars,
                ownerName = current.ownerName,
                businessName = current.businessName
            )
            passChars.fill('\u0000')
            
            _state.update {
                when (result) {
                    is RegisterResult.Success -> {
                        onSuccess(result.companyId)
                        it.copy(loading = false, showOtpDialog = false, complete = true, passwordString = "", confirmPasswordString = "")
                    }
                    is RegisterResult.Failure -> {
                        it.copy(loading = false, error = result.message)
                    }
                }
            }
        }
    }
}

@Composable
fun RegisterScreen(
    viewModel: RegisterViewModel,
    onNavigateBackToLogin: () -> Unit,
    onRegisterSuccess: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val scrollState = rememberScrollState()
    val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity

    LaunchedEffect(state.complete) {
        if (state.complete) {
            onRegisterSuccess()
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
                .widthIn(max = 440.dp)
                .padding(24.dp)
                .verticalScroll(scrollState)
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
                    .padding(28.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Electric Blue Badge
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            color = Color(0xFF1976D2),
                            shape = RoundedCornerShape(20.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = stringResource(com.kadaikutty.pos.R.string.register_title),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = stringResource(com.kadaikutty.pos.R.string.register_subtitle),
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
                    value = state.businessName,
                    onValueChange = { viewModel.updateBusinessName(it) },
                    label = { Text("Shop / Business Name", color = Color(0xFF94A3B8)) },
                    placeholder = { Text("e.g. Sasi Supermarket", color = Color(0xFF64748B)) },
                    leadingIcon = { Icon(Icons.Default.Home, contentDescription = null, tint = Color(0xFF94A3B8)) },
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
                    value = state.ownerName,
                    onValueChange = { viewModel.updateOwnerName(it) },
                    label = { Text("Owner / Admin Name", color = Color(0xFF94A3B8)) },
                    placeholder = { Text("e.g. Sasi Dharan", color = Color(0xFF64748B)) },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF94A3B8)) },
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
                    value = state.passwordString,
                    onValueChange = { viewModel.updatePassword(it) },
                    label = { Text("Password", color = Color(0xFF94A3B8)) },
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

                OutlinedTextField(
                    value = state.confirmPasswordString,
                    onValueChange = { viewModel.updateConfirmPassword(it) },
                    label = { Text("Confirm Password", color = Color(0xFF94A3B8)) },
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

                if (!state.error.isNullOrBlank()) {
                    Text(
                        text = state.error ?: "",
                        color = Color(0xFFFF6B6B),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.align(Alignment.Start)
                    )
                }

                Button(
                    onClick = { 
                        if (activity != null) {
                            viewModel.register(activity)
                        }
                    },
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
                    if (state.loading && !state.showOtpDialog) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White
                        )
                    } else {
                        Text("Send OTP & Register", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }

                Text(
                    text = "Already have an account? Sign In",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF38BDF8),
                    modifier = Modifier
                        .clickable { onNavigateBackToLogin() }
                        .padding(vertical = 4.dp)
                )
            }
        }
        
        if (state.showOtpDialog) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { viewModel.dismissOtpDialog() }
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF0B0F17),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    com.kadaikutty.pos.core.ui.otp.OrbitOtpVerificationView(
                        otpLength = 6,
                        otpValue = state.otp,
                        phoneNumber = state.mobileNumber,
                        onOtpChange = { viewModel.updateOtp(it) },
                        onVerifyTriggered = {
                            viewModel.verifyOtpAndCompleteRegistration { onRegisterSuccess() }
                        },
                        onResendClick = {
                            if (activity != null) {
                                viewModel.register(activity)
                            }
                        },
                        isLoading = state.loading,
                        errorMessage = state.error
                    )
                }
            }
        }
    }
}
